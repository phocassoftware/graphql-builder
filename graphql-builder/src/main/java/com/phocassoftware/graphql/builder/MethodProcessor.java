/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.phocassoftware.graphql.builder;

import static com.phocassoftware.graphql.builder.EntityUtil.isContext;

import com.phocassoftware.graphql.builder.annotations.Directive;
import com.phocassoftware.graphql.builder.annotations.GraphQLDeprecated;
import com.phocassoftware.graphql.builder.annotations.GraphQLDescription;
import com.phocassoftware.graphql.builder.annotations.Mutation;
import com.phocassoftware.graphql.builder.annotations.Query;
import com.phocassoftware.graphql.builder.annotations.Subscription;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldDefinition.Builder;
import graphql.schema.GraphQLObjectType;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Function;

class MethodProcessor {

	private final DataFetcherRunner dataFetcherRunner;
	private final EntityProcessor entityProcessor;
	private final DirectivesSchema diretives;

	private final GraphQLCodeRegistry.Builder codeRegistry;

	private final GraphQLObjectType.Builder graphQuery;
	private final GraphQLObjectType.Builder graphMutations;
	private final GraphQLObjectType.Builder graphSubscriptions;

	public MethodProcessor(DataFetcherRunner dataFetcherRunner, EntityProcessor entityProcessor, DirectivesSchema diretives) {
		this.dataFetcherRunner = dataFetcherRunner;
		this.entityProcessor = entityProcessor;
		this.diretives = diretives;
		this.codeRegistry = GraphQLCodeRegistry.newCodeRegistry();

		this.graphQuery = GraphQLObjectType.newObject();
		graphQuery.name("Query");
		this.graphMutations = GraphQLObjectType.newObject();
		graphMutations.name("Mutations");
		this.graphSubscriptions = GraphQLObjectType.newObject();
		graphSubscriptions.name("Subscriptions");
	}

	void process(AuthorizerSchema authorizer, Method method) throws ReflectiveOperationException {
		if (!Modifier.isStatic(method.getModifiers())) {
			throw new RuntimeException("End point must be a static method");
		}
		FieldCoordinates coordinates;
		GraphQLObjectType.Builder object;
		if (method.isAnnotationPresent(Query.class)) {
			coordinates = FieldCoordinates.coordinates("Query", EntityUtil.getName(method.getName(), method));
			object = graphQuery;
		} else if (method.isAnnotationPresent(Mutation.class)) {
			coordinates = FieldCoordinates.coordinates("Mutations", EntityUtil.getName(method.getName(), method));
			object = graphMutations;
		} else if (method.isAnnotationPresent(Subscription.class)) {
			coordinates = FieldCoordinates.coordinates("Subscriptions", EntityUtil.getName(method.getName(), method));
			object = graphSubscriptions;
		} else {
			return;
		}

		object.field(process(authorizer, coordinates, null, method));
	}

	Builder process(AuthorizerSchema authorizer, FieldCoordinates coordinates, TypeMeta parentMeta, Method method)
		throws InvocationTargetException, IllegalAccessException {
		GraphQLFieldDefinition.Builder field = GraphQLFieldDefinition.newFieldDefinition();

		entityProcessor.addSchemaDirective(method, method.getDeclaringClass(), field::withAppliedDirective);

		var deprecated = method.getAnnotation(GraphQLDeprecated.class);
		if (deprecated != null) {
			field.deprecate(deprecated.value());
		}

		var description = method.getAnnotation(GraphQLDescription.class);
		if (description != null) {
			field.description(description.value());
		}

		field.name(coordinates.getFieldName());

		TypeMeta meta = new TypeMeta(parentMeta, method.getReturnType(), method.getGenericReturnType(), method);
		var type = entityProcessor.getType(meta, method.getAnnotations());
		field.type(type);
		for (int i = 0; i < method.getParameterCount(); i++) {
			var parameter = method.getParameters()[i];
			GraphQLArgument.Builder argument = GraphQLArgument.newArgument();
			if (isContext(parameter.getType(), parameter.getAnnotations())) {
				continue;
			}

			TypeMeta inputMeta = new TypeMeta(null, parameter.getType(), method.getGenericParameterTypes()[i], parameter);
			argument.type(entityProcessor.getInputType(inputMeta, method.getParameterAnnotations()[i])); // TODO:dirty cast

			description = parameter.getAnnotation(GraphQLDescription.class);
			if (description != null) {
				argument.description(description.value());
			}

			for (Annotation annotation : parameter.getAnnotations()) {
				// Check to see if the annotation is a directive
				if (!annotation.annotationType().isAnnotationPresent(Directive.class)) {
					continue;
				}
				var annotationType = annotation.annotationType();
				// Get the values out of the directive annotation
				var methods = annotationType.getDeclaredMethods();

				// Get the applied directive and add it to the argument
				var appliedDirective = getAppliedDirective(annotation, annotationType, methods);
				argument.withAppliedDirective(appliedDirective);
			}

			argument.name(EntityUtil.getName(parameter.getName(), parameter));
			// TODO: argument.defaultValue(defaultValue)
			field.argument(argument);
		}

		DataFetcher<?> fetcher = buildFetcher(diretives, authorizer, method, meta);
		codeRegistry.dataFetcher(coordinates, fetcher);
		return field;
	}

