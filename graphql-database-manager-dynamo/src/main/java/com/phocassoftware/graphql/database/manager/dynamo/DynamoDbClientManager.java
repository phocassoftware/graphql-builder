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

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

/** Client/table groups in overlay order; the last table is the writable table. */
public final class DynamoDbClientManager {
	private final List<ClientTables> clients;

	/** Groups and their tables are in overlay order; the last table receives writes. */
	public DynamoDbClientManager(List<ClientTables> clients) {
		this.clients = List.copyOf(clients);
		Preconditions.checkArgument(!this.clients.isEmpty(), "Empty client array");
		var names = tableNames();
		Preconditions.checkArgument(names.stream().distinct().count() == names.size(), "Table client names must be unique");
	}

	public static DynamoDbClientManager shared(List<String> tables, DynamoDbAsyncClient client) {
		return new DynamoDbClientManager(List.of(new ClientTables(client, tables)));
	}

	public static DynamoDbClientManager perTable(List<DynamoDbTable> tables) {
		return new DynamoDbClientManager(tables.stream().map(table -> new ClientTables(table.client(), List.of(table.name()))).toList());
	}

	public List<DynamoDbTable> tables() {
		return clients.stream().flatMap(group -> group.tables().stream().map(name -> new DynamoDbTable(name, group.client()))).toList();
	}

	public List<String> tableNames() {
		return clients.stream().flatMap(group -> group.tables().stream()).toList();
	}

	public DynamoDbTable writableTable() {
		var group = clients.getLast();
		return new DynamoDbTable(group.tables().getLast(), group.client());
	}

	/** Batch requests in overlay order, sharing one request for each client/table group. */
	public List<ClientTables> readBatches() {
		return clients;
	}

	/** Fetches all configured tables, retrying each group on its own client. */
	CompletableFuture<Flattener> getItems(List<Map<String, AttributeValue>> entries, int maxRetry) {
		var result = CompletableFuture.completedFuture(Flattener.create(tableNames(), false));
		for (var batch : readBatches()) {
			Map<String, KeysAndAttributes> items = new HashMap<>();
			for (var table : batch.tables()) {
				items.put(table, KeysAndAttributes.builder().keys(entries).consistentRead(true).build());
			}
			result = result.thenCompose(flattener -> getItems(0, batch.client(), items, flattener, maxRetry));
		}
		return result;
	}

	private CompletableFuture<Flattener> getItems(
		int count,
		DynamoDbAsyncClient client,
		Map<String, KeysAndAttributes> items,
		Flattener flattener,
		int maxRetry
	) {
		if (count > maxRetry) {
			throw new RuntimeException("Failed to get keys from dynamo after " + maxRetry + " attempts");
		}
		var delay = CompletableFuture.delayedExecutor(100 * count * count, TimeUnit.MILLISECONDS);
		return CompletableFuture
			.supplyAsync(
				() -> client
					.batchGetItem(builder -> builder.requestItems(items))
					.thenCompose(response -> {
						response.responses().forEach(flattener::add);
						return response.unprocessedKeys().isEmpty()
							? CompletableFuture.completedFuture(flattener)
							: getItems(count + 1, client, response.unprocessedKeys(), flattener, maxRetry);
					}),
				delay
			)
			.thenCompose(result -> result);
	}

	public record ClientTables(DynamoDbAsyncClient client, List<String> tables) {
		public ClientTables {
			Objects.requireNonNull(client, "client");
			tables = List.copyOf(tables);
			Preconditions.checkArgument(!tables.isEmpty(), "Empty table array");
			Preconditions.checkArgument(tables.stream().noneMatch(String::isBlank), "Table name must not be blank");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Collects configuration separately from validated client groups. */
	public static final class Builder {
		private DynamoDbClientManager clientManager;
		private List<String> tables;
		private DynamoDbAsyncClient client;

		private Builder() {}

		public Builder tables(List<String> tables) {
			Preconditions.checkArgument(clientManager == null, "Set clientManager or tables, not both");
			this.tables = List.copyOf(tables);
			return this;
		}

		public Builder client(DynamoDbAsyncClient client) {
			Preconditions.checkArgument(clientManager == null, "Set clientManager or dynamoDbAsyncClient, not both");
			this.client = client;
			return this;
		}

		public Builder clientManager(DynamoDbClientManager clientManager) {
			Preconditions.checkArgument(this.clientManager == null, "Client manager is already configured");
			Preconditions.checkArgument(tables == null, "Set clientManager or tables, not both");
			Preconditions.checkArgument(client == null, "Set clientManager or dynamoDbAsyncClient, not both");
			this.clientManager = Objects.requireNonNull(clientManager, "clientManager");
			return this;
		}

		public DynamoDbClientManager build() {
			if (clientManager != null) {
				return clientManager;
			}
			Preconditions.checkNotNull(tables, "Tables must be set");
			Preconditions.checkArgument(!tables.isEmpty(), "Empty table array");
			if (client == null) {
				client = DynamoDbAsyncClient.create();
			}
			return shared(tables, client);
		}
	}

}
