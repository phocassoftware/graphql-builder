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

import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Embedded DynamoDB Local with isolated in-memory tables. */
public final class LocalDynamoDbServer implements AutoCloseable {
	private final DynamoDBProxyServer server;
	private final DynamoDbClient client;
	private final DynamoDbAsyncClient asyncClient;

	private LocalDynamoDbServer(DynamoDBProxyServer server, DynamoDbClient client, DynamoDbAsyncClient asyncClient) {
		this.server = server;
		this.client = client;
		this.asyncClient = asyncClient;
	}

	public static LocalDynamoDbServer start() throws Exception {
		var port = DynamoDbInitializer.findFreePort();
		var server = DynamoDbInitializer.startDynamoServer(port);
		try {
			return new LocalDynamoDbServer(server, DynamoDbInitializer.startDynamoClient(port), DynamoDbInitializer.startDynamoAsyncClient(port));
		} catch (Exception e) {
			server.stop();
			throw e;
		}
	}

	public void createEntityTable(String name) throws Exception {
		DynamoDbInitializer.createTable(client, name);
	}

	public void createHistoryTable(String name) throws Exception {
		DynamoDbInitializer.createHistoryTable(client, name);
	}

	public DynamoDbAsyncClient asyncClient() {
		return asyncClient;
	}

	public DynamoDbClient client() {
		return client;
	}

	@Override
	public void close() throws Exception {
		asyncClient.close();
		client.close();
		server.stop();
	}
}
