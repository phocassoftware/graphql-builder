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

import com.phocassoftware.graphql.builder.annotations.Scalar;
import com.phocassoftware.graphql.builder.annotations.Union;
import com.phocassoftware.graphql.builder.mapper.InputTypeBuilder;
import graphql.Scalars;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EntityProcessor {

	private final DirectivesSchema directives;

	private final Map<String, EntityHolder> entities;
	private final MethodProcessor methodProcessor;

	EntityProcessor(DataFetcherRunner dataFetcherRunner, List<GraphQLScalarType> scalars, DirectivesSchema directives) {
		this.methodProcessor = new MethodProcessor(dataFetcherRunner, this, directives);
		this.entities = new HashMap<>();
		addDefaults();
		addScalars(scalars);

		this.directives = directives;
	}

	private void addDefaults() {
		put(Boolean.class, new ScalarEntity(Scalars.GraphQLBoolean));
		put(Boolean.TYPE, new ScalarEntity(Scalars.GraphQLBoolean));

		put(Double.class, new ScalarEntity(Scalars.GraphQLFloat));
		put(Double.TYPE, new ScalarEntity(Scalars.GraphQLFloat));

		put(Integer.class, new ScalarEntity(Scalars.GraphQLInt));
		put(Integer.TYPE, new ScalarEntity(Scalars.GraphQLInt));

		put(String.class, new ScalarEntity(Scalars.GraphQLString));
	}

	private void addScalars(List<GraphQLScalarType> scalars) {
		for (var scalar : scalars) {
			var coercing = scalar.getCoercing();
			var type = coercing.getClass();
			for (var method : type.getMethods()) {
				if (method.isSynthetic()) {
					continue;
				}
				if ("parseValue".equals(method.getName())) {
					var returnType = method.getReturnType();
					if (returnType.equals(Long.class)) {
						put(Long.TYPE, new ScalarEntity(scalar));
					} else if (returnType.equals(Byte.class)) {
						put(Byte.TYPE, new ScalarEntity(scalar));
					} else if (returnType.equals(Character.class)) {
						put(Character.TYPE, new ScalarEntity(scalar));
					} else if (returnType.equals(Float.class)) {
						put(Float.TYPE, new ScalarEntity(scalar));
					} else if (returnType.equals(Short.class)) {
						put(Short.TYPE, new ScalarEntity(scalar));
					}
					put(returnType, new ScalarEntity(scalar));
					break;
				}
			}
		}
	}

	private void put(Class<?> type, ScalarEntity entity) {
		var name = EntityUtil.getName(new TypeMeta(null, type, type));
		entities.put(name, entity);
	}

	Set<GraphQLType> getAdditionalTypes() {
		return entities.values().stream().flatMap(s -> s.types()).collect(Collectors.toSet());
	}

	public EntityHolder getEntity(Class<?> type) {
		return getEntity(new TypeMeta(null, type, type));
	}

	EntityHolder getEntity(TypeMeta meta) {
		String name = EntityUtil.getName(meta);
		return entities
			.computeIfAbsent(
				name,
				__ -> {
					Class<?> type = meta.getType();
					Type genericType = meta.getGenericType();
					if (genericType == null) {
						genericType = type;
					}
					try {
						if (type.isAnnotationPresent(Scalar.class)) {
							return new ScalarEntity(directives, meta);
						}
						if (type.isEnum()) {
							return new EnumEntity(directives, meta);
						} else {
							return new ObjectEntity(this, meta);
						}
					} catch (ReflectiveOperationException | RuntimeException e) {
						throw new RuntimeException("Failed to build schema for class " + type, e);
					}
				}
			);
	}

	public GraphQLOutputType getType(TypeMeta meta, Annotation[] annotations) {
		for (var annotation : annotations) {
			if (annotation instanceof Union) {
				var union = (Union) annotation;
				return getUnionType(meta, union);
			}
		}

		return getEntity(meta).getType(meta, annotations);
	}

	private GraphQLOutputType getUnionType(TypeMeta meta, Union union) {
		var name = UnionType.name(union);

		return entities
			.computeIfAbsent(
				name,
				__ -> {
					try {
						return new UnionType(this, union);
					} catch (RuntimeException e) {
						throw new RuntimeException("Failed to build schema for union " + union, e);
					}
				}
			)
			.getType(meta, null);
	}

	public GraphQLInputType getInputType(TypeMeta meta, Annotation[] annotations) {
		return getEntity(meta).getInputType(meta, annotations);
	}

	void addSchemaDirective(
		AnnotatedElement element,
		Class<?> location,
		Consumer<GraphQLAppliedDirective> builder,
		Introspection.DirectiveLocation directiveLocation
	) {
		this.directives.addSchemaDirective(element, location, builder, directiveLocation);
	}

	public InputTypeBuilder getResolver(TypeMeta meta) {
		return getEntity(meta).getResolver(meta);
	}

	public InputTypeBuilder getResolver(Class<?> type) {
		var meta = new TypeMeta(null, type, type);
		return getEntity(meta).getResolver(meta);
	}

	GraphQLCodeRegistry.Builder getCodeRegistry() {
		return this.methodProcessor.getCodeRegistry();
	}

	MethodProcessor getMethodProcessor() {
		return methodProcessor;
	}
}
