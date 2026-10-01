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
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import static org.mockito.Mockito.mock;

class DynamoDbSeedClientTest {
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

			local.delete(local.get(Entry.class, "updated").get(), true).get();
			assertNull(local.get(Entry.class, "updated").get());
			assertEquals(1, local.query(Entry.class).get().size());
			assertEquals("original", seed.get(Entry.class, "updated").get().getName());
		} finally {
			localServer.stop();
			seedServer.stop();
		}
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
}
