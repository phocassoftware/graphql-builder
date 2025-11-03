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

import com.phocassoftware.graphql.builder.annotations.GraphQLDescription;
import com.phocassoftware.graphql.builder.annotations.Scalar;
import com.phocassoftware.graphql.builder.mapper.InputTypeBuilder;
import graphql.introspection.Introspection;
import graphql.schema.Coercing;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLScalarType;

public class ScalarEntity extends EntityHolder {

	private final GraphQLScalarType scalar;

	public ScalarEntity(GraphQLScalarType scalar) {
		this.scalar = scalar;
	}

	public ScalarEntity(DirectivesSchema directives, TypeMeta meta) throws ReflectiveOperationException {
		GraphQLScalarType.Builder scalarType = GraphQLScalarType.newScalar();
		String typeName = EntityUtil.getName(meta);
		scalarType.name(typeName);

		var type = meta.getType();

		var description = type.getAnnotation(GraphQLDescription.class);
		if (description != null) {
			scalarType.description(description.value());
		}

		Class<? extends Coercing> coerecing = type.getAnnotation(Scalar.class).value();
		scalarType.coercing(coerecing.getDeclaredConstructor().newInstance());

		directives.addSchemaDirective(type, type, scalarType::withAppliedDirective, Introspection.DirectiveLocation.SCALAR);
		this.scalar = scalarType.build();
	}

	@Override
	protected GraphQLNamedInputType buildInput() {
		return scalar;
	}

	@Override
	protected GraphQLNamedOutputType buildType() {
		return scalar;
	}

	@Override
	protected String buildInputName() {
		return scalar.getName();
	}

	@Override
	public InputTypeBuilder buildResolver() {
		return scalar.getCoercing()::parseValue;
	}
}