	private GraphQLAppliedDirective getAppliedDirective(Annotation annotation, Class<? extends Annotation> annotationType, Method[] methods)
		throws IllegalAccessException, InvocationTargetException {
		var appliedDirective = new GraphQLAppliedDirective.Builder().name(annotationType.getSimpleName());
		for (var definedMethod : methods) {
			var name = definedMethod.getName();
			var value = definedMethod.invoke(annotation);
			if (value == null) {
				continue;
			}

			TypeMeta innerMeta = new TypeMeta(null, definedMethod.getReturnType(), definedMethod.getGenericReturnType());
			var argumentType = entityProcessor.getEntity(innerMeta).getInputType(innerMeta, definedMethod.getAnnotations());
			appliedDirective.argument(GraphQLAppliedDirectiveArgument.newArgument().name(name).type(argumentType).valueProgrammatic(value).build());
		}
		return appliedDirective.build();
	}

	private <T extends Annotation> DataFetcher<?> buildFetcher(DirectivesSchema diretives, AuthorizerSchema authorizer, Method method, TypeMeta meta) {
		DataFetcher<?> fetcher = buildDataFetcher(meta, method);
		fetcher = diretives.wrap(method, meta, fetcher);

		if (authorizer != null) {
			fetcher = authorizer.wrap(fetcher, method);
		}
		return fetcher;
	}

	private DataFetcher<?> buildDataFetcher(TypeMeta meta, Method method) {
		Function<DataFetchingEnvironment, Object>[] resolvers = new Function[method.getParameterCount()];

		method.setAccessible(true);

		for (int i = 0; i < resolvers.length; i++) {
			var parameter = method.getParameters()[i];
			Class<?> type = parameter.getType();
			var name = EntityUtil.getName(parameter.getName(), parameter);
			var generic = method.getGenericParameterTypes()[i];
			var argMeta = new TypeMeta(meta, type, generic, parameter);
			resolvers[i] = buildResolver(name, argMeta, parameter.getAnnotations());
		}

		DataFetcher<?> fetcher = env -> {
			try {
				Object[] args = new Object[resolvers.length];
				for (int i = 0; i < resolvers.length; i++) {
					args[i] = resolvers[i].apply(env);
				}
				return method.invoke(env.getSource(), args);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof Exception) {
					throw (Exception) e.getCause();
				} else {
					throw e;
				}
			} catch (Exception e) {
				throw e;
			}
		};

		return dataFetcherRunner.manage(method, fetcher);
	}

	private Function<DataFetchingEnvironment, Object> buildResolver(String name, TypeMeta argMeta, Annotation[] annotations) {
		if (isContext(argMeta.getType(), annotations)) {
			var type = argMeta.getType();
			if (type.isAssignableFrom(DataFetchingEnvironment.class)) {
				return env -> env;
			}
			if (type.isAssignableFrom(GraphQLContext.class)) {
				return env -> env.getGraphQlContext();
			}
			return env -> {
				var localContext = env.getLocalContext();
				if (localContext != null && type.isAssignableFrom(localContext.getClass())) {
					return localContext;
				}

				var context = env.getContext();
				if (context != null && type.isAssignableFrom(context.getClass())) {
					return context;
				}

				context = env.getGraphQlContext().get(name);
				if (context != null && type.isAssignableFrom(context.getClass())) {
					return context;
				}
				throw new RuntimeException("Context object " + name + " not found");
			};
		}

		var resolver = entityProcessor.getResolver(argMeta);

		return env -> {
			var arg = env.getArgument(name);
			return resolver.convert(arg, env.getGraphQlContext(), env.getLocale());
		};
	}

	public GraphQLCodeRegistry.Builder getCodeRegistry() {
		return codeRegistry;
	}

	GraphQLObjectType.Builder getGraphQuery() {
		return graphQuery;
	}

	GraphQLObjectType.Builder getGraphMutations() {
		return graphMutations;
	}

	GraphQLObjectType.Builder getGraphSubscriptions() {
		return graphSubscriptions;
	}
}
