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

import tools.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.DatabaseDriver;
import com.phocassoftware.graphql.database.manager.DatabaseManager;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public final class DynamoDbManager extends DatabaseManager {

	private final ObjectMapper mapper;
	private final Supplier<String> idGenerator;
	private final DynamoDbAsyncClient client;

	private DynamoDbManager(ObjectMapper mapper, Supplier<String> idGenerator, DynamoDbAsyncClient client, DatabaseDriver dynamoDb) {
		super(dynamoDb);
		this.mapper = mapper;
		this.idGenerator = idGenerator;
		this.client = client;
	}

	public static DyanmoDbManagerBuilder builder() {
		return new DyanmoDbManagerBuilder();
	}

	public static class DyanmoDbManagerBuilder {

		private DynamoDbAsyncClient client;
		private ObjectMapper mapper;
		private List<String> tables;
		private List<DynamoDbTable> tableClients;
		private Supplier<String> idGenerator;
		private DatabaseDriver database;
		private String historyTable;
		private int batchWriteSize = 25;
		private int maxRetry = 10;
		private boolean globalEnabled = true;
		private boolean hash = false;
		private String classPath = null;

		private String parallelIndex = null;

		public DyanmoDbManagerBuilder dynamoDbAsyncClient(DynamoDbAsyncClient client) {
			this.client = client;
			return this;
		}

		public DyanmoDbManagerBuilder objectMapper(ObjectMapper mapper) {
			this.mapper = mapper;
			return this;
		}

		public DyanmoDbManagerBuilder tables(List<String> tables) {
			this.tables = tables;
			return this;
		}

		public DyanmoDbManagerBuilder tables(String... tables) {
			this.tables = Arrays.asList(tables);
			return this;
		}

		/** Tables are ordered from seed to writable; the last client receives writes. */
		public DyanmoDbManagerBuilder tableClients(List<DynamoDbTable> tableClients) {
			this.tableClients = List.copyOf(tableClients);
			return this;
		}

		public DyanmoDbManagerBuilder tableClients(DynamoDbTable... tableClients) {
			return tableClients(Arrays.asList(tableClients));
		}

		public DyanmoDbManagerBuilder historyTable(String historyTable) {
			this.historyTable = historyTable;
			return this;
		}

		public DyanmoDbManagerBuilder idGenerator(Supplier<String> idGenerator) {
			this.idGenerator = idGenerator;
			return this;
		}

		public DyanmoDbManagerBuilder dynamoDb(final DatabaseDriver database) {
			this.database = database;
			return this;
		}

		public DyanmoDbManagerBuilder batchWriteSize(int batchWriteSize) {
			if (batchWriteSize < 0 || batchWriteSize > 25) {
				throw new RuntimeException("Batch write size must be between 0-25");
			}
			this.batchWriteSize = batchWriteSize;
			return this;
		}

		public DyanmoDbManagerBuilder maxRetry(int maxRetry) {
			this.maxRetry = maxRetry;
			return this;
		}

		public DyanmoDbManagerBuilder global(boolean global) {
			this.globalEnabled = global;
			return this;
		}

		public DyanmoDbManagerBuilder hash(boolean hash) {
			this.hash = hash;
			return this;
		}

		public DyanmoDbManagerBuilder classPath(String classPath) {
			if (!Strings.isNullOrEmpty(classPath)) {
				this.classPath = classPath;
			}
			return this;
		}

		public DyanmoDbManagerBuilder parallelIndex(String parallelIndex) {
			this.parallelIndex = parallelIndex;
			return this;
		}

		public DynamoDbManager build() {
			List<String> configuredTables = tables;
			DynamoDbAsyncClient writableClient = client;
			if (tableClients != null) {
				Preconditions.checkArgument(configuredTables == null, "Set tables or tableClients, not both");
				Preconditions.checkArgument(!tableClients.isEmpty(), "Empty table client array");
				Preconditions.checkArgument(writableClient == null, "Set tableClients without dynamoDbAsyncClient");
				configuredTables = tableClients.stream().map(DynamoDbTable::name).toList();
				Preconditions.checkArgument(configuredTables.stream().distinct().count() == configuredTables.size(), "Table client names must be unique");
				writableClient = tableClients.getLast().client();
			}
			Preconditions.checkNotNull(configuredTables, "Tables must be set");
			Preconditions.checkArgument(!configuredTables.isEmpty(), "Empty table array");
			Preconditions.checkNotNull(mapper, "Mapper is null");

			if (writableClient == null) {
				writableClient = client = DynamoDbAsyncClient.create();
			}
			if (idGenerator == null) {
				idGenerator = () -> UUID.randomUUID().toString();
			}

			database = Objects
				.requireNonNullElse(
					database,
					new DynamoDb(
						mapper,
						configuredTables,
						historyTable,
						writableClient,
						idGenerator,
						batchWriteSize,
						maxRetry,
						globalEnabled,
						hash,
						classPath,
						parallelIndex,
						tableClients == null ? Map.of()
							: tableClients.stream().collect(java.util.stream.Collectors.toMap(DynamoDbTable::name, DynamoDbTable::client))
					)
				);

			return new DynamoDbManager(mapper, idGenerator, writableClient, database);
		}
	}

	public ObjectMapper getMapper() {
		return mapper;
	}

	public String newId() {
		return idGenerator.get();
	}

	public <T> T convertTo(Map<String, AttributeValue> item, Class<T> type) {
		return TableUtil.convertTo(mapper, item, type);
	}

	public <T> T convertTo(AttributeValue item, Class<T> type) {
		return TableUtil.convertTo(mapper, item, type);
	}

	public AttributeValue toAttributes(Object entity) {
		var entries = TableUtil.toAttributes(mapper, entity);
		return AttributeValue.builder().m(entries).build();
	}

	public DynamoDbAsyncClient getDynamoDbAsyncClient() {
		return client;
	}
}
