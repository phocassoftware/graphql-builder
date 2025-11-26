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

import com.phocassoftware.graphql.builder.context.GraphContext;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ContextTest {

	@Test
	public void testEntireContext() throws ReflectiveOperationException {
		Map<String, Boolean> response = execute("query {entireContext} ", __ -> {}).getData();
		assertTrue(response.get("entireContext"));
	}

	@Test
	public void testEnv() throws ReflectiveOperationException {
		Map<String, Boolean> response = execute("query {env} ", __ -> {}).getData();
		assertTrue(response.get("env"));
	}

	@Test
	public void testDeprecated() throws ReflectiveOperationException {
		@SuppressWarnings("deprecation")
		Map<String, Boolean> response = execute("query {deprecatedContext} ", b -> b.context(new GraphContext("context"))).getData();
		assertTrue(response.get("deprecatedContext"));
	}

	@Test
	public void testNamed() throws ReflectiveOperationException {
		Map<String, Boolean> response = execute("query {namedContext} ", b -> b.graphQLContext(c -> c.of("named", new GraphContext("context")))).getData();
		assertTrue(response.get("namedContext"));
	}

	@Test
	public void testNamedParameter() throws ReflectiveOperationException {
		Map<String, Boolean> response = execute("query {namedParemeterContext} ", b -> b.graphQLContext(c -> c.of("context", "test"))).getData();
		assertTrue(response.get("namedParemeterContext"));
	}

	@Test
	public void testMissing() throws ReflectiveOperationException {
		var response = execute("query {missingContext} ", b -> b.graphQLContext(c -> c.of("context", "test")));
		assertEquals(1, response.getErrors().size());
		var error = response.getErrors().get(0);
		assertTrue(error.getMessage().contains("Context object notPresent not found"));
	}

	@Test
	public void testLocalContext() throws ReflectiveOperationException {
		var response = execute("""
			query {
				localContext {
					name
				}
			}
			""", b -> b.graphQLContext(c -> c.of("context", new GraphContext("not john")))).getData();
		assertEquals(Map.of("localContext", Map.of("name", "John")), response);
	}

	private ExecutionResult execute(String query, Consumer<ExecutionInput.Builder> modify) {
		GraphQL schema = GraphQL
			.newGraphQL(new IntrospectionWithDirectivesSupport().apply(SchemaBuilder.build("com.phocassoftware.graphql.builder.context")))
			.build();
		var input = ExecutionInput.newExecutionInput();
		input.query(query);
		modify.accept(input);
		ExecutionResult result = schema.execute(input);
		return result;
	}
}
