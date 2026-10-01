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

import static com.phocassoftware.graphql.database.manager.test.DynamoDbInitializer.*;
import static org.junit.jupiter.api.Assertions.*;

import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.annotations.Hash;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDb;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import com.phocassoftware.graphql.database.manager.test.hashed.SimplerHasher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import static org.mockito.Mockito.mock;

class DynamoDbSeedClientTest {
	@Test
	void directConstructorRequiresSeedClient() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new DynamoDb(
				new ObjectMapperCreator().get(),
				List.of("local"),
				List.of("seed"),
				null,
				mock(DynamoDbAsyncClient.class),
				null,
				() -> "id",
				25,
				10,
				false,
				false,
				null,
				null
			)
		);
	}

	@Test
	void seedAndWritableTableNamesMustBeDistinct() {
		assertThrows(
			IllegalArgumentException.class,
			() -> DynamoDbManager
				.builder()
				.tables("same")
				.seedTables(mock(DynamoDbAsyncClient.class), "same")
				.objectMapper(new ObjectMapperCreator().get())
				.build()
		);
	}

	@Test
	void seedReadsAndLocalWritesUseDifferentClients() throws Exception {
		var seedPort = findFreePort();
		var localPort = findFreePort();
		var seedServer = startDynamoServer(seedPort);
		var localServer = startDynamoServer(localPort);
		try (
			var seedSync = startDynamoClient(seedPort);
			var localSync = startDynamoClient(localPort);
			var seedClient = startDynamoAsyncClient(seedPort);
			var localClient = startDynamoAsyncClient(localPort)) {
			createTable(seedSync, "seed");
			createTable(localSync, "local");
			var mapper = new ObjectMapperCreator().get();
			var seedManager = DynamoDbManager.builder().dynamoDbAsyncClient(seedClient).tables("seed").objectMapper(mapper).global(false).hash(false).build();
			var localManager = DynamoDbManager
				.builder()
				.dynamoDbAsyncClient(localClient)
				.tables("local")
				.seedTables(seedClient, "seed")
				.objectMapper(mapper)
				.global(false)
				.hash(false)
				.classPath("com.phocassoftware.graphql.database.manager.test")
				.build();
			var seed = seedManager.getDatabase("org");
			var local = localManager.getDatabase("org");

			seed.put(new Entry("updated", "original")).get();
			seed.put(new Entry("deleted", "original")).get();
			assertEquals("original", local.get(Entry.class, "updated").get().getName());
			assertEquals(2, local.query(Entry.class).get().size());

			var updated = local.get(Entry.class, "updated").get();
			updated.setName("local update");
			local.put(updated).get();
			local.put(new Entry("created", "local create")).get();
			local.delete(local.get(Entry.class, "deleted").get(), true).get();

			assertEquals("local update", local.get(Entry.class, "updated").get().getName());
			assertEquals("local create", local.get(Entry.class, "created").get().getName());
			assertNull(local.get(Entry.class, "deleted").get());
			assertEquals(2, local.query(Entry.class).get().size());
			assertEquals("original", seed.get(Entry.class, "updated").get().getName());
			assertEquals("original", seed.get(Entry.class, "deleted").get().getName());
			assertNull(seed.get(Entry.class, "created").get());
			seedManager.getDatabase("other").put(new Entry("updated", "other organisation")).get();
			assertEquals(
				Map.of("org:updated", "local update", "org:created", "local create", "other:updated", "other organisation"),
				scanEntries(localManager)
			);

			local.delete(local.get(Entry.class, "updated").get(), true).get();
			assertNull(local.get(Entry.class, "updated").get());
			assertEquals(1, local.query(Entry.class).get().size());
			assertEquals("original", seed.get(Entry.class, "updated").get().getName());

			seed.put(new Entry("aaa", "hidden")).get();
			seed.put(new Entry("bbb", "visible")).get();
			local.delete(localManager.getDatabase("org").get(Entry.class, "aaa").get(), true).get();
			assertEquals("bbb", localManager.getDatabase("org").query(Entry.class, query -> query.limit(1)).get().getFirst().getId());
			assertEquals("other organisation", seedManager.getDatabase("other").get(Entry.class, "updated").get().getName());
			assertEquals("other organisation", localManager.getDatabase("other").get(Entry.class, "updated").get().getName());

			assertEquals(Map.of("org:bbb", "visible", "org:created", "local create", "other:updated", "other organisation"), scanEntries(localManager));

			seed.put(new HashedEntry("hash-seed", "seed hash")).get();
			var hashed = localManager.getDatabase("org").get(HashedEntry.class, "hash-seed").get();
			hashed.setName("local hash");
			localManager.getDatabase("org").put(hashed).get();
			Map<String, String> hashedScan = new ConcurrentHashMap<>();
			localManager
				.startTableScan(
					builder -> builder
						.parallelism(1)
						.updater(
							HashedEntry.class,
							(context, entry) -> hashedScan.put(context.getVirtualDatabase().getOrganisationId() + ":" + entry.getId(), entry.getName())
						)
				)
				.start()
				.join();
			assertEquals(Map.of("org:hash-seed", "local hash"), hashedScan);
		} finally {
			localServer.stop();
			seedServer.stop();
		}
	}

	private static Map<String, String> scanEntries(DynamoDbManager manager) {
		Map<String, String> scanned = new ConcurrentHashMap<>();
		manager
			.startTableScan(
				builder -> builder
					.parallelism(1)
					.updater(
						Entry.class,
						(context, entry) -> scanned.put(context.getVirtualDatabase().getOrganisationId() + ":" + entry.getId(), entry.getName())
					)
			)
			.start()
			.join();
		return scanned;
	}

	public static class Entry extends Table {
		private String name;

		public Entry() {}

		public Entry(String id, String name) {
			setId(id);
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Hash(SimplerHasher.class)
	public static class HashedEntry extends Table {
		private String name;

		public HashedEntry() {}

		public HashedEntry(String id, String name) {
			setId(id);
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
