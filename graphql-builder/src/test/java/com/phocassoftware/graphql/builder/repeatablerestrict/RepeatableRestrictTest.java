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
package com.phocassoftware.graphql.builder.repeatablerestrict;

import com.phocassoftware.graphql.builder.SchemaBuilder;
import graphql.GraphQL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RepeatableRestrictTest {

	/*
	 * Regression: a class annotated with more than one @Restrict is stored in the @Restricts container.
	 * classgraph reports such a class under @Restrict too, but the direct annotation is absent, so the
	 * global-restrict scan must not assume getAnnotation(Restrict.class) is non-null.
	 */
	@Test
	public void buildsSchemaWithRepeatableRestrict() throws ReflectiveOperationException {
		var schema = GraphQL
			.newGraphQL(SchemaBuilder.build("com.phocassoftware.graphql.builder.repeatablerestrict"))
			.build();

		var result = schema.execute("query { entity { __typename } }");

		Assertions.assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
	}
}
