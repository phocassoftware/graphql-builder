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
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

sealed interface ClientRouting {
	List<String> tables();

	DynamoDbAsyncClient clientFor(String table);

	default String writableTable() {
		return tables().getLast();
	}

	default DynamoDbAsyncClient writableClient() {
		return clientFor(writableTable());
	}

	default ClientRouting withTables(List<String> tables) {
		throw new IllegalArgumentException("Set tables or tableClients, not both");
	}

	default ClientRouting withClient(DynamoDbAsyncClient client) {
		throw new IllegalArgumentException("Set tableClients without dynamoDbAsyncClient");
	}

	default ClientRouting withTableClients(List<DynamoDbTable> tables) {
		return new PerTable(tables);
	}

	default ClientRouting build() {
		return this;
	}

	record Shared(List<String> tables, DynamoDbAsyncClient client) implements ClientRouting {
		public Shared {
			tables = tables == null ? null : List.copyOf(tables);
		}

		@Override
		public DynamoDbAsyncClient clientFor(String table) {
			return client;
		}

		@Override
		public ClientRouting withTables(List<String> tables) {
			return new Shared(tables, client);
		}

		@Override
		public ClientRouting withClient(DynamoDbAsyncClient client) {
			return new Shared(tables, client);
		}

		@Override
		public ClientRouting withTableClients(List<DynamoDbTable> tableClients) {
			Preconditions.checkArgument(tables == null, "Set tables or tableClients, not both");
			Preconditions.checkArgument(client == null, "Set tableClients without dynamoDbAsyncClient");
			return new PerTable(tableClients);
		}

		@Override
		public ClientRouting build() {
			Preconditions.checkNotNull(tables, "Tables must be set");
			Preconditions.checkArgument(!tables.isEmpty(), "Empty table array");
			return client == null ? new Shared(tables, DynamoDbAsyncClient.create()) : this;
		}
	}

	record PerTable(List<DynamoDbTable> tableClients) implements ClientRouting {
		public PerTable {
			tableClients = List.copyOf(tableClients);
			Preconditions.checkArgument(!tableClients.isEmpty(), "Empty table client array");
			Preconditions
				.checkArgument(
					tableClients.stream().map(DynamoDbTable::name).distinct().count() == tableClients.size(),
					"Table client names must be unique"
				);
		}

		@Override
		public List<String> tables() {
			return tableClients.stream().map(DynamoDbTable::name).toList();
		}

		@Override
		public DynamoDbAsyncClient clientFor(String table) {
			return tableClients.stream().filter(entry -> entry.name().equals(table)).findFirst().orElseThrow().client();
		}
	}
}
