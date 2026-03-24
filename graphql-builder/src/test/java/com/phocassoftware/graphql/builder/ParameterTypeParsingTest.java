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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ParameterTypeParsingTest {

	public static final ObjectMapper MAPPER = JsonMapper
		.builder()
		.changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.FIELD, Visibility.ANY))
		.build();

	// TODO:add failure cases
	@Test
	public void testRequiredType() {
		Map<String, Map<String, String>> response = execute("query test($type: InputTestInput!){requiredType(type: $type){value}} ", "{\"value\": \"There\"}")
			.getData();
		assertEquals("There", response.get("requiredType").get("value"));
	}

	@Test
	public void testEnum() {
		Map<String, Map<String, String>> response = execute("query enumTest($type: AnimalType!){enumTest(type: $type)} ", "\"CAT\"").getData();
		assertEquals("CAT", response.get("enumTest"));
	}

	@Test
	public void testDescription() {
		Map<String, Map<String, Object>> response = execute(
			"{" +
				"  __type(name: \"AnimalType\") {" +
				"    name" +
				"    kind" +
				"    description" +
				"    enumValues {" +
				"      name" +
				"      description" +
				"    }" +
				"  }" +
				"} ",
			null
		)
			.getData();

		var type = response.get("__type");

		assertEquals("enum desc", type.get("description"));

		Map<String, String> dog = new HashMap<>();
		dog.put("name", "DOG");
		dog.put("description", null);

		assertEquals(List.of(Map.of("name", "CAT", "description", "A cat"), dog), type.get("enumValues"));
	}

	@Test
	public void testOptionalTypePresent() {
		Map<String, Map<String, String>> response = execute("query test($type: InputTestInput){optionalType(type: $type){value}} ", "{\"value\": \"There\"}")
			.getData();
		assertEquals("There", response.get("optionalType").get("value"));
	}

	@Test
	public void testOptionalTypeNull() {
		Map<String, Map<String, String>> response = execute("query test($type: InputTestInput){optionalType(type: $type){value}} ", null).getData();
		assertEquals(null, response.get("optionalType"));
	}

	@Test
	public void testOptionalTypeMissing() {
		Map<String, Map<String, String>> response = execute("query test{optionalType{value}} ", null).getData();
		assertEquals(null, response.get("optionalType"));
	}

	@Test
	public void testRequiredListTypeEmpty() {
		Map<String, List<Map<String, String>>> response = execute("query {requiredListType(type: []){value}} ", null).getData();
		assertEquals(Collections.emptyList(), response.get("requiredListType"));
	}

	@Test
	public void testRequiredListType() {
		Map<String, List<Map<String, String>>> response = execute(
			"query test($type: [InputTestInput!]!){requiredListType(type: $type){value}} ",
			"[{\"value\": \"There\"}]"
		)
			.getData();
		assertEquals("There", response.get("requiredListType").get(0).get("value"));
	}

	@Test
	public void testOptionalListTypeEmpty() {
		Map<String, List<Map<String, String>>> response = execute("query {optionalListType(type: []){value}} ", null).getData();
		assertEquals(Collections.emptyList(), response.get("optionalListType"));
	}

	@Test
	public void testOptionalListType() {
		Map<String, List<Map<String, String>>> response = execute(
			"query test($type: [InputTestInput!]){optionalListType(type: $type){value}} ",
			"[{\"value\": \"There\"}]"
		)
			.getData();
		assertEquals("There", response.get("optionalListType").get(0).get("value"));
	}

	@Test
	public void testOptionalListTypeNull() {
		Map<String, List<Map<String, String>>> response = execute("query {optionalListType{value}} ", null).getData();
		assertEquals(null, response.get("optionalListType"));
	}

	@Test
	public void testRequiredListOptionalType() {
		Map<String, List<Map<String, String>>> response = execute(
			"query test($type: [InputTestInput]!){requiredListOptionalType(type: $type){value}} ",
			"[null, {\"value\": \"There\"}]"
		)
			.getData();
		assertEquals("There", response.get("requiredListOptionalType").get(1).get("value"));
		assertEquals(null, response.get("requiredListOptionalType").get(0));
	}

	@Test
	public void testOptionalListOptionalType() {
		Map<String, List<Map<String, String>>> response = execute(
			"query test($type: [InputTestInput]){optionalListOptionalType(type: $type){value}} ",
			"[null, {\"value\": \"There\"}]"
		)
			.getData();
		assertEquals("There", response.get("optionalListOptionalType").get(1).get("value"));
		assertEquals(null, response.get("optionalListOptionalType").get(0));
	}

	@Test
	public void testOptionalListOptionalTypeNull() {
		Map<String, List<Map<String, String>>> response = execute("query {optionalListOptionalType{value}} ", null).getData();
		assertEquals(null, response.get("optionalListOptionalType"));
	}

	private ExecutionResult execute(String query, String type) {
		Object obj = null;
		if (type != null) {
			obj = MAPPER.readValue(type, Object.class);
		}
		Map<String, Object> variables = new HashMap<>();
		variables.put("type", obj);

		var schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.parameter")).build();
		var input = ExecutionInput.newExecutionInput().query(query).variables(variables).build();
		ExecutionResult result = schema.execute(input);
		if (!result.getErrors().isEmpty()) {
			throw new RuntimeException(result.getErrors().toString()); // TODO:cleanup
		}
		return result;
	}
}
