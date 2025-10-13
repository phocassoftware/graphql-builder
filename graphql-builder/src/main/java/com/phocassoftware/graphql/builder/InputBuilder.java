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
import com.phocassoftware.graphql.builder.annotations.InputIgnore;
import com.phocassoftware.graphql.builder.annotations.OneOf;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;
import com.phocassoftware.graphql.builder.mapper.ConstructorFieldBuilder;
import com.phocassoftware.graphql.builder.mapper.ConstructorFieldBuilder.RecordMapper;
import com.phocassoftware.graphql.builder.mapper.InputTypeBuilder;
import com.phocassoftware.graphql.builder.mapper.ObjectFieldBuilder;
import com.phocassoftware.graphql.builder.mapper.ObjectFieldBuilder.FieldMapper;
import com.phocassoftware.graphql.builder.mapper.OneOfBuilder;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputObjectType.Builder;
import graphql.schema.GraphQLNamedInputType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public abstract class InputBuilder {

	protected final EntityProcessor entityProcessor;
	protected final TypeMeta meta;

	public InputBuilder(EntityProcessor entityProcessor, TypeMeta meta) {
		this.entityProcessor = entityProcessor;
		this.meta = meta;
	}

	protected String buildName() {
		var schemaType = SchemaOption.BOTH;
		Entity graphTypeAnnotation = meta.getType().getAnnotation(Entity.class);
		if (graphTypeAnnotation != null) {
			schemaType = graphTypeAnnotation.value();
		}

		String typeName = EntityUtil.getName(meta);

		String inputName = typeName;
		if (schemaType != SchemaOption.INPUT) {
			inputName += "Input";
		}
		return inputName;
	}

	public GraphQLNamedInputType buildInput() {
		GraphQLInputObjectType.Builder graphInputType = GraphQLInputObjectType.newInputObject();
		var type = meta.getType();

		graphInputType.name(buildName());

		{
			var description = type.getAnnotation(GraphQLDescription.class);
			if (description != null) {
				graphInputType.description(description.value());
			}
		}

		processFields(graphInputType);

		entityProcessor.addSchemaDirective(type, type, graphInputType::withAppliedDirective);
		return graphInputType.build();
	}

	abstract void processFields(Builder graphInputType);

	protected abstract InputTypeBuilder resolve();

	public static class OneOfInputBuilder extends InputBuilder {

		public OneOfInputBuilder(EntityProcessor entityProcessor, TypeMeta meta) {
			super(entityProcessor, meta);
		}

		@Override
		void processFields(Builder graphInputType) {
			var oneOf = meta.getType().getAnnotation(OneOf.class);
			for (var oneOfType : oneOf.value()) {
				var name = oneOfType.name();
				GraphQLInputObjectField.Builder field = GraphQLInputObjectField.newInputObjectField();
				field.name(name);
				if (!oneOfType.description().isEmpty()) {
					field.description(oneOfType.description());
				}
				TypeMeta innerMeta = new TypeMeta(meta, oneOfType.type(), oneOfType.type());
				innerMeta.optional();
				var type = entityProcessor.getEntity(innerMeta).getInputType(innerMeta, new Annotation[0]);
				field.type(type);
				graphInputType.field(field);
			}
		}

		@Override
		protected InputTypeBuilder resolve() {
			var oneOf = meta.getType().getAnnotation(OneOf.class);
			return new OneOfBuilder(entityProcessor, meta.getType(), oneOf);
		}
	}

	public static class ObjectType extends InputBuilder {

		public ObjectType(EntityProcessor entityProcessor, TypeMeta meta) {
			super(entityProcessor, meta);
		}

		@Override
		void processFields(Builder graphInputType) {
			for (Method method : meta.getType().getMethods()) {
				try {
					var name = EntityUtil.setter(method);
					if (name.isPresent()) {
						GraphQLInputObjectField.Builder field = GraphQLInputObjectField.newInputObjectField();
						field.name(name.get());

						entityProcessor.addSchemaDirective(method, meta.getType(), field::withAppliedDirective);

						try {
							var classField = method.getDeclaringClass().getDeclaredField(name.get());
							entityProcessor.addSchemaDirective(classField, meta.getType(), field::withAppliedDirective);
						} catch (NoSuchFieldException ignored) {}

						TypeMeta innerMeta = new TypeMeta(meta, method.getParameterTypes()[0], method.getGenericParameterTypes()[0], method.getParameters()[0]);
						var entity = entityProcessor.getEntity(innerMeta);
						var inputType = entity.getInputType(innerMeta, method.getParameterAnnotations()[0]);
						field.type(inputType);

						var description = method.getAnnotation(GraphQLDescription.class);
						if (description != null) {
							field.description(description.value());
						}

						graphInputType.field(field);
					}
				} catch (RuntimeException e) {
					throw new RuntimeException("Failed to process method " + method, e);
				}
			}
		}

		@Override
		public InputTypeBuilder resolve() {
			var fieldMappers = new ArrayList<FieldMapper>();

			for (Method method : meta.getType().getMethods()) {
				try {
					var name = EntityUtil.setter(method);
					if (name.isPresent()) {

						TypeMeta innerMeta = new TypeMeta(meta, method.getParameterTypes()[0], method.getGenericParameterTypes()[0], method.getParameters()[0]);
						fieldMappers.add(FieldMapper.build(entityProcessor, innerMeta, name.get(), method));
					}
				} catch (RuntimeException e) {
					throw new RuntimeException("Failed to process method " + method, e);
				}
			}

			return new ObjectFieldBuilder(meta.getType(), fieldMappers);
		}
	}

	public static class ObjectConstructorType extends InputBuilder {

		private final Constructor<?> constructor;

		public ObjectConstructorType(EntityProcessor entityProcessor, TypeMeta meta, Constructor<?> constructor) {
			super(entityProcessor, meta);
			this.constructor = constructor;
		}

		@Override
		void processFields(Builder graphInputType) {
			for (var parameter : constructor.getParameters()) {
				try {
					GraphQLInputObjectField.Builder field = GraphQLInputObjectField.newInputObjectField();
					field.name(parameter.getName());

					var classProperties = meta.getType().getDeclaredFields();
					for (var classProperty : classProperties) {
						entityProcessor.addSchemaDirective(classProperty, meta.getType(), field::withAppliedDirective);
					}

					entityProcessor.addSchemaDirective(parameter, meta.getType(), field::withAppliedDirective);
					TypeMeta innerMeta = new TypeMeta(meta, parameter.getType(), parameter.getParameterizedType(), parameter);
					var entity = entityProcessor.getEntity(innerMeta);
					var inputType = entity.getInputType(innerMeta, parameter.getAnnotations());

					var description = parameter.getAnnotation(GraphQLDescription.class);
					if (description != null) {
						field.description(description.value());
					}
					field.type(inputType);
					graphInputType.field(field);
				} catch (RuntimeException e) {
					throw new RuntimeException("Failed to process method " + parameter, e);
				}
			}
		}

		@Override
		public InputTypeBuilder resolve() {
			var fieldMappers = new ArrayList<RecordMapper>();

			for (var parameter : constructor.getParameters()) {
				TypeMeta innerMeta = new TypeMeta(meta, parameter.getType(), parameter.getParameterizedType(), parameter);
				var resolver = entityProcessor.getResolver(innerMeta);
				fieldMappers.add(new RecordMapper(parameter.getName(), parameter.getType(), resolver));
			}

			return new ConstructorFieldBuilder(meta.getType(), fieldMappers);
		}
	}

	public static class Record extends InputBuilder {

		public Record(EntityProcessor entityProcessor, TypeMeta meta) {
			super(entityProcessor, meta);
		}

		@Override
		void processFields(Builder graphInputType) {
			for (var field : this.meta.getType().getDeclaredFields()) {
				try {
					if (field.isSynthetic()) {
						continue;
					}
					if (field.isAnnotationPresent(GraphQLIgnore.class)) {
						continue;
					}
					if (Modifier.isStatic(field.getModifiers())) {
						continue;
					} else {
						// getter type
						if (!field.isAnnotationPresent(InputIgnore.class)) {
							String name = EntityUtil.getName(field.getName(), field);
							GraphQLInputObjectField.Builder fieldBuilder = GraphQLInputObjectField.newInputObjectField();
							fieldBuilder.name(name);
							entityProcessor.addSchemaDirective(field, meta.getType(), fieldBuilder::withAppliedDirective);
							TypeMeta innerMeta = new TypeMeta(meta, field.getType(), field.getGenericType(), field);
							var entity = entityProcessor.getEntity(innerMeta);
							var inputType = entity.getInputType(innerMeta, field.getAnnotations());

							var description = field.getAnnotation(GraphQLDescription.class);
							if (description != null) {
								fieldBuilder.description(description.value());
							}

							fieldBuilder.type(inputType);
							graphInputType.field(fieldBuilder);
						}
					}
				} catch (RuntimeException e) {
					throw new RuntimeException("Failed to process method " + field, e);
				}
			}
		}

		@Override
		protected InputTypeBuilder resolve() {
			try {
				var fieldMappers = new ArrayList<RecordMapper>();

				for (var field : this.meta.getType().getDeclaredFields()) {
					if (field.isSynthetic()) {
						continue;
					}
					if (field.isAnnotationPresent(GraphQLIgnore.class)) {
						continue;
					}
					if (Modifier.isStatic(field.getModifiers())) {
						continue;
					} else {
						// getter type
						if (!field.isAnnotationPresent(InputIgnore.class)) {
							TypeMeta innerMeta = new TypeMeta(meta, field.getType(), field.getGenericType(), field);
							var resolver = entityProcessor.getResolver(innerMeta);
							var name = EntityUtil.getName(field.getName(), field);
							fieldMappers.add(new RecordMapper(name, field.getType(), resolver));
						}
					}
				}
				return new ConstructorFieldBuilder(meta.getType(), fieldMappers);
			} catch (RuntimeException e) {
				throw new RuntimeException("Failed to process " + this.meta.getType(), e);
			}
		}
	}
}
