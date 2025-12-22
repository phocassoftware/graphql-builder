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

import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;

import com.phocassoftware.graphql.builder.annotations.GraphQLDescription;
import com.phocassoftware.graphql.builder.annotations.GraphQLIgnore;
import com.phocassoftware.graphql.builder.mapper.InputTypeBuilder;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNamedOutputType;

public class EnumEntity extends EntityHolder {

	private final GraphQLEnumType enumType;

	public EnumEntity(DirectivesSchema directives, TypeMeta meta) throws ReflectiveOperationException {
		graphql.schema.GraphQLEnumType.Builder enumType = GraphQLEnumType.newEnum();
		String typeName = EntityUtil.getName(meta);
		enumType.name(typeName);

		var type = meta.getType();

		var description = type.getAnnotation(GraphQLDescription.class);
		if (description != null) {
			enumType.description(description.value());
		}

		Object[] enums = type.getEnumConstants();
		for (Object e : enums) {
			Enum a = (Enum) e;
			var field = type.getDeclaredField(e.toString());
			if (field.isAnnotationPresent(GraphQLIgnore.class)) {
				continue;
			}
			var name = EntityUtil.getName(a.name(), field);
			var valueDef = newEnumValueDefinition().name(a.name()).value(a);
			var desc = field.getAnnotation(GraphQLDescription.class);
			if (desc != null) {
				valueDef.description(desc.value());
			}

			enumType.value(valueDef.build());
		}
		directives.addSchemaDirective(type, type, enumType::withAppliedDirective, Introspection.DirectiveLocation.ENUM);
		this.enumType = enumType.build();
	}

	@Override
	protected GraphQLNamedInputType buildInput() {
		return enumType;
	}

	@Override
	protected GraphQLNamedOutputType buildType() {
		return enumType;
	}

	@Override
	protected String buildInputName() {
		return enumType.getName();
	}

	@Override
	protected InputTypeBuilder buildResolver() {
		return null;
	}
}
