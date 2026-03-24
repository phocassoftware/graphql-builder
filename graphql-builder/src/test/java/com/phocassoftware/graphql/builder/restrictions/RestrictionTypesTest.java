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
package com.phocassoftware.graphql.builder.restrictions;

import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonMappingException;
import com.phocassoftware.graphql.builder.SchemaBuilder;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RestrictionTypesTest {

	/*
	 * Really basic tests, just to ensure restrictions work on different types.
	 */

	private static GraphQL schema;

	@BeforeAll
	public static void init() throws ReflectiveOperationException {
		schema = GraphQL.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.restrictions.parameter")).build();
	}

	private static String singleQueryGql = "query entityQuery( $allowed: Boolean! ) { single(allowed: $allowed) { __typename } }";
	private static String singleOptionalQueryGql = "query entityQuery( $allowed: Boolean ) { singleOptional(allowed: $allowed) { __typename } }";
	private static String listQueryGql = "query entityQuery( $allowed: [Boolean!]! ) { list(allowed: $allowed) { __typename } }";
	private static String listOptionalQueryGql = "query entityQuery( $allowed: [Boolean!] ) { listOptional(allowed: $allowed) { __typename } }";
	private static final String LIST_QUERY_INHERITANCE_GPL = """
		query entityQuery( $allowed: [Boolean!]! ) {
			listInheritance(allowed: $allowed) {
				__typename
			}
		}
		""";

	@Test
	public void singleEntityQuery() throws ReflectiveOperationException, JsonMappingException, JsonProcessingException {
		Map<String, Object> variables = new HashMap<>();
		variables.put("allowed", true);
		Map<String, Map<String, Object>> response = execute(singleQueryGql, variables).getData();
		// No fancy checks. Just want to make ensure it executes without issue.
		Assertions.assertTrue(response.get("single").containsKey("__typename"));
	}

	@Test
	public void singleOptionalEntityQuery() throws ReflectiveOperationException, JsonMappingException, JsonProcessingException {
		Map<String, Object> variables = new HashMap<>();

		// Allowed
		variables.put("allowed", true);
		Map<String, Map<String, Object>> responseAllowed = execute(singleOptionalQueryGql, variables).getData();
		Assertions.assertTrue(responseAllowed.get("singleOptional").containsKey("__typename"));

		// Not allowed
		variables.put("allowed", false);
		Map<String, Object> responseDenied = execute(singleOptionalQueryGql, variables).getData();
		Assertions.assertNull(responseDenied.get("singleOptional"));
	}

	@Test
	public void listEntityQuery() throws ReflectiveOperationException, JsonMappingException, JsonProcessingException {
		Map<String, Object> variables = new HashMap<>();

		variables.put("allowed", Arrays.asList(true, true, true));
		Map<String, List<Object>> responseAllAllowed = execute(listQueryGql, variables).getData();
		Assertions.assertEquals(3, responseAllAllowed.get("list").size());

		variables.put("allowed", Arrays.asList(true, false, true));
		Map<String, List<Object>> responseSomeAllowed = execute(listQueryGql, variables).getData();
		Assertions.assertEquals(2, responseSomeAllowed.get("list").size());

		variables.put("allowed", Arrays.asList(false, false, false));
		Map<String, List<Object>> responseNoneAllowed = execute(listQueryGql, variables).getData();
		Assertions.assertEquals(0, responseNoneAllowed.get("list").size());
	}

	@Test
	public void listEntityInheritanceQuery() throws ReflectiveOperationException, JsonMappingException, JsonProcessingException {
		Map<String, Object> variables = new HashMap<>();

		variables.put("allowed", Arrays.asList(true, true, true));
		Map<String, List<Object>> responseAllAllowed = execute(LIST_QUERY_INHERITANCE_GPL, variables).getData();
		Assertions.assertEquals(3, responseAllAllowed.get("listInheritance").size());

		variables.put("allowed", Arrays.asList(true, false, true));
		Map<String, List<Object>> responseSomeAllowed = execute(LIST_QUERY_INHERITANCE_GPL, variables).getData();
		Assertions.assertEquals(2, responseSomeAllowed.get("listInheritance").size());

		variables.put("allowed", Arrays.asList(false, false, false));
		Map<String, List<Object>> responseNoneAllowed = execute(LIST_QUERY_INHERITANCE_GPL, variables).getData();
		Assertions.assertEquals(0, responseNoneAllowed.get("listInheritance").size());
	}

	@Test
	public void optionalListEntityQuery() throws ReflectiveOperationException, JsonMappingException, JsonProcessingException {
		Map<String, Object> variables = new HashMap<>();

		// No list passed through
		Map<String, List<Object>> responseNoVariables = execute(listOptionalQueryGql, variables).getData();
		Assertions.assertNull(responseNoVariables.get("listOptional"));

		variables.put("allowed", Arrays.asList(true, true, true));
		Map<String, List<Object>> responseAllAllowed = execute(listOptionalQueryGql, variables).getData();
		Assertions.assertEquals(3, responseAllAllowed.get("listOptional").size());

		variables.put("allowed", Arrays.asList(true, false, true));
		Map<String, List<Object>> responseSomeAllowed = execute(listOptionalQueryGql, variables).getData();
		Assertions.assertEquals(2, responseSomeAllowed.get("listOptional").size());

		variables.put("allowed", Arrays.asList(false, false, false));
		Map<String, List<Object>> responseNoneAllowed = execute(listOptionalQueryGql, variables).getData();
		Assertions.assertEquals(0, responseNoneAllowed.get("listOptional").size());
	}

	private static ExecutionResult execute(String query, Map<String, Object> variables) throws JsonMappingException, JsonProcessingException {
		var input = ExecutionInput.newExecutionInput().query(query).variables(variables).build();
		ExecutionResult result = schema.execute(input);
		if (!result.getErrors().isEmpty()) {
			throw new RuntimeException(result.getErrors().toString());
		}
		return result;
	}
}
