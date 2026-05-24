package com.phocassoftware.graphql.builder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

public class OneOfOutputTest {

	@Test
	public void testOneOfSealedInterfaceAsOutputType() {
		assertDoesNotThrow(
			() -> SchemaBuilder.build("com.phocassoftware.graphql.builder.oneofoutput"),
			"@OneOf sealed interface used as output type should build schema without error"
		);
	}
}
