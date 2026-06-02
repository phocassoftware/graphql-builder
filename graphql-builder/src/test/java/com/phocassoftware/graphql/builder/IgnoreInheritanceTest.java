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
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.ExecutionResult;
import graphql.GraphQL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class IgnoreInheritanceTest {

	private static final GraphQL SCHEMA = GraphQL
		.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.ignoreinheritance"))
		.build();

	private static Set<String> fieldsOf(String typeName) {
		ExecutionResult result = SCHEMA.execute("{ __type(name: \"" + typeName + "\") { fields { name } } }");
		assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
		Map<String, Map<String, List<Map<String, String>>>> data = result.getData();
		return data.get("__type").get("fields").stream().map(f -> f.get("name")).collect(Collectors.toSet());
	}

	@Test
	public void testInterfaceIgnoreIsInheritedByImplementation() {
		assertEquals(Set.of("name"), fieldsOf("Dog"), "secret should be hidden because the interface method is @GraphQLIgnore");
	}

	@Test
	public void testGraphQLFieldOverrulesInheritedIgnoreOnRecord() {
		assertEquals(Set.of("name", "secret"), fieldsOf("Cat"), "@GraphQLField should re-expose the inherited-ignored secret");
	}

	@Test
	public void testSuperclassIgnoreIsInheritedBySubclass() {
		assertEquals(Set.of("shared", "model"), fieldsOf("Car"), "internal should be hidden because the superclass getter is @GraphQLIgnore");
	}

	@Test
	public void testGraphQLFieldOverrulesInheritedIgnoreOnClass() {
		assertEquals(Set.of("shared", "model", "internal"), fieldsOf("Bike"), "@GraphQLField should re-expose the inherited-ignored internal");
	}
}
