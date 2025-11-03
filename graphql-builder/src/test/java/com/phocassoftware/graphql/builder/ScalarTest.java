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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.phocassoftware.graphql.builder.scalar.Fur;
import com.phocassoftware.graphql.builder.scalar.Shape;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
import graphql.scalars.ExtendedScalars;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScalarTest {

	@Test
	public void testCatFur() throws ReflectiveOperationException {
		var scalar = getField("Fur", "SCALAR");
		assertEquals("soft", scalar.get("description"));
	}

	@Test
	public void testCatShape() throws ReflectiveOperationException {
		var scalar = getField("Shape", "SCALAR");
		assertNull(scalar.get("description"));
		List<Map<String, String>> directive = (List<Map<String, String>>) scalar.get("appliedDirectives");
		assertEquals("Capture", directive.getFirst().get("name"));
	}

	public Map<String, Object> getField(String typeName, String kind) throws ReflectiveOperationException {
		Map<String, Map<String, Object>> response = execute(
			"{" +
				"  __type(name: \"" +
				typeName +
				"\") {" +
				"    name" +
				"    description" +
				"    kind" +
				"   appliedDirectives {\n" +
				"        name\n" +
				"        args {\n" +
				"          name\n" +
				"          value\n" +
				"        }\n" +
				"      }" +
				"  }" +
				"} "
		)
			.getData();
		var type = response.get("__type");
		Assertions.assertEquals(typeName, type.get("name"));
		Assertions.assertEquals(kind, type.get("kind"));
		return type;
	}

	@Test
	public void testQueryCatFur() throws ReflectiveOperationException {
		Map<String, Map<String, Fur>> response = execute(
			"query fur($fur: Fur!, $age: Long!){getCat(fur: $fur, age: $age){ fur age}} ",
			Map.of("fur", "long", "age", 2)
		)
			.getData();
		var cat = response.get("getCat");

		var fur = cat.get("fur");

		assertEquals("long", fur.getInput());
		assertEquals(2L, cat.get("age"));
	}

	@Test
	public void testQueryShape() throws ReflectiveOperationException {
		Map<String, Shape> response = execute("query shape($shape: Shape!){getShape(shape: $shape)} ", Map.of("shape", "round")).getData();
		var shape = response.get("getShape");

		assertEquals("round", shape.getInput());
	}

	private ExecutionResult execute(String query) {
		return execute(query, null);
	}

	private ExecutionResult execute(String query, Map<String, Object> variables) {
		GraphQL schema = GraphQL
			.newGraphQL(
				new IntrospectionWithDirectivesSupport()
					.apply(SchemaBuilder.builder().classpath("com.phocassoftware.graphql.builder.scalar").scalar(ExtendedScalars.GraphQLLong).build().build())
			)
			.build();
		var input = ExecutionInput.newExecutionInput();
		input.query(query);
		if (variables != null) {
			input.variables(variables);
		}
		ExecutionResult result = schema.execute(input);
		if (!result.getErrors().isEmpty()) {
			throw new RuntimeException(result.getErrors().toString()); // TODO:cleanup
		}
		return result;
	}
}
