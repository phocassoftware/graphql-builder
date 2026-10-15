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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Client groups in overlay order; the last table receives writes. */
public sealed class ClientRouting permits ClientRouting.LocalOverlayRouting {
	public record TableClient(String name, DynamoDbAsyncClient client) {}

	private final List<DynamoDbClientManager> clients;
	private final List<TableClient> tables;
	public ClientRouting(List<DynamoDbClientManager> clients) {
		this.clients = List.copyOf(clients);
		Preconditions.checkArgument(!this.clients.isEmpty(), "Empty client array");
		this.tables = this.clients
			.stream()
			.flatMap(manager -> manager.tables().stream().map(table -> new TableClient(table, manager.client())))
			.toList();
		var tables = tableNames();
		Preconditions.checkArgument(tables.stream().distinct().count() == tables.size(), "Table client names must be unique");
	}

	/** Retain deletion markers in the writable table to keep seed entities hidden. */
	public static ClientRouting localOverlay(List<DynamoDbClientManager> clients) {
		return new LocalOverlayRouting(clients);
	}

	static final class LocalOverlayRouting extends ClientRouting {
		private LocalOverlayRouting(List<DynamoDbClientManager> clients) {
			super(clients);
		}
	}

	public static ClientRouting shared(List<String> tables, DynamoDbAsyncClient client) {
		return new ClientRouting(List.of(new DynamoDbClientManager(tables, client)));
	}

	public List<DynamoDbClientManager> clients() {
		return clients;
	}

	public List<TableClient> tables() {
		return tables;
	}

	public List<String> tableNames() {
		return tables.stream().map(TableClient::name).toList();
	}

	public String writableTable() {
		return clients.getLast().tables().getLast();
	}

	public DynamoDbAsyncClient writableClient() {
		return clients.getLast().client();
	}

	CompletableFuture<Flattener> getItems(List<Map<String, AttributeValue>> entries, int maxRetry) {
		var result = CompletableFuture.completedFuture(Flattener.create(tableNames(), false));
		for (var manager : clients) {
			result = result.thenCompose(flattener -> manager.getItems(entries, flattener, maxRetry));
		}
		return result;
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Collects configuration separately from validated client groups. */
	public static final class Builder {
		private ClientRouting routing;
		private List<String> tables;
		private DynamoDbAsyncClient client;

		private Builder() {}

		public Builder tables(List<String> tables) {
			Preconditions.checkArgument(routing == null, "Set routing or tables, not both");
			this.tables = List.copyOf(tables);
			return this;
		}

		public Builder client(DynamoDbAsyncClient client) {
			Preconditions.checkArgument(routing == null, "Set routing or dynamoDbAsyncClient, not both");
			this.client = client;
			return this;
		}

		public Builder routing(ClientRouting routing) {
			Preconditions.checkArgument(this.routing == null, "Routing is already configured");
			Preconditions.checkArgument(tables == null, "Set routing or tables, not both");
			Preconditions.checkArgument(client == null, "Set routing or dynamoDbAsyncClient, not both");
			this.routing = Objects.requireNonNull(routing, "routing");
			return this;
		}

		public ClientRouting build() {
			if (routing != null) {
				return routing;
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
