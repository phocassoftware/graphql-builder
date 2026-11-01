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

package com.phocassoftware.graphql.database.manager.dynamo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.util.TableCoreUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tools.jackson.databind.ObjectMapper;

class FlattenerMultiTest {
	@ParameterizedTest
	@ValueSource(strings = {
		"seed,middle,overlay",
		"seed,overlay,middle",
		"middle,seed,overlay",
		"middle,overlay,seed",
		"overlay,seed,middle",
		"overlay,middle,seed"
	})
	void keepsLowestOriginAndHighestValuesRegardlessOfArrivalOrder(String order) {
		var tables = List.of("seed", "middle", "overlay");
		var flattener = Flattener.create(tables, false);
		for (var table : order.split(",")) {
			var item = Map
				.of(
					"organisationId",
					AttributeValue.builder().s("org").build(),
					"id",
					AttributeValue.builder().s(TableCoreUtil.table(Entry.class) + ":same").build(),
					"revision",
					AttributeValue.builder().n(Integer.toString(tables.indexOf(table) + 1)).build(),
					"item",
					AttributeValue
						.builder()
						.m(
							Map
								.of(
									"id",
									AttributeValue.builder().s("same").build(),
									"name",
									AttributeValue.builder().s(table).build()
								)
						)
						.build()
				);
			flattener.add(table, List.of(item));
		}
		var merged = flattener.get(Optional.empty(), Entry.class, "same");
		assertEquals("seed", merged.getSourceTable());
		assertEquals("overlay", merged.getTable());
		var result = merged.convertTo(new ObjectMapper(), Entry.class);
		assertEquals("overlay", result.getName());
		assertEquals(3, result.getRevision());
	}

	public static class Entry extends Table {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
