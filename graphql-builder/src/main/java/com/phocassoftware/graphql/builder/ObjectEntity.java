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

import com.phocassoftware.graphql.builder.annotations.GraphQLCreator;
import com.phocassoftware.graphql.builder.annotations.OneOf;
import com.phocassoftware.graphql.builder.mapper.InputTypeBuilder;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNamedOutputType;

public class ObjectEntity extends EntityHolder {

	private InputBuilder inputBuilder;

	private TypeBuilder typeBuilder;

	public ObjectEntity(EntityProcessor entityProcessor, TypeMeta meta) {
		if (meta.getType().isRecord()) {
			typeBuilder = new TypeBuilder.Record(entityProcessor, meta);
		} else {
			typeBuilder = new TypeBuilder.ObjectType(entityProcessor, meta);
		}

		if (meta.getType().isAnnotationPresent(OneOf.class)) {
			inputBuilder = new InputBuilder.OneOfInputBuilder(entityProcessor, meta);
		} else if (meta.getType().isRecord()) {
			inputBuilder = new InputBuilder.Record(entityProcessor, meta);
		} else {
			var constructors = meta.getType().getDeclaredConstructors();
			if (constructors.length == 1) {
				var constructor = constructors[0];
				if (constructor.getParameterCount() > 0) {
					inputBuilder = new InputBuilder.ObjectConstructorType(entityProcessor, meta, constructor);
					return;
				}
			}
			for (var constructor : constructors) {
				if (constructor.isAnnotationPresent(GraphQLCreator.class)) {
					inputBuilder = new InputBuilder.ObjectConstructorType(entityProcessor, meta, constructor);
					return;
				}
			}
			inputBuilder = new InputBuilder.ObjectType(entityProcessor, meta);
		}
	}

	@Override
	protected GraphQLNamedInputType buildInput() {
		return inputBuilder.buildInput();
	}

	@Override
	public InputTypeBuilder buildResolver() {
		return inputBuilder.resolve();
	}

	@Override
	protected GraphQLNamedOutputType buildType() {
		return typeBuilder.buildType();
	}

	@Override
	protected String buildInputName() {
		return inputBuilder.buildName();
	}
}
