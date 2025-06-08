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

import static org.junit.jupiter.api.Assertions.*;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class DirectiveTest {

	@Test
	public void testDirectiveAppliedToQuery() throws ReflectiveOperationException {
		GraphQL schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.type.directive")).build();
		var cat = schema.getGraphQLSchema().getFieldDefinition(FieldCoordinates.coordinates(schema.getGraphQLSchema().getQueryType(), "getCat"));
		var capture = cat.getAppliedDirective("Capture");
		var argument = capture.getArgument("color");
		var color = argument.getValue();
		assertEquals("meow", color);
	}

	@Test
	public void testNoArgumentDirective() throws ReflectiveOperationException {
		GraphQL schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.type.directive")).build();
		var cat = schema.getGraphQLSchema().getFieldDefinition(FieldCoordinates.coordinates(schema.getGraphQLSchema().getQueryType(), "getUpper"));
		var uppercase = cat.getAppliedDirective("Uppercase");
		assertNotNull(uppercase);
		assertTrue(uppercase.getArguments().isEmpty());
	}

	@Test
	public void testPresentOnSchema() throws ReflectiveOperationException {
		GraphQL schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.type.directive")).build();
		var capture = schema.getGraphQLSchema().getSchemaAppliedDirective("Capture");
		var argument = capture.getArgument("color");
		var color = argument.getValue();
		assertEquals("top", color);
	}

	@Test
	public void testDirectivePass() throws ReflectiveOperationException {
		Map<String, Object> response = execute("query allowed($name: String!){allowed(name: $name)} ", Map.of("name", "tabby")).getData();
		assertEquals("tabby", response.get("allowed"));
	}

	@Test
	public void testDirectiveFail() throws ReflectiveOperationException {
		var response = execute("query allowed($name: String!){allowed(name: $name)} ", Map.of("name", "calico"));

		assertNull(response.getData());

		assertTrue(response.getErrors().get(0).getMessage().contains("forbidden"));
	}

	@Test
	public void testDirectiveArgument() {
		GraphQL schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.type.directive")).build();
		var cat = schema.getGraphQLSchema().getFieldDefinition(FieldCoordinates.coordinates(schema.getGraphQLSchema().getQueryType(), "getNickname"));
		var argument = cat.getArgument("nickName");
		var directive = argument.getAppliedDirective("Input");
		assertNotNull(directive);
		var value = directive.getArgument("value").getValue();
		assertEquals("TT", value);
	}

	@Test
	public void testDirectiveArgumentDefinition() {
		Map<String, Object> response = execute("query IntrospectionQuery { __schema { directives { name locations args { name } } } }", null).getData();
		List<LinkedHashMap<String, Object>> dir = (List<LinkedHashMap<String, Object>>) ((Map<String, Object>) response.get("__schema")).get("directives");
		LinkedHashMap<String, Object> input = dir.stream().filter(map -> map.get("name").equals("Input")).collect(Collectors.toList()).get(0);

		assertEquals(30, dir.size());
		assertEquals("ARGUMENT_DEFINITION", ((List<String>) input.get("locations")).get(0));
		assertEquals(1, ((List<Object>) input.get("args")).size());
		// getNickname(nickName: String! @Input(value : "TT")): String!
		// directive @Input(value: String!) on ARGUMENT_DEFINITION
	}

	private ExecutionResult execute(String query, Map<String, Object> variables) {
		GraphQLSchema preSchema = SchemaBuilder.builder().classpath("com.phocassoftware.graphql.builder.type.directive").build().build();
		GraphQL schema = GraphQL.newGraphQL(new IntrospectionWithDirectivesSupport().apply(preSchema)).build();

		var input = ExecutionInput.newExecutionInput();
		input.query(query);
		if (variables != null) {
			input.variables(variables);
		}
		ExecutionResult result = schema.execute(input);
		return result;
	}
}
