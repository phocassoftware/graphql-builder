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

import java.lang.reflect.Parameter;

import com.phocassoftware.graphql.database.dynamo.history.lambda.HistoryLambda;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;

public class HistoryProcessor {

	private String[] tables;
	private DynamoDbClient client;
	private DynamoDbStreamsAsyncClient streamClient;

	public HistoryProcessor(DynamoDbClient client, DynamoDbStreamsAsyncClient streamClient, Parameter parameter, String[] tables) {
		this.tables = tables;
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
		for (final String table : tables) {
			try {
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

			} catch (ResourceNotFoundException e) {
				// ignore
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
