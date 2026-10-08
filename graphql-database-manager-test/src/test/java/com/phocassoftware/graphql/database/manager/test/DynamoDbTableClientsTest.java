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

package com.phocassoftware.graphql.database.manager.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDb;
import java.util.List;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbTable;
import org.junit.jupiter.api.Test;

final class DynamoDbTableClientsTest {
	@Test
	void routesSeedReadsAndOverlayWritesToSeparateClients() throws Exception {
		try (var seedServer = LocalDynamoDbServer.start(); var overlayServer = LocalDynamoDbServer.start()) {
			seedServer.createEntityTable("seed");
			overlayServer.createEntityTable("overlay");

			var seedManager = manager(new DynamoDbTable("seed", seedServer.asyncClient()));
			var overlayManager = manager(new DynamoDbTable("seed", seedServer.asyncClient()), new DynamoDbTable("overlay", overlayServer.asyncClient()));
			var seed = seedManager.getVirtualDatabase("org");
			var overlay = overlayManager.getVirtualDatabase("org");
			assertSame(overlayServer.asyncClient(), overlayManager.getDynamoDbAsyncClient());
			var updated = seed.put(new Entry("original"));
			var deleted = seed.put(new Entry("delete me"));

			assertEquals("original", overlay.get(Entry.class, updated.getId()).getName());
			assertEquals(2, overlay.query(Entry.class).size());

			var changed = overlay.get(Entry.class, updated.getId());
			changed.setName("local");
			overlay.put(changed);
			overlay.delete(overlay.get(Entry.class, deleted.getId()), true);
			var created = overlay.put(new Entry("created"));

			var reloadedOverlay = overlayManager.getVirtualDatabase("org");
			assertEquals("local", reloadedOverlay.get(Entry.class, updated.getId()).getName());
			assertFalse(reloadedOverlay.getOptional(Entry.class, deleted.getId()).isPresent());
			assertTrue(reloadedOverlay.getOptional(Entry.class, created.getId()).isPresent());
			assertEquals(2, reloadedOverlay.query(Entry.class).size());

			var reloadedSeed = seedManager.getVirtualDatabase("org");
			assertEquals("original", reloadedSeed.get(Entry.class, updated.getId()).getName());
			assertTrue(reloadedSeed.getOptional(Entry.class, deleted.getId()).isPresent());
			assertFalse(reloadedSeed.getOptional(Entry.class, created.getId()).isPresent());
		}
	}

	@Test
	void legacyBuilderRoutesSeedAndWritableTablesWithEitherSetterOrder() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			server.createEntityTable("seed");
			server.createEntityTable("overlay");
			var seed = manager(new DynamoDbTable("seed", server.asyncClient())).getVirtualDatabase("org");
			var entry = seed.put(new Entry("original"));
			var clientFirst = DynamoDbManager
				.builder()
				.objectMapper(new ObjectMapper())
				.dynamoDbAsyncClient(server.asyncClient())
				.tables("seed", "overlay")
				.build();
			var tablesFirst = DynamoDbManager
				.builder()
				.objectMapper(new ObjectMapper())
				.tables(List.of("seed", "overlay"))
				.dynamoDbAsyncClient(server.asyncClient())
				.build();

			assertSame(server.asyncClient(), clientFirst.getDynamoDbAsyncClient());
			assertSame(server.asyncClient(), tablesFirst.getDynamoDbAsyncClient());
			var changed = clientFirst.getVirtualDatabase("org").get(Entry.class, entry.getId());
			changed.setName("local");
			clientFirst.getVirtualDatabase("org").put(changed);
			assertEquals("local", tablesFirst.getVirtualDatabase("org").get(Entry.class, entry.getId()).getName());
			assertEquals(
				"original",
				manager(new DynamoDbTable("seed", server.asyncClient()))
					.getVirtualDatabase("org")
					.get(Entry.class, entry.getId())
					.getName()
			);
		}
	}

	@Test
	void rejectsMixedRoutingConfigurationsInEitherSetterOrder() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var table = new DynamoDbTable("table", server.asyncClient());
			assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().tables("table").tableClients(table));
			assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().tableClients(table).tables("table"));
			assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().dynamoDbAsyncClient(server.asyncClient()).tableClients(table));
			assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().tableClients(table).dynamoDbAsyncClient(server.asyncClient()));
		}
	}

	@Test
	void validatesTableClientsForBothManagerAndDirectDriver() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var table = new DynamoDbTable("table", server.asyncClient());
			for (var tables : List.of(List.<DynamoDbTable>of(), List.of(table, table))) {
				assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().tableClients(tables));
				assertThrows(
					IllegalArgumentException.class,
					() -> new DynamoDb(
						new ObjectMapper(),
						tables,
						null,
						() -> "id",
						25,
						10,
						true,
						false,
						null,
						null
					)
				);
			}
		}
	}

	private static DynamoDbManager manager(DynamoDbTable... tables) {
		return DynamoDbManager.builder().objectMapper(new ObjectMapper()).tableClients(tables).build();
	}

	static class Entry extends Table {
		private String name;

		public Entry() {}

		Entry(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
