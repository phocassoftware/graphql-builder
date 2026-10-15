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

/** One client and the ordered tables fetched together through it. */
public record DynamoDbClientManager(List<String> tables, DynamoDbAsyncClient client, boolean tombstoneDeletes) {
	public DynamoDbClientManager(List<String> tables, DynamoDbAsyncClient client) {
		this(tables, client, false);
	}

	/** Local overlay writes retain deletion markers to hide seed entities. */
	public static DynamoDbClientManager localOverlay(String table, DynamoDbAsyncClient client) {
		return new DynamoDbClientManager(List.of(table), client, true);
	}

	public DynamoDbClientManager {
		Objects.requireNonNull(client, "client");
		tables = List.copyOf(tables);
		Preconditions.checkArgument(!tables.isEmpty(), "Empty table array");
		Preconditions.checkArgument(tables.stream().noneMatch(String::isBlank), "Table name must not be blank");
		Preconditions.checkArgument(tables.stream().distinct().count() == tables.size(), "Table names must be unique");
	}

	/** Fetches all tables in one batch, retrying unprocessed keys on this client. */
	CompletableFuture<Flattener> getItems(List<Map<String, AttributeValue>> entries, Flattener flattener, int maxRetry) {
		Map<String, KeysAndAttributes> items = new HashMap<>();
		for (var table : tables) {
			items.put(table, KeysAndAttributes.builder().keys(entries).consistentRead(true).build());
		}
		return getItems(0, items, flattener, maxRetry);
	}

	private CompletableFuture<Flattener> getItems(
		int count,
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
							: getItems(count + 1, response.unprocessedKeys(), flattener, maxRetry);
					}),
				delay
			)
			.thenCompose(result -> result);
	}

}
