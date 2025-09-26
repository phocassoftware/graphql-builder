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

import com.phocassoftware.graphql.builder.annotations.Directive;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class DirectiveProcessor {

	private final GraphQLDirective directive;
	private final Map<String, Function<Object, GraphQLAppliedDirectiveArgument>> builders;

	public DirectiveProcessor(GraphQLDirective directive, Map<String, Function<Object, GraphQLAppliedDirectiveArgument>> builders) {
		this.directive = directive;
		this.builders = builders;
	}

	public static DirectiveProcessor build(EntityProcessor entityProcessor, Class<? extends Annotation> directive, boolean isJakarta) {
		var builder = GraphQLDirective.newDirective().name(directive.getSimpleName());

		Introspection.DirectiveLocation[] validLocations;
		if (isJakarta) {
			validLocations = new Introspection.DirectiveLocation[] {
				Introspection.DirectiveLocation.ARGUMENT_DEFINITION,
				Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION };
		} else {
			validLocations = directive.getAnnotation(Directive.class).value();

			// Check for repeatable tag in annotation and add it
			builder.repeatable(directive.getAnnotation(Directive.class).repeatable());
		}

		// loop through and add valid locations
		for (Introspection.DirectiveLocation location : validLocations) {
			builder.validLocation(location);
		}

		// Go through each argument and add name/type to directive
		var methods = directive.getDeclaredMethods();
		Map<String, Function<Object, GraphQLAppliedDirectiveArgument>> builders = new HashMap<>();
		for (Method method : methods) {
			if (method.getParameterCount() != 0) {
				continue;
			}
			if (!validResponseType(method.getReturnType())) {
				continue;
			}
			var name = method.getName();

			GraphQLArgument.Builder argument = GraphQLArgument.newArgument();
			argument.name(name);

			// Get the type of the argument from the return type of the method
			TypeMeta innerMeta = new TypeMeta(null, method.getReturnType(), method.getGenericReturnType());
			var argumentType = entityProcessor.getEntity(innerMeta).getInputType(innerMeta, method.getAnnotations());
			argument.type(argumentType);

			// Add the argument to the directive builder to be used for declaration
			builder.argument(argument);

			// Add a builder to the builders list (in order to populate applied directives)
			builders
				.put(
					name,
					object -> {
						try {
							return GraphQLAppliedDirectiveArgument.newArgument().name(name).type(argumentType).valueProgrammatic(method.invoke(object)).build();
						} catch (IllegalAccessException | InvocationTargetException e) {
							throw new RuntimeException(e);
						}
					}
				);
		}
		return new DirectiveProcessor(builder.build(), builders);
	}

	private static boolean validResponseType(Class<?> returnType) {
		if (returnType.isArray()) {
			return validResponseType(returnType.getComponentType());
		}
		if(returnType.equals(Class.class)) {
			return false;
		}
		return true;
	}

	public void apply(Annotation annotation, Consumer<GraphQLAppliedDirective> builder) throws InvocationTargetException, IllegalAccessException {
		var methods = annotation.annotationType().getDeclaredMethods();

		// Create a new AppliedDirective which we will populate with the set values
		var arguments = GraphQLAppliedDirective.newDirective();
		arguments.name(directive.getName());

		// To get the value we loop through each method and get the method name and value
		for (Method m : methods) {
			if (!validResponseType(m.getReturnType())) {
				continue;
			}
			// Using the builder created earlier populate the values of each method.
			arguments.argument(builders.get(m.getName()).apply(annotation));
		}

		// Add the argument to the Builder
		builder.accept(arguments.build());
	}

	public GraphQLDirective getDirective() {
		return this.directive;
	}
}
