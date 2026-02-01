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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

//does not test all of records as needs newer version of java. But Classes that look like records
public class RecordTest {


	@Test
	public void testRecordInheritance() {
		var response = execute("query { recordInheritanceTest { ... on CatRecord { name, somethingToDoWithCats, type } ... on DogRecord { name, somethingToDoWithDogs, type } } }", null);
		assertTrue(response.getErrors().isEmpty(), () -> response.getErrors().toString());
		Map<String, List<Map<String, String>>> data = response.getData();
		var results = data.get("recordInheritanceTest");
		assertEquals(2, results.size());
		assertEquals(Map.of("name", "Lola", "somethingToDoWithDogs", "LikesBones", "type", "Dog"), results.get(0));
		assertEquals(Map.of("name", "Mavi", "somethingToDoWithCats", "LikesCheese", "type", "Cat"), results.get(1));
	}


	@Test
	public void testEntireContext() {
		var type = Map.of(
			"name", "foo",
			"age", 4
		);
		Map<String, Map<String, Object>> response = execute(
			"query passthrough($type: InputTypeInput!){passthrough(type: $type) {name age weight}} ",
			Map.of("type", type)
		)
			.getData();
		var passthrough = response.get("passthrough");
		var expected = new HashMap<>(type);
		expected.put("weight", null);
		assertEquals(expected, passthrough);
	}

	@Test
	public void testDescription() throws ReflectiveOperationException, JsonMappingException, JsonProcessingException {
		Map<String, Map<String, Object>> response = execute(
			"{" +
				"  __type(name: \"InputTypeInput\") {" +
				"    name" +
				"    kind" +
				"    description" +
				"    inputFields {" +
				"      name" +
				"      description" +
				"    }" +
				"  }" +
				"} ",
			null
		)
			.getData();

		var type = response.get("__type");
		assertEquals("record Type", type.get("description"));

		Map<String, String> age = new HashMap<>();
		age.put("name", "age");
		age.put("description", null);

		Map<String, String> weight = new HashMap<>();
		weight.put("name", "weight");
		weight.put("description", null);

		assertEquals(List.of(Map.of("name", "name", "description", "the name"), age, weight), type.get("inputFields"));
	}

	@Test
	public void testNullable() {
		var response = execute("query nullableTest($type: Boolean){nullableTest(type: $type)} ", null);
		var expected = new HashMap<String, String>();
		expected.put("nullableTest", null);
		assertEquals(expected, response.getData());
		assertTrue(response.getErrors().isEmpty());
	}

	@Test
	public void testSetNullable() {
		Map<String, Boolean> response = execute("query nullableTest($type: Boolean){nullableTest(type: $type)}", Map.of("type", true)).getData();
		var passthrough = response.get("nullableTest");
		assertEquals(true, passthrough);
	}

	@Test
	public void testNullableArray() {
		List<Boolean> array = new ArrayList<>();
		array.add(true);
		var response = execute("query nullableArrayTest($type: [Boolean!]){nullableArrayTest(type: $type)}", Map.of("type", array));
		var expected = new HashMap<String, List<Boolean>>();
		expected.put("nullableArrayTest", array);
		assertTrue(response.getErrors().isEmpty());
		assertEquals(expected, response.getData());
	}

	@Test
	public void testNullable2Array() {
		var response = execute("query nullableArrayTest($type: [Boolean!]){nullableArrayTest(type: $type)}", Map.of());
		var expected = new HashMap<String, List<Boolean>>();
		expected.put("nullableArrayTest", null);
		assertTrue(response.getErrors().isEmpty());
		assertEquals(expected, response.getData());
	}

	@Test
	public void testNullableArrayFails() {
		List<Boolean> array = new ArrayList<>();
		array.add(true);
		var response = execute("query nullableArrayTest($type: [Boolean]){nullableArrayTest(type: $type)}", Map.of("type", array));
		assertFalse(response.getErrors().isEmpty());
	}

	@Test
	public void testNullableInnerArray() {
		List<Boolean> array = new ArrayList<>();
		array.add(null);
		array.add(true);
		var response = execute("query nullableInnerArrayTest($type: [Boolean]!){nullableInnerArrayTest(type: $type)}", Map.of("type", array));
		var expected = new HashMap<String, List<Boolean>>();
		expected.put("nullableInnerArrayTest", array);
		assertTrue(response.getErrors().isEmpty());
		assertEquals(expected, response.getData());
	}

	@Test
	public void testNullableInnerArrayFails() {
		List<Boolean> array = new ArrayList<>();
		array.add(true);
		var response = execute("query nullableInnerArrayTest($type: [Boolean]){nullableInnerArrayTest(type: $type)}", Map.of("type", array));
		assertFalse(response.getErrors().isEmpty());
	}

	@Test
	public void testInnerNullableArray() {
		List<Boolean> array = new ArrayList<>();
		array.add(null);
		array.add(true);
		var response = execute("query innerNullableArrayTest($type: [Boolean]!){innerNullableArrayTest(type: $type)}", Map.of("type", array));
		var expected = new HashMap<String, List<Boolean>>();
		expected.put("innerNullableArrayTest", array);
		assertTrue(response.getErrors().isEmpty());
		assertEquals(expected, response.getData());
	}

	@Test
	public void testInnerNullableArrayFails() {
		List<Boolean> array = new ArrayList<>();
		array.add(true);
		var response = execute("query innerNullableArrayTest($type: [Boolean]){innerNullableArrayTest(type: $type)}", Map.of("type", array));
		assertFalse(response.getErrors().isEmpty());
	}

	private ExecutionResult execute(String query, Map<String, Object> variables) {
		GraphQL schema = GraphQL
			.newGraphQL(new IntrospectionWithDirectivesSupport().apply(SchemaBuilder.build("com.phocassoftware.graphql.builder.record")))
			.build();
		var input = ExecutionInput.newExecutionInput();
		input.query(query);
		if (variables != null) {
			input.variables(variables);
		}
		ExecutionResult result = schema.execute(input);
		return result;
	}
}
