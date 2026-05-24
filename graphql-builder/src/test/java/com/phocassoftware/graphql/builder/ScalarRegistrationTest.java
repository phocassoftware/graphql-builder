package com.phocassoftware.graphql.builder;

import static org.junit.jupiter.api.Assertions.*;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ScalarRegistrationTest {

	public static class ZoneIdCoercing implements Coercing<ZoneId, ZoneId> {

		@Override
		public ZoneId serialize(Object dataFetcherResult) {
			if (dataFetcherResult instanceof ZoneId z) return z;
			return ZoneId.of(dataFetcherResult.toString());
		}

		@Override
		public ZoneId parseValue(Object input) {
			if (input instanceof ZoneId z) return z;
			return ZoneId.of(input.toString());
		}

		@Override
		public ZoneId parseLiteral(Object input) {
			return ZoneId.of(input.toString());
		}
	}

	private static final GraphQLScalarType ZONE_ID_SCALAR = new GraphQLScalarType.Builder()
		.name("ZoneId")
		.coercing(new ZoneIdCoercing())
		.build();

	private static final GraphQL graphQL = GraphQL
		.newGraphQL(
			SchemaBuilder
				.builder()
				.classpath("com.phocassoftware.graphql.builder.scalartest")
				.scalar(ZONE_ID_SCALAR)
				.build()
				.build()
		)
		.build();

	@Test
	public void schemaBuildsWithCustomScalarInOneOfUnion() {
		assertNotNull(graphQL);
	}

	@Test
	public void queryReturnsCustomScalarField() {
		var result = graphQL.execute(
			ExecutionInput.newExecutionInput()
				.query("query { widgets { ... on TimedWidget { name timeZone } ... on SimpleWidget { name } } }")
				.build()
		);

		assertTrue(result.getErrors().isEmpty(), "Expected no errors but got: " + result.getErrors());
		Map<String, List<Map<String, Object>>> data = result.getData();
		var widgets = data.get("widgets");
		assertEquals(2, widgets.size());

		assertEquals("hello", widgets.get(0).get("name"));
		assertEquals("world", widgets.get(1).get("name"));
		assertNotNull(widgets.get(1).get("timeZone"));
	}
}
