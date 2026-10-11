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
import com.phocassoftware.graphql.database.manager.dynamo.ClientRouting;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDb;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
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
			assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().tables("table").clientRouting(ClientRouting.perTable(List.of(table))));
			assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().clientRouting(ClientRouting.perTable(List.of(table))).tables("table"));
			assertThrows(
				IllegalArgumentException.class,
				() -> DynamoDbManager.builder().dynamoDbAsyncClient(server.asyncClient()).clientRouting(ClientRouting.perTable(List.of(table)))
			);
			assertThrows(
				IllegalArgumentException.class,
				() -> DynamoDbManager.builder().clientRouting(ClientRouting.perTable(List.of(table))).dynamoDbAsyncClient(server.asyncClient())
			);
		}
	}

	@Test
	void validatesTableClientsForBothManagerAndDirectDriver() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var table = new DynamoDbTable("table", server.asyncClient());
			for (var tables : List.of(List.<DynamoDbTable>of(), List.of(table, table))) {
				assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().clientRouting(ClientRouting.perTable(tables)));
				assertThrows(IllegalArgumentException.class, () -> ClientRouting.perTable(tables));
			}
		}
	}

	@Test
	void readsInTableOrderAndRetriesWithoutChangingOverlayPrecedence() throws Exception {
		try (var firstServer = LocalDynamoDbServer.start(); var secondServer = LocalDynamoDbServer.start()) {
			firstServer.createEntityTable("seed");
			secondServer.createEntityTable("middle");
			firstServer.createEntityTable("overlay");
			for (var table : List
				.of(
					new DynamoDbTable("seed", firstServer.asyncClient()),
					new DynamoDbTable("middle", secondServer.asyncClient()),
					new DynamoDbTable("overlay", firstServer.asyncClient())
				)) {
				var entry = new Entry(table.name());
				entry.setId("same-id");
				manager(table).getVirtualDatabase("org").put(entry);
			}

			var requestOrder = new ArrayList<String>();
			var first = new RecordingClient(firstServer.asyncClient(), true, requestOrder);
			var second = new RecordingClient(secondServer.asyncClient(), false, requestOrder);
			var routing = ClientRouting
				.perTable(
					List
						.of(
							new DynamoDbTable("seed", first),
							new DynamoDbTable("middle", second),
							new DynamoDbTable("overlay", first)
						)
				);
			var manager = DynamoDbManager.builder().objectMapper(new ObjectMapper()).clientRouting(routing).build();

			assertEquals("overlay", manager.getVirtualDatabase("org").get(Entry.class, "same-id").getName());
			assertSame(first, manager.getDynamoDbAsyncClient());
			assertEquals(List.of("seed", "seed", "middle", "overlay"), requestOrder);
			assertEquals(3, first.requests.size());
			assertEquals(Set.of("seed"), first.requests.getFirst().requestItems().keySet());
			assertEquals(first.requests.getFirst().requestItems(), first.requests.get(1).requestItems());
			assertEquals(Set.of("overlay"), first.requests.getLast().requestItems().keySet());
			assertEquals(1, second.requests.size());
			assertEquals(Set.of("middle"), second.requests.getFirst().requestItems().keySet());
		}
	}

	@Test
	void sharedRoutingBatchesTablesAndRetriesOnTheSameClient() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			for (var name : List.of("seed", "overlay")) {
				server.createEntityTable(name);
				var entry = new Entry(name);
				entry.setId("same-id");
				manager(new DynamoDbTable(name, server.asyncClient())).getVirtualDatabase("org").put(entry);
			}
			var client = new RecordingClient(server.asyncClient(), true, new ArrayList<>());
			var manager = DynamoDbManager
				.builder()
				.objectMapper(new ObjectMapper())
				.tables("seed", "overlay")
				.dynamoDbAsyncClient(client)
				.build();

			assertEquals("overlay", manager.getVirtualDatabase("org").get(Entry.class, "same-id").getName());
			assertEquals(2, client.requests.size());
			assertEquals(Set.of("seed", "overlay"), client.requests.getFirst().requestItems().keySet());
			assertEquals(client.requests.getFirst().requestItems(), client.requests.getLast().requestItems());
		}
	}

	@Test
	void routingBuilderKeepsBuiltConfigurationImmutable() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var names = new ArrayList<>(List.of("seed", "overlay"));
			var builder = ClientRouting.builder().tables(names).client(server.asyncClient());
			names.clear();
			var first = builder.build();
			var second = builder.tables(List.of("other")).build();
			assertEquals(List.of("seed", "overlay"), first.tableNames());
			assertEquals(List.of("other"), second.tableNames());
			assertSame(server.asyncClient(), first.writableTable().client());
			assertSame(first, ClientRouting.builder().routing(first).build());
			assertThrows(NullPointerException.class, () -> ClientRouting.builder().build());
			assertThrows(IllegalArgumentException.class, () -> ClientRouting.builder().tables(List.of()).build());
			assertThrows(UnsupportedOperationException.class, () -> first.readBatches().clear());
			assertThrows(UnsupportedOperationException.class, () -> first.readBatches().getFirst().tables().clear());
		}
	}

	@Test
	void sharedRoutingIsValidatedAndImmutable() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var names = new ArrayList<>(List.of("seed", "overlay"));
			var routing = ClientRouting.shared(names, server.asyncClient());
			names.clear();
			assertEquals(List.of("seed", "overlay"), routing.tableNames());
			assertEquals("overlay", routing.writableTable().name());
			assertThrows(UnsupportedOperationException.class, () -> routing.tables().clear());
			assertThrows(UnsupportedOperationException.class, () -> routing.tableNames().clear());
			assertThrows(IllegalArgumentException.class, () -> ClientRouting.shared(List.of(), server.asyncClient()));
			assertThrows(IllegalArgumentException.class, () -> ClientRouting.shared(List.of("same", "same"), server.asyncClient()));
			assertThrows(IllegalArgumentException.class, () -> ClientRouting.shared(List.of(" "), server.asyncClient()));
			assertThrows(NullPointerException.class, () -> ClientRouting.shared(List.of("table"), null));
		}
	}

	private static final class RecordingClient implements DynamoDbAsyncClient {
		private final DynamoDbAsyncClient delegate;
		private final boolean retryFirstRequest;
		private final List<String> requestOrder;
		private final List<BatchGetItemRequest> requests = new ArrayList<>();

		private RecordingClient(DynamoDbAsyncClient delegate, boolean retryFirstRequest, List<String> requestOrder) {
			this.delegate = delegate;
			this.retryFirstRequest = retryFirstRequest;
			this.requestOrder = requestOrder;
		}

		@Override
		public CompletableFuture<BatchGetItemResponse> batchGetItem(BatchGetItemRequest request) {
			requests.add(request);
			requestOrder.addAll(request.requestItems().keySet());
			if (retryFirstRequest && requests.size() == 1) {
				return CompletableFuture.completedFuture(BatchGetItemResponse.builder().unprocessedKeys(request.requestItems()).build());
			}
			return delegate.batchGetItem(request);
		}

		@Override
		public String serviceName() {
			return delegate.serviceName();
		}

		@Override
		public void close() {}
	}

	private static DynamoDbManager manager(DynamoDbTable... tables) {
		return DynamoDbManager.builder().objectMapper(new ObjectMapper()).clientRouting(ClientRouting.perTable(List.of(tables))).build();
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
