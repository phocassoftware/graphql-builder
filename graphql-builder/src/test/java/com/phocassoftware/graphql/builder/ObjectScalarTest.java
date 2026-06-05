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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.scalars.ExtendedScalars;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ObjectScalarTest {

	@Test
	public void objectFieldServedByRegisteredObjectScalar() {
		// The JSON scalar's coercing only ever yields Object. It must be bound to Object.class so that
		// an Object-typed field is serialised through it, rather than degrading to the String scalar's toString.
		Map<String, Object> data = execute("{ getPayload }").getData();
		assertEquals(Map.of("id", "some value"), data.get("getPayload"));
	}

	private ExecutionResult execute(String query) {
		GraphQL schema = GraphQL
			.newGraphQL(
				SchemaBuilder.builder().classpath("com.phocassoftware.graphql.builder.objectscalar").scalar(ExtendedScalars.Json).build().build()
			)
			.build();
		ExecutionResult result = schema.execute(ExecutionInput.newExecutionInput().query(query).build());
		if (!result.getErrors().isEmpty()) {
			throw new RuntimeException(result.getErrors().toString());
		}
		return result;
	}
}
