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

import java.lang.reflect.Parameter;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import tools.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.Database;
import com.phocassoftware.graphql.database.manager.VirtualDatabase;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import com.phocassoftware.graphql.database.manager.test.TestDatabaseProvider.ServerWrapper;
import com.phocassoftware.graphql.database.manager.test.annotations.*;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class ArgumentProvider {

	private final String uniqueId;
	private final ServerWrapper wrapper;
	private final Parameter parameter;
	private final Boolean withHistory;
	private final boolean hashed;
	private final String classPath;
	private final ObjectMapper objectMapper;
	private final Consumer<String> tableDeleteConsumer;

	public ArgumentProvider(
		String uniqueId,
		ServerWrapper wrapper,
		Parameter parameter,
		Boolean withHistory,
		boolean hashed,
		String classPath,
		ObjectMapper objectMapper,
		Consumer<String> tableDeleteConsumer

	) {
		this.uniqueId = uniqueId;
		this.wrapper = wrapper;
		this.parameter = parameter;
		this.withHistory = withHistory;
		this.hashed = hashed;
		this.classPath = classPath;
		this.objectMapper = objectMapper;
		this.tableDeleteConsumer = tableDeleteConsumer;
	}

	public DynamoDbManager getDynamoDbManager(boolean scope) {

		var client = wrapper.clientAsync();
		final var globalEnabledAnnotation = parameter.getAnnotation(GlobalEnabled.class);

		var tables = getTables(scope);

		var globalEnabled = true;
		if (globalEnabledAnnotation != null) {
			globalEnabled = globalEnabledAnnotation.value();
		}

		return getDatabaseManager(client, tables.tables(), tables.historyTable(), globalEnabled, hashed, classPath, "parallelIndex", objectMapper);
	}

	public HistoryProcessor getHistoryProcessor() {
		var s1 = Stream.of(getTables(true).tables());
		var s2 = Stream.of(getTables(false).tables());

		var tables = Stream.concat(s1, s2).toArray(String[]::new);
		return new HistoryProcessor(wrapper.client(), wrapper.streamClient(), parameter, tables);
	}

	private Tables getTables(boolean scope) {
		try {
			String[] tables = new String[] { "table" };
			String historyTable = "table_history";
			final var databaseNames = parameter.getAnnotation(DatabaseNames.class);
			var client = wrapper.client();

			if (databaseNames != null) {
				tables = Stream.of(databaseNames.value()).map(name -> name + "_" + uniqueId).toArray(String[]::new);
				for (final String table : tables) {
					createTable(client, table);
					tableDeleteConsumer.accept(table);
					if (withHistory) {
						historyTable = table + "_history";
						createHistoryTable(client, historyTable);
						tableDeleteConsumer.accept(historyTable);
					}
				}
			} else if (scope || withHistory) {
				tables = new String[] { "table" + "_" + uniqueId };
				historyTable = tables[0] + "_history";
				createTable(client, tables[0]);
				createHistoryTable(client, historyTable);
				tableDeleteConsumer.accept(historyTable);
			}
			return new Tables(tables, historyTable);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public DynamoDbAsyncClient getClientAsync() {
		return wrapper.clientAsync();
	}

	public DynamoDbClient getClient() {
		return wrapper.client();
	}

	public Database getDatabase() {
		var databaseOrganisation = parameter.getAnnotation(DatabaseOrganisation.class);
		var organisationId = databaseOrganisation != null ? databaseOrganisation.value() : UUID.randomUUID().toString();

		return getEmbeddedDatabase(getDynamoDbManager(false), organisationId);
	}

	public VirtualDatabase getVirtualDatabase() {
		return new VirtualDatabase(getDatabase());
	}

	private record Tables(String[] tables, String historyTable) {}

}
