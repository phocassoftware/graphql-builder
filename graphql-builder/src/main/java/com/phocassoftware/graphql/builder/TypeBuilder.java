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

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.GraphQLDescription;
import com.phocassoftware.graphql.builder.annotations.GraphQLIgnore;
import com.phocassoftware.graphql.builder.exceptions.DuplicateMethodNameException;
import graphql.introspection.Introspection;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLTypeReference;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;

public abstract class TypeBuilder {

	protected final EntityProcessor entityProcessor;
	protected final TypeMeta meta;

	public TypeBuilder(EntityProcessor entityProcessor, TypeMeta meta) {
		this.entityProcessor = entityProcessor;
		this.meta = meta;
	}

	public GraphQLNamedOutputType buildType() {
		Builder graphType = GraphQLObjectType.newObject();
		String typeName = EntityUtil.getName(meta);
		graphType.name(typeName);

		GraphQLInterfaceType.Builder interfaceBuilder = GraphQLInterfaceType.newInterface();
		interfaceBuilder.name(typeName);
		var type = meta.getType();
		{
			var description = type.getAnnotation(GraphQLDescription.class);
			if (description != null) {
				graphType.description(description.value());
				interfaceBuilder.description(description.value());
			}
		}

		processFields(typeName, graphType, interfaceBuilder);

		boolean unmappedGenerics = meta.hasUnmappedGeneric();

		if (unmappedGenerics) {
			var name = EntityUtil.getName(meta.notDirect());

			graphType.withInterface(GraphQLTypeReference.typeRef(name));
			if (meta.isDirect()) {
				interfaceBuilder.withInterface(GraphQLTypeReference.typeRef(name));
			}
		}
		Class<?> parent = type.getSuperclass();
		while (parent != null) {
			if (parent.isAnnotationPresent(Entity.class)) {
				TypeMeta innerMeta = new TypeMeta(meta, parent, type.getGenericSuperclass());
				GraphQLInterfaceType interfaceName = (GraphQLInterfaceType) entityProcessor.getEntity(innerMeta).getInnerType(innerMeta);
				addInterface(graphType, interfaceBuilder, interfaceName);

				if (!parent.equals(type.getGenericSuperclass())) {
					innerMeta = new TypeMeta(meta, parent, parent);
					interfaceName = (GraphQLInterfaceType) entityProcessor.getEntity(innerMeta).getInnerType(innerMeta);
					addInterface(graphType, interfaceBuilder, interfaceName);
				}

				var genericMeta = new TypeMeta(null, parent, parent);
				if (!EntityUtil.getName(innerMeta).equals(EntityUtil.getName(genericMeta))) {
					interfaceName = (GraphQLInterfaceType) entityProcessor.getEntity(genericMeta).getInnerType(genericMeta);
					addInterface(graphType, interfaceBuilder, interfaceName);
				}
			}
			parent = parent.getSuperclass();
		}
		// generics
		TypeMeta innerMeta = new TypeMeta(meta, type, type);
		if (!EntityUtil.getName(innerMeta).equals(typeName)) {
			var interfaceName = entityProcessor.getEntity(innerMeta).getInnerType(innerMeta);
			graphType.withInterface(GraphQLTypeReference.typeRef(interfaceName.getName()));
			interfaceBuilder.withInterface(GraphQLTypeReference.typeRef(interfaceName.getName()));
		}
		innerMeta = new TypeMeta(null, type, type);
		if (!EntityUtil.getName(innerMeta).equals(typeName)) {
			var interfaceName = entityProcessor.getEntity(innerMeta).getInnerType(innerMeta);
			graphType.withInterface(GraphQLTypeReference.typeRef(interfaceName.getName()));
			interfaceBuilder.withInterface(GraphQLTypeReference.typeRef(interfaceName.getName()));
		}

		boolean interfaceable = type.isInterface() || Modifier.isAbstract(type.getModifiers());
		if (!meta.isDirect() && (interfaceable || unmappedGenerics)) {
			entityProcessor.addSchemaDirective(type, type, interfaceBuilder::withAppliedDirective, Introspection.DirectiveLocation.OBJECT);
			GraphQLInterfaceType built = interfaceBuilder.build();

			entityProcessor
				.getCodeRegistry()
				.typeResolver(
					built.getName(),
					env -> {
						if (type.isInstance(env.getObject())) {
							var meta = new TypeMeta(null, env.getObject().getClass(), env.getObject().getClass());
							var t = entityProcessor.getEntity(meta).getInnerType(null);
							if (!(t instanceof GraphQLObjectType)) {
								t = entityProcessor.getEntity(meta.direct()).getInnerType(null);
							}
							try {
								return (GraphQLObjectType) t;
							} catch (ClassCastException e) {
								throw e;
							}
						}
						return null;
					}
				);

			if (unmappedGenerics && !meta.isDirect()) {
				var directType = meta.direct();
				entityProcessor.getEntity(directType).getInnerType(directType);
			}
			return built;
		}

		entityProcessor.addSchemaDirective(type, type, graphType::withAppliedDirective, Introspection.DirectiveLocation.OBJECT);
		var built = graphType.build();
		entityProcessor
			.getCodeRegistry()
			.typeResolver(
				built.getName(),
				env -> {
					if (type.isInstance(env.getObject())) {
						return built;
					}
					return null;
				}
			);
		return built;
	}

