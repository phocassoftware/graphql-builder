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

import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class RenameTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	public void testClassRename() throws JsonProcessingException, JSONException {
		var type = Map.of("nameSet", "foo");
		var response = execute("""
				query passthroughClass($type: ClassTypeInput!) {
					passthroughClass(type: $type) {
						nameGet
					}
				}
			""", Map.of("type", type))
			.toSpecification();
		JSONAssert.assertEquals("""
				{
					"data": {
						"passthroughClass": {
							"nameGet": "foo"
						}
					}
				}
			""", MAPPER.writeValueAsString(response), false);
	}

	@Test
	public void testRecordRename() throws JsonProcessingException, JSONException {
		var type = Map.of("name", "foo");
		var response = execute("""
				query passthroughRecord($type: RecordTypeInput!) {
					passthroughRecord(type: $type) {
						name
					}
				}
			""", Map.of("type", type))
			.toSpecification();
		JSONAssert.assertEquals("""
				{
					"data": {
						"passthroughRecord": {
							"name": "foo"
						}
					}
				}
			""", MAPPER.writeValueAsString(response), false);
	}

	private ExecutionResult execute(String query, Map<String, Object> variables) {
		GraphQL schema = GraphQL
			.newGraphQL(new IntrospectionWithDirectivesSupport().apply(SchemaBuilder.build("com.phocassoftware.graphql.builder.rename")))
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
