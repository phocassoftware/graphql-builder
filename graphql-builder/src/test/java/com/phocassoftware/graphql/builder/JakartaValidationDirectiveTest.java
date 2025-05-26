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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JakartaValidationDirectiveTest {
	@Test
	void testJakartaSizeAnnotationAddedAsDirective() {
		GraphQL schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.type.directive")).build();
		var name = schema.getGraphQLSchema().getFieldDefinition(FieldCoordinates.coordinates(schema.getGraphQLSchema().getMutationType(), "setName"));
		var directive = name.getArgument("name").getAppliedDirective("Size");
		var argument = directive.getArgument("min");
		var min = argument.getValue();
		assertEquals(3, min);
	}

	@Test
	void testJakartaSizeAnnotationAddedAsDirectiveOnARecord() {
		GraphQL schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.type.directive.record")).build();
		var name = schema.getGraphQLSchema().getFieldDefinition(FieldCoordinates.coordinates(schema.getGraphQLSchema().getMutationType(), "setName"));
		var directive = name.getArgument("name").getAppliedDirective("Size");
		var argument = directive.getArgument("min");
		var min = argument.getValue();
		assertEquals(3, min);
	}

	@Test
	void testJakartaSizeDirectiveArgumentDefinition() {
		Map<String, Object> response = execute("query IntrospectionQuery { __schema { directives { name locations args { name } } } }", null, true).getData();
		List<LinkedHashMap<String, Object>> dir = (List<LinkedHashMap<String, Object>>) ((Map<String, Object>) response.get("__schema")).get("directives");
		LinkedHashMap<String, Object> constraint = dir.stream().filter(map -> map.get("name").equals("Size")).collect(Collectors.toList()).get(0);

		assertEquals(30, dir.size());
		assertEquals("ARGUMENT_DEFINITION", ((List<String>) constraint.get("locations")).get(0));
		assertEquals("INPUT_FIELD_DEFINITION", ((List<String>) constraint.get("locations")).get(1));
		assertEquals(5, ((List<Object>) constraint.get("args")).size());
		assertEquals("{name=payload}", ((List<Object>) constraint.get("args")).getFirst().toString());
		assertEquals("{name=min}", ((List<Object>) constraint.get("args")).get(1).toString());
		assertEquals("{name=max}", ((List<Object>) constraint.get("args")).get(2).toString());
		assertEquals("{name=message}", ((List<Object>) constraint.get("args")).get(3).toString());
		assertEquals("{name=groups}", ((List<Object>) constraint.get("args")).get(4).toString());
	}

	@Test
	void testJakartaSizeValidationIsApplied() {
		var name = "Roger";
		Map<String, String> response = execute("mutation setName($name: String!){setName(name: $name)} ", Map.of("name", name), true).getData();
		var result = response.get("setName");

		assertEquals(name, result);

		name = "Po";
		var error = execute("mutation setName($name: String!){setName(name: $name)} ", Map.of("name", name), true).getErrors().getFirst();

		assertEquals("size must be between 3 and 2147483647", error.getMessage());
	}

	@Test
	void testJakartaSizeValidationIsNotAppliedWhenFlagIsFalse() {
		var name = "Po";
		Map<String, String> response = execute("mutation setName($name: String!){setName(name: $name)} ", Map.of("name", name), false).getData();
		var result = response.get("setName");

		assertEquals(name, result);
	}

	@Test
	void testJakartaMinAndMaxValidationIsApplied() {
		var age = 4;
		Map<String, Integer> response = execute("mutation setAge($age: Int!){setAge(age: $age)} ", Map.of("age", age), true).getData();
		var result = response.get("setAge");

		assertEquals(age, result);

		age = 2;
		var error = execute("mutation setAge($age: Int!){setAge(age: $age)} ", Map.of("age", age), true).getErrors().getFirst();

		assertEquals("must be greater than or equal to 3", error.getMessage());

		age = 100;
		error = execute("mutation setAge($age: Int!){setAge(age: $age)} ", Map.of("age", age), true).getErrors().getFirst();

		assertEquals("must be less than or equal to 99", error.getMessage());
	}

	private ExecutionResult execute(String query, Map<String, Object> variables, boolean validate) {
		var builder = SchemaBuilder.builder().classpath("com.phocassoftware.graphql.builder.type.directive");
		if (validate) builder = builder.validate();
		GraphQLSchema preSchema = builder.build().build();
		GraphQL schema = GraphQL.newGraphQL(new IntrospectionWithDirectivesSupport().apply(preSchema)).build();

		var input = ExecutionInput.newExecutionInput();
		input.query(query);
		if (variables != null) {
			input.variables(variables);
		}
		return schema.execute(input);
	}
}
