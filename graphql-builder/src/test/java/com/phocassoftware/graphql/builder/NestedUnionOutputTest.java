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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class NestedUnionOutputTest {

	private static GraphQLSchema schema() {
		return SchemaBuilder.build("com.phocassoftware.graphql.builder.nestedunionoutput");
	}

	@Test
	public void nestedSealedInterfaceFlattensToConcreteLeaves() {
		var union = assertInstanceOf(GraphQLUnionType.class, schema().getType("Vehicle"), "field-less sealed interface should be a union");
		var members = union.getTypes().stream().map(t -> t.getName()).toList();

		// The nested LandVehicle interface must be flattened to its concrete leaves, not included as a member.
		assertFalse(members.contains("LandVehicle"), () -> "union must not contain the nested interface, got " + members);
		assertEquals(3, members.size(), () -> "expected three concrete members, got " + members);
		assertTrue(members.containsAll(java.util.List.of("Car", "Truck", "Boat")), () -> "expected Car, Truck, Boat in " + members);
	}

	@Test
	public void resolvesNestedLeafAtRuntime() {
		var result = GraphQL
			.newGraphQL(schema())
			.build()
			.execute("query { getVehicle { ... on Car { name } ... on Truck { name } ... on Boat { name } } }");

		assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
		Map<String, Map<String, Object>> data = result.getData();
		assertEquals(Map.of("name", "Beetle"), data.get("getVehicle"));
	}
}
