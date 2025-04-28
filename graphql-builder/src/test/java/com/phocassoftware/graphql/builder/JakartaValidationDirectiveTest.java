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
	void testJakartaSizeDirectiveArgumentDefinition() {
		Map<String, Object> response = execute("query IntrospectionQuery { __schema { directives { name locations args { name } } } }", null).getData();
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
	void testJakartaValidationIsApplied() {
		var name = "Roger";
		Map<String, String> response = execute("mutation setName($name: String!){setName(name: $name)} ", Map.of("name", name)).getData();
		var result = response.get("setName");

		assertEquals(name, result);
	}

	private ExecutionResult execute(String query, Map<String, Object> variables) {
		GraphQLSchema preSchema = SchemaBuilder.builder().classpath("com.phocassoftware.graphql.builder.type.directive").build().build();
		GraphQL schema = GraphQL.newGraphQL(new IntrospectionWithDirectivesSupport().apply(preSchema)).build();

		var input = ExecutionInput.newExecutionInput();
		input.query(query);
		if (variables != null) {
			input.variables(variables);
		}
		return schema.execute(input);
	}
}
