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

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.Database;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;

final class DynamoDbInitializer {

	@SuppressWarnings("unchecked")
	static void createTable(final DynamoDbClient client, final String name) throws ExecutionException, InterruptedException {
		try {
			client.describeTable(builder -> builder.tableName(name));
			return;
		} catch (ResourceNotFoundException ignored) {}

		// looks like bug within local dynamodb client around creating multiple tables at the same time
		synchronized (DynamoDbInitializer.class) {
			client
				.createTable(
					t -> t
						.tableName(name)
						.keySchema(
							KeySchemaElement.builder().attributeName("organisationId").keyType(KeyType.HASH).build(),
							KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build()
						)
						.streamSpecification(streamSpecification -> streamSpecification.streamEnabled(true).streamViewType(StreamViewType.NEW_IMAGE))
						.globalSecondaryIndexes(
							GlobalSecondaryIndex
								.builder()
								.indexName("secondaryGlobal")
								.provisionedThroughput(p -> p.readCapacityUnits(10L).writeCapacityUnits(10L))
								.projection(b -> b.projectionType(ProjectionType.ALL))
								.keySchema(KeySchemaElement.builder().attributeName("secondaryGlobal").keyType(KeyType.HASH).build())
								.build(),
							GlobalSecondaryIndex
								.builder()
								.indexName("parallelIndex")
								.provisionedThroughput(p -> p.readCapacityUnits(10L).writeCapacityUnits(10L))
								.projection(b -> b.projectionType(ProjectionType.ALL))
								.keySchema(
									KeySchemaElement.builder().attributeName("organisationId").keyType(KeyType.HASH).build(),
									KeySchemaElement.builder().attributeName("parallelHash").keyType(KeyType.RANGE).build()
								)
								.build()
						)
						.localSecondaryIndexes(
							builder -> builder
								.indexName("secondaryOrganisation")
								.projection(b -> b.projectionType(ProjectionType.ALL))
								.keySchema(
									KeySchemaElement.builder().attributeName("organisationId").keyType(KeyType.HASH).build(),
									KeySchemaElement.builder().attributeName("secondaryOrganisation").keyType(KeyType.RANGE).build()
								)
						)
						.attributeDefinitions(
							AttributeDefinition.builder().attributeName("organisationId").attributeType(ScalarAttributeType.S).build(),
							AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
							AttributeDefinition.builder().attributeName("secondaryGlobal").attributeType(ScalarAttributeType.S).build(),
							AttributeDefinition.builder().attributeName("secondaryOrganisation").attributeType(ScalarAttributeType.S).build(),
							AttributeDefinition.builder().attributeName("parallelHash").attributeType(ScalarAttributeType.S).build()
						)
						.provisionedThroughput(p -> p.readCapacityUnits(10L).writeCapacityUnits(10L).build())
				);

		}
	}

	static void createHistoryTable(final DynamoDbClient client, final String name) throws ExecutionException, InterruptedException {
		try {
			client.describeTable(builder -> builder.tableName(name));
			return;
		} catch (ResourceNotFoundException ignored) {}

		// looks like bug within local dynamodb client around creating multiple tables at the same time
		synchronized (DynamoDbInitializer.class) {
			client
				.createTable(
					t -> t
						.tableName(name)
						.keySchema(
							KeySchemaElement.builder().attributeName("organisationIdType").keyType(KeyType.HASH).build(),
							KeySchemaElement.builder().attributeName("idRevision").keyType(KeyType.RANGE).build()
						)
						.localSecondaryIndexes(
							builder -> builder
								.indexName("startsWithUpdatedAt")
								.projection(b -> b.projectionType(ProjectionType.ALL))
								.keySchema(
									KeySchemaElement.builder().attributeName("organisationIdType").keyType(KeyType.HASH).build(),
									KeySchemaElement.builder().attributeName("startsWithUpdatedAt").keyType(KeyType.RANGE).build()
								),
							builder -> builder
								.indexName("idDate")
								.projection(b -> b.projectionType(ProjectionType.ALL))
								.keySchema(
									KeySchemaElement.builder().attributeName("organisationIdType").keyType(KeyType.HASH).build(),
									KeySchemaElement.builder().attributeName("idDate").keyType(KeyType.RANGE).build()
								)
						)
						.attributeDefinitions(
							AttributeDefinition.builder().attributeName("organisationIdType").attributeType(ScalarAttributeType.S).build(),
							AttributeDefinition.builder().attributeName("idRevision").attributeType(ScalarAttributeType.B).build(),
							AttributeDefinition.builder().attributeName("idDate").attributeType(ScalarAttributeType.B).build(),
							AttributeDefinition.builder().attributeName("startsWithUpdatedAt").attributeType(ScalarAttributeType.B).build()
						)
						.provisionedThroughput(p -> p.readCapacityUnits(10L).writeCapacityUnits(10L).build())
				);
		}
	}

	static synchronized DynamoDBProxyServer startDynamoServer(final String port) throws Exception {
		final String[] localArgs = { "-inMemory", "-disableTelemetry", "-port", port };
		final var server = ServerRunner.createServerFromCommandLineArgs(localArgs);
		server.start();

		return server;
	}

	static DynamoDbAsyncClient startDynamoAsyncClient(final String port) throws URISyntaxException {
		return DynamoDbAsyncClient
			.builder()
			.region(Region.AWS_GLOBAL)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("anything", "anything")))
			.endpointOverride(new URI("http://localhost:" + port))
			.build();
	}

	static DynamoDbClient startDynamoClient(final String port) throws URISyntaxException {
		return DynamoDbClient
			.builder()
			.region(Region.AWS_GLOBAL)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("anything", "anything")))
			.endpointOverride(new URI("http://localhost:" + port))
			.build();
	}

	static DynamoDbStreamsAsyncClient startDynamoStreamClient(final String port) throws URISyntaxException {
		return DynamoDbStreamsAsyncClient
			.builder()
			.region(Region.AWS_GLOBAL)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("anything", "anything")))
			.endpointOverride(new URI("http://localhost:" + port))
			.build();
	}

	static String findFreePort() throws IOException {
		final var serverSocket = new ServerSocket(0);
		final var port = String.valueOf(serverSocket.getLocalPort());
		serverSocket.close();

		return port;
	}

	static Database getEmbeddedDatabase(final DynamoDbManager dynamoDbManager, final String organisationId) {
		final var database = dynamoDbManager.getDatabase(organisationId);

		return database;
	}

	static DynamoDbManager getDatabaseManager(
		final DynamoDbAsyncClient client,
		final String[] tables,
		String historyTable,
		boolean globalEnabled,
		boolean hashed,
		String classpath,
		String parallelIndex,
		ObjectMapper objectMapper
	) {
		return DynamoDbManager
			.builder()
			.tables(tables)
			.dynamoDbAsyncClient(client)
			.historyTable(historyTable)
			.global(globalEnabled)
			.hash(hashed)
			.classPath(classpath)
			.parallelIndex(parallelIndex)
			.objectMapper(objectMapper)
			.build();
	}
}
