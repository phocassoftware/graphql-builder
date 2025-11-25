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

import java.util.Map;

import org.junit.jupiter.api.Test;

import graphql.*;
import graphql.introspection.IntrospectionWithDirectivesSupport;

public class IgnoreTest {

	@Test
	public void testValid() {
		var response = execute(
			"""
				query person {
					person {
						age
						occupation
					}
				}
				""",
			Map.of()
		);
		assertEquals(Map.of("person", Map.of("age", 30, "occupation", true)), response.getData());
	}

	@Test
	public void testErrors() {
		var response = execute(
			"""
				query person {
					person {
						age
						name
					}
				}
				""",
			Map.of()
		);
		assertTrue(response.getErrors().size() > 0);
		assertTrue(response.getErrors().getFirst().getMessage().contains("Field 'name' in type 'Person' is undefined"));
	}

	private ExecutionResult execute(String query, Map<String, Object> variables) {
		GraphQL schema = GraphQL
			.newGraphQL(new IntrospectionWithDirectivesSupport().apply(SchemaBuilder.build("com.phocassoftware.graphql.builder.ignore")))
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