	private void addInterface(Builder graphType, GraphQLInterfaceType.Builder interfaceBuilder, GraphQLInterfaceType interfaceName) {
		graphType.withInterface(interfaceName);
		for (var inner : interfaceName.getInterfaces()) {
			graphType.withInterface(GraphQLTypeReference.typeRef(inner.getName()));
			interfaceBuilder.withInterface(GraphQLTypeReference.typeRef(inner.getName()));
		}
		interfaceBuilder.withInterface(interfaceName);
	}

	protected abstract void processFields(String typeName, Builder graphType, graphql.schema.GraphQLInterfaceType.Builder interfaceBuilder);

	public static class ObjectType extends TypeBuilder {

		public ObjectType(EntityProcessor entityProcessor, TypeMeta meta) {
			super(entityProcessor, meta);
		}

		@Override
		protected void processFields(String typeName, Builder graphType, graphql.schema.GraphQLInterfaceType.Builder interfaceBuilder) {
			var type = meta.getType();
			var methods = type.getMethods();

			var duplicateMethodNames = new HashSet<String>();

			Arrays
				.stream(methods)
				.forEach(method -> {
					var name = EntityUtil.getter(method);
					if (name.isEmpty()) return;
					if (!duplicateMethodNames.add(name.get())) {
						throw new DuplicateMethodNameException(typeName, name.get());
					}
					var f = entityProcessor.getMethodProcessor().process(null, FieldCoordinates.coordinates(typeName, name.get()), meta, method);
					graphType.field(f);
					interfaceBuilder.field(f);
				});
		}
	}

	public static class Record extends TypeBuilder {

		public Record(EntityProcessor entityProcessor, TypeMeta meta) {
			super(entityProcessor, meta);
		}

		@Override
		protected void processFields(String typeName, Builder graphType, graphql.schema.GraphQLInterfaceType.Builder interfaceBuilder) {
			var type = meta.getType();

			var fieldsByName = Arrays
				.stream(type.getDeclaredFields())
				.filter(field -> !field.isSynthetic())
				.filter(field -> !Modifier.isStatic(field.getModifiers()))
				.collect(java.util.stream.Collectors.toMap(f -> f.getName(), f -> f, (a, b) -> a));

			var duplicateMethodNames = new HashSet<String>();

			Arrays
				.stream(type.getMethods())
				.filter(method -> !method.isSynthetic())
				.filter(method -> !method.getDeclaringClass().equals(Object.class))
				.filter(method -> !method.isAnnotationPresent(GraphQLIgnore.class))
				.filter(method -> !Modifier.isStatic(method.getModifiers()))
				.filter(method -> method.getParameterCount() == 0)
				.filter(method -> !method.getReturnType().equals(void.class))
				.filter(method -> !method.getName().startsWith("get") && !method.getName().startsWith("is"))
				.forEach(method -> {
					var field = fieldsByName.get(method.getName());
					if (field != null && field.isAnnotationPresent(GraphQLIgnore.class)) {
						return;
					}
					var name = field != null
						? EntityUtil.getName(field.getName(), field, method)
						: method.getName();
					if (!duplicateMethodNames.add(name)) {
						return;
					}
					var f = entityProcessor.getMethodProcessor().process(null, FieldCoordinates.coordinates(typeName, name), meta, method);
					graphType.field(f);
					interfaceBuilder.field(f);
				});
		}
	}
}
