package com.phocassoftware.graphql.builder;

import com.phocassoftware.graphql.builder.exceptions.DuplicateMethodNameException;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DuplicateMethodNameTest {

	@Test
	void testFailsToBuildSchema() {
		assertThrows(
			DuplicateMethodNameException.class,
			() -> GraphQL
				.newGraphQL(
					new IntrospectionWithDirectivesSupport().apply(SchemaBuilder.build("com.phocassoftware.graphql.builder.duplicateMethodNames.invalid"))
				)
				.build()
		);
	}

	@Test
	void testSucceedsWithOverloadsAnnotatedWithIgnore() {
		var schema = GraphQL
			.newGraphQL(new IntrospectionWithDirectivesSupport().apply(SchemaBuilder.build("com.phocassoftware.graphql.builder.duplicateMethodNames.valid")))
			.build();
		assertNotNull(schema);
	}

}
