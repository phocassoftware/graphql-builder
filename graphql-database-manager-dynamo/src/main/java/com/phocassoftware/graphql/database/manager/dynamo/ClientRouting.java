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
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/** Validated routing in overlay order; the last table is the writable table. */
public final class ClientRouting {
	private final List<DynamoDbTable> tables;
	private final List<String> tableNames;
	private final List<ClientTables> readBatches;

	private ClientRouting(List<DynamoDbTable> tables, boolean shared) {
		this.tables = List.copyOf(tables);
		Preconditions.checkArgument(!this.tables.isEmpty(), "Empty table client array");
		this.tableNames = this.tables.stream().map(DynamoDbTable::name).toList();
		Preconditions.checkArgument(tableNames.stream().distinct().count() == tableNames.size(), "Table client names must be unique");
		this.readBatches = shared
			? List.of(new ClientTables(this.tables.getFirst().client(), tableNames))
			: this.tables.stream().map(table -> new ClientTables(table.client(), List.of(table.name()))).toList();
	}

	public static ClientRouting shared(List<String> tables, DynamoDbAsyncClient client) {
		Objects.requireNonNull(client, "client");
		return new ClientRouting(tables.stream().map(table -> new DynamoDbTable(table, client)).toList(), true);
	}

	public static ClientRouting perTable(List<DynamoDbTable> tables) {
		return new ClientRouting(tables, false);
	}

	public List<DynamoDbTable> tables() {
		return tables;
	}

	public List<String> tableNames() {
		return tableNames;
	}

	public DynamoDbTable writableTable() {
		return tables.getLast();
	}

	/** Batch requests in read order, with shared routing using one batch for all tables. */
	public List<ClientTables> readBatches() {
		return readBatches;
	}

	public record ClientTables(DynamoDbAsyncClient client, List<String> tables) {
		public ClientTables {
			Objects.requireNonNull(client, "client");
			tables = List.copyOf(tables);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Collects configuration separately from validated routing. */
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
