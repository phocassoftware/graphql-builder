package com.fleetpin.graphql.database.manager.test;

import com.fleetpin.graphql.database.dynamo.history.lambda.HistoryLambda;
import com.fleetpin.graphql.database.manager.test.annotations.DatabaseNames;
import java.lang.reflect.Parameter;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;

public class HistoryProcessor {

	private String[] tables;
	private DynamoDbClient client;
	private DynamoDbStreamsAsyncClient streamClient;

	public HistoryProcessor(DynamoDbClient client, DynamoDbStreamsAsyncClient streamClient, Parameter parameter, String organisationId) {
		final var databaseNames = parameter.getAnnotation(DatabaseNames.class);
		this.tables = databaseNames != null ? databaseNames.value() : new String[] { "table" };
		this.client = client;
		this.streamClient = streamClient;
	}

	static class Processor extends HistoryLambda {

		private final DynamoDbClient client;
		private final String tableName;

		public Processor(DynamoDbClient client, String tableName) {
			this.client = client;
			this.tableName = tableName;
		}

		@Override
		public DynamoDbClient getClient() {
			return client;
		}

		@Override
		public String getTableName() {
			return tableName;
		}
	}

	public void process() {
		try {
			for (final String table : tables) {
				var streamArn = client.describeTable(builder -> builder.tableName(table).build()).table().latestStreamArn();

				var shards = streamClient.describeStream(builder -> builder.streamArn(streamArn).build()).get().streamDescription().shards();
				for (final var shard : shards) {
					var shardIterator = streamClient
						.getShardIterator(builder -> builder.shardIteratorType(ShardIteratorType.TRIM_HORIZON).streamArn(streamArn).shardId(shard.shardId()))
						.get()
						.shardIterator();
					var response = streamClient.getRecords(builder -> builder.shardIterator(shardIterator)).get();
					var processor = new Processor(client, table + "_history");
					processor.process(response.records());
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
