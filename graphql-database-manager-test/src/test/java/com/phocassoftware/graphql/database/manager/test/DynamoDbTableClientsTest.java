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
import com.phocassoftware.graphql.database.manager.RevisionMismatchException;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbClientManager;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDb;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import com.phocassoftware.graphql.database.manager.dynamo.ClientRouting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class DynamoDbTableClientsTest {
	@Test
	void routesSeedReadsAndOverlayWritesToSeparateClients() throws Exception {
		try (var seedServer = LocalDynamoDbServer.start(); var overlayServer = LocalDynamoDbServer.start()) {
			seedServer.createEntityTable("seed");
			overlayServer.createEntityTable("overlay");

			var seedManager = manager(new DynamoDbClientManager(List.of("seed"), seedServer.asyncClient()));
			var overlayManager = manager(
				new DynamoDbClientManager(List.of("seed"), seedServer.asyncClient()),
				new DynamoDbClientManager(List.of("overlay"), overlayServer.asyncClient())
			);
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

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void deletingUpdatedSeedKeepsItHidden(boolean separateClients) throws Exception {
		try (var seedServer = LocalDynamoDbServer.start(); var localServer = LocalDynamoDbServer.start()) {
			var writableServer = separateClients ? localServer : seedServer;
			seedServer.createEntityTable("seed");
			writableServer.createEntityTable("overlay");
			var seedClient = new DynamoDbClientManager(List.of("seed"), seedServer.asyncClient());
			var localClient = new DynamoDbClientManager(List.of("overlay"), writableServer.asyncClient());
			var seed = manager(seedClient).getVirtualDatabase("org");
			var overlay = manager(true, seedClient, localClient).getVirtualDatabase("org");
			var original = seed.put(new Entry("original"));
			var untouched = seed.put(new Entry("untouched"));
			var changed = overlay.get(Entry.class, original.getId());
			changed.setName("local");
			overlay.put(changed);
			var stale = overlay.get(Entry.class, original.getId());
			var current = manager(true, seedClient, localClient).getVirtualDatabase("org").get(Entry.class, original.getId());
			current.setName("latest");
			overlay.put(current);

			var failure = assertThrows(CompletionException.class, () -> overlay.delete(stale, true));
			assertTrue(failure.getCause() instanceof RevisionMismatchException);
			assertEquals("latest", overlay.get(Entry.class, original.getId()).getName());

			overlay.delete(overlay.get(Entry.class, original.getId()), true);
			overlay.delete(overlay.get(Entry.class, untouched.getId()), true);
			var created = overlay.put(new Entry("local only"));
			overlay.delete(created, true);

			var reloaded = manager(true, seedClient, localClient).getVirtualDatabase("org");
			assertFalse(reloaded.getOptional(Entry.class, original.getId()).isPresent());
			assertFalse(reloaded.getOptional(Entry.class, untouched.getId()).isPresent());
			assertFalse(reloaded.getOptional(Entry.class, created.getId()).isPresent());
			assertTrue(reloaded.query(Entry.class).isEmpty());
			var reloadedSeed = manager(seedClient).getVirtualDatabase("org");
			assertEquals("original", reloadedSeed.get(Entry.class, original.getId()).getName());
			assertEquals(original.getRevision(), reloadedSeed.get(Entry.class, original.getId()).getRevision());
			assertTrue(reloadedSeed.getOptional(Entry.class, untouched.getId()).isPresent());
			assertFalse(reloadedSeed.getOptional(Entry.class, created.getId()).isPresent());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void defaultDeletionStillRemovesWritableOverride(boolean separateClients) throws Exception {
		try (var seedServer = LocalDynamoDbServer.start(); var localServer = LocalDynamoDbServer.start()) {
			var writableServer = separateClients ? localServer : seedServer;
			seedServer.createEntityTable("seed");
			writableServer.createEntityTable("overlay");
			var seedClient = new DynamoDbClientManager(List.of("seed"), seedServer.asyncClient());
			var writableClient = new DynamoDbClientManager(List.of("overlay"), writableServer.asyncClient());
			var original = manager(seedClient).getVirtualDatabase("org").put(new Entry("seed"));
			var overlay = manager(seedClient, writableClient).getVirtualDatabase("org");
			var changed = overlay.get(Entry.class, original.getId());
			changed.setName("override");
			overlay.put(changed);
			overlay.delete(overlay.get(Entry.class, original.getId()), true);

			assertFalse(manager(writableClient).getVirtualDatabase("org").getOptional(Entry.class, original.getId()).isPresent());
			assertEquals("seed", manager(seedClient, writableClient).getVirtualDatabase("org").get(Entry.class, original.getId()).getName());
		}
	}

	@Test
	void legacyBuilderRoutesSeedAndWritableTablesWithEitherSetterOrder() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			server.createEntityTable("seed");
			server.createEntityTable("overlay");
			var seed = manager(new DynamoDbClientManager(List.of("seed"), server.asyncClient())).getVirtualDatabase("org");
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
				manager(new DynamoDbClientManager(List.of("seed"), server.asyncClient()))
					.getVirtualDatabase("org")
					.get(Entry.class, entry.getId())
					.getName()
			);
		}
	}

	@Test
	void rejectsMixedRoutingConfigurationsInEitherSetterOrder() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var table = new DynamoDbClientManager(List.of("table"), server.asyncClient());
			assertThrows(
				IllegalArgumentException.class,
				() -> DynamoDbManager.builder().tables("table").clientRouting(new ClientRouting(List.of(table)))
			);
			assertThrows(
				IllegalArgumentException.class,
				() -> DynamoDbManager.builder().clientRouting(new ClientRouting(List.of(table))).tables("table")
			);
			assertThrows(
				IllegalArgumentException.class,
				() -> DynamoDbManager.builder().dynamoDbAsyncClient(server.asyncClient()).clientRouting(new ClientRouting(List.of(table)))
			);
			assertThrows(
				IllegalArgumentException.class,
				() -> DynamoDbManager.builder().clientRouting(new ClientRouting(List.of(table))).dynamoDbAsyncClient(server.asyncClient())
			);
		}
	}

	@Test
	void validatesTableClientsForBothManagerAndDirectDriver() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var table = new DynamoDbClientManager(List.of("table"), server.asyncClient());
			for (var tables : List.of(List.<DynamoDbClientManager>of(), List.of(table, table))) {
				assertThrows(IllegalArgumentException.class, () -> DynamoDbManager.builder().clientRouting(new ClientRouting(tables)));
				assertThrows(IllegalArgumentException.class, () -> new ClientRouting(tables));
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
					new DynamoDbClientManager(List.of("seed"), firstServer.asyncClient()),
					new DynamoDbClientManager(List.of("middle"), secondServer.asyncClient()),
					new DynamoDbClientManager(List.of("overlay"), firstServer.asyncClient())
				)) {
				var entry = new Entry(table.tables().getFirst());
				entry.setId("same-id");
				manager(table).getVirtualDatabase("org").put(entry);
			}

			var requestOrder = new ArrayList<String>();
			var first = new RecordingClient(firstServer.asyncClient(), true, requestOrder);
			var second = new RecordingClient(secondServer.asyncClient(), false, requestOrder);
			var clientManager = new ClientRouting(
				List
					.of(
						new DynamoDbClientManager(List.of("seed"), first),
						new DynamoDbClientManager(List.of("middle"), second),
						new DynamoDbClientManager(List.of("overlay"), first)
					)
			);
			var manager = DynamoDbManager.builder().objectMapper(new ObjectMapper()).clientRouting(clientManager).build();

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
				manager(new DynamoDbClientManager(List.of(name), server.asyncClient())).getVirtualDatabase("org").put(entry);
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
	void batchesSharedTablesWithinMultipleClientGroups() throws Exception {
		try (var seedServer = LocalDynamoDbServer.start(); var overlayServer = LocalDynamoDbServer.start()) {
			for (var name : List.of("seed", "middle", "overlay")) {
				var server = name.equals("overlay") ? overlayServer : seedServer;
				server.createEntityTable(name);
				var entry = new Entry(name);
				entry.setId("same-id");
				manager(new DynamoDbClientManager(List.of(name), server.asyncClient())).getVirtualDatabase("org").put(entry);
			}
			var first = new RecordingClient(seedServer.asyncClient(), true, new ArrayList<>());
			var second = new RecordingClient(overlayServer.asyncClient(), false, new ArrayList<>());
			var names = new ArrayList<>(List.of("seed", "middle"));
			var groups = new ArrayList<>(
				List
					.of(
						new DynamoDbClientManager(names, first),
						new DynamoDbClientManager(List.of("overlay"), second)
					)
			);
			var clients = new ClientRouting(groups);
			names.clear();
			groups.clear();
			var manager = DynamoDbManager.builder().objectMapper(new ObjectMapper()).clientRouting(clients).build();

			assertEquals(List.of("seed", "middle", "overlay"), clients.tableNames());
			assertEquals(
				List
					.of(
						new ClientRouting.TableClient("seed", first),
						new ClientRouting.TableClient("middle", first),
						new ClientRouting.TableClient("overlay", second)
					),
				clients.tables()
			);
			assertEquals("overlay", manager.getVirtualDatabase("org").get(Entry.class, "same-id").getName());
			assertSame(second, manager.getDynamoDbAsyncClient());
			assertEquals(2, first.requests.size());
			assertEquals(Set.of("seed", "middle"), first.requests.getFirst().requestItems().keySet());
			assertEquals(first.requests.getFirst().requestItems(), first.requests.getLast().requestItems());
			assertEquals(1, second.requests.size());
			assertEquals(Set.of("overlay"), second.requests.getFirst().requestItems().keySet());
			assertThrows(
				IllegalArgumentException.class,
				() -> new ClientRouting(
					List
						.of(
							new DynamoDbClientManager(List.of("same"), first),
							new DynamoDbClientManager(List.of("same"), second)
						)
				)
			);
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
			assertSame(server.asyncClient(), first.writableClient());
			assertSame(first, ClientRouting.builder().routing(first).build());
			assertThrows(NullPointerException.class, () -> ClientRouting.builder().build());
			assertThrows(IllegalArgumentException.class, () -> ClientRouting.builder().tables(List.of()).build());
			assertThrows(UnsupportedOperationException.class, () -> first.clients().clear());
			assertThrows(UnsupportedOperationException.class, () -> first.clients().getFirst().tables().clear());
		}
	}

	@Test
	void sharedRoutingIsValidatedAndImmutable() throws Exception {
		try (var server = LocalDynamoDbServer.start()) {
			var names = new ArrayList<>(List.of("seed", "overlay"));
			var clientManager = ClientRouting.shared(names, server.asyncClient());
			names.clear();
			assertEquals(List.of("seed", "overlay"), clientManager.tableNames());
			assertEquals("overlay", clientManager.writableTable());
			assertThrows(UnsupportedOperationException.class, () -> clientManager.tables().clear());
			assertThrows(UnsupportedOperationException.class, () -> clientManager.tables().clear());
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

	private static DynamoDbManager manager(DynamoDbClientManager... tables) {
		return manager(false, tables);
	}

	private static DynamoDbManager manager(boolean retainOverlayDeletes, DynamoDbClientManager... tables) {
		return DynamoDbManager
			.builder()
			.objectMapper(new ObjectMapper())
			.clientRouting(new ClientRouting(List.of(tables)))
			.retainOverlayDeletes(retainOverlayDeletes)
			.build();
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
