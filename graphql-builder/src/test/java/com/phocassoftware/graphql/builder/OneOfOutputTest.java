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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.GraphQL;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OneOfOutputTest {

	private static GraphQLSchema schema() {
		return SchemaBuilder.build("com.phocassoftware.graphql.builder.oneofoutput");
	}

	@Test
	public void testSealedInterfaceAsOutputType() {
		assertDoesNotThrow(OneOfOutputTest::schema, "Sealed interface used as output type should build schema without error");
	}

	@Test
	public void testSealedInterfaceWithoutOneOfBecomesUnion() {
		var type = schema().getType("Animal");
		var union = assertInstanceOf(GraphQLUnionType.class, type, "A sealed interface with no fields should produce a union");
		var members = union.getTypes().stream().map(t -> t.getName()).toList();
		assertEquals(2, members.size());
		assertTrue(members.contains("Cat"), () -> "expected Cat in " + members);
		assertTrue(members.contains("Dog"), () -> "expected Dog in " + members);
	}

	@Test
	public void testUnionResolvesConcreteTypeAtRuntime() {
		var result = GraphQL
			.newGraphQL(schema())
			.build()
			.execute("query { getAnimal { ... on Cat { name } ... on Dog { name } } }");

		assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
		Map<String, Map<String, Object>> data = result.getData();
		assertEquals(Map.of("name", "Mavi"), data.get("getAnimal"));
	}

	@Test
	public void testOneOfInterfaceIsBothUnionOutputAndOneOfInput() {
		var schema = schema();

		var union = assertInstanceOf(GraphQLUnionType.class, schema.getType("Shape"), "Shape should be a union output");
		var members = union.getTypes().stream().map(t -> t.getName()).toList();
		assertTrue(members.contains("Circle") && members.contains("Square"), () -> "expected Circle and Square in " + members);

		var input = assertInstanceOf(GraphQLInputObjectType.class, schema.getType("ShapeInput"), "Shape should also produce an input type");
		var fields = input.getFieldDefinitions().stream().map(f -> f.getName()).toList();
		assertTrue(fields.contains("circle") && fields.contains("square"), () -> "expected circle and square input fields in " + fields);
		// @OneOf inputs are modelled as an input object whose variant fields are all optional.
		input
			.getFieldDefinitions()
			.forEach(f -> assertInstanceOf(GraphQLInputObjectType.class, f.getType(), "variant fields should be nullable input objects"));
	}

	@Test
	public void testOneOfInterfaceRoundTripsThroughInputAndOutput() {
		var result = GraphQL
			.newGraphQL(schema())
			.build()
			.execute("mutation { putShape(shape: { circle: { radius: 2.0 } }) { ... on Circle { radius } ... on Square { side } } }");

		assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
		Map<String, Map<String, Object>> data = result.getData();
		assertEquals(Map.of("radius", 2.0), data.get("putShape"));
	}

	@Test
	public void testNonSealedInterfaceWithEntityImplementationsBecomesUnion() {
		var schema = SchemaBuilder.build("com.phocassoftware.graphql.builder.nonsealedoutput");

		var union = assertInstanceOf(
			GraphQLUnionType.class,
			schema.getType("Thing"),
			"A non-sealed interface should still produce a union via its @Entity implementations"
		);
		var members = union.getTypes().stream().map(t -> t.getName()).toList();
		assertEquals(2, members.size());
		assertTrue(members.contains("A") && members.contains("B"), () -> "expected A and B in " + members);
	}

	// --- comment 1: only field-less interfaces become unions ---

	interface Marker {}

	interface WithGetter {
		String getName();
	}

	interface WithStaticOnly {
		static int answer() {
			return 42;
		}
	}

	@Test
	public void testOnlyFieldLessInterfacesAreTreatedAsUnions() {
		// field-less interfaces (no getters) are represented as unions
		assertEquals(false, EntityUtil.hasFields(Marker.class));
		assertEquals(false, EntityUtil.hasFields(WithStaticOnly.class));
		assertEquals(false, EntityUtil.hasFields(com.phocassoftware.graphql.builder.oneofoutput.Animal.class));
		assertEquals(false, EntityUtil.hasFields(com.phocassoftware.graphql.builder.oneofoutput.Shape.class));
		// an interface that declares a getter has fields and must not be turned into a union
		assertEquals(true, EntityUtil.hasFields(WithGetter.class));
	}

	@Test
	public void testInterfaceWithNoDiscoverableImplementationsFailsWithClearMessage() {
		var error = assertThrows(
			RuntimeException.class,
			() -> SchemaBuilder.build("com.phocassoftware.graphql.builder.nomembersoutput")
		);

		var message = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause()) {
			message.append(t.getMessage()).append('\n');
		}
		assertTrue(
			message.toString().contains("@Entity"),
			() -> "expected a message explaining how to provide union members, got: " + message
		);
	}
}
