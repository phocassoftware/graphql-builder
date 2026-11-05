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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

/** One client and the ordered tables fetched together through it. */
public record DynamoDbClientManager(List<String> tables, DynamoDbAsyncClient client) {
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
		return getItems(0, items, flattener::add, maxRetry).thenApply(ignored -> flattener);
	}

	/**
	 * Reads raw keys from all configured tables without requiring the entity key or item schema.
	 * The caller supplies consistency and projection settings and must keep the total number of
	 * keys across all tables at or below DynamoDB's 100-key batch limit. Unprocessed keys are
	 * retried at most {@code maxRetry} times after the initial request; exhaustion fails the
	 * future instead of returning partial results. Empty keys do not issue a request.
	 */
	public CompletableFuture<Map<String, List<Map<String, AttributeValue>>>> getItems(KeysAndAttributes attributes, int maxRetry) {
		Objects.requireNonNull(attributes, "attributes");
		Preconditions.checkArgument(maxRetry >= 0, "maxRetry must not be negative");
		Preconditions.checkArgument(attributes.keys().size() <= 100 / tables.size(), "A batch must contain at most 100 keys across all tables");
		if (attributes.keys().isEmpty()) {
			return CompletableFuture.completedFuture(Map.of());
		}
		Map<String, KeysAndAttributes> items = new HashMap<>();
		for (var table : tables) {
			items.put(table, attributes);
		}
		Map<String, List<Map<String, AttributeValue>>> results = new HashMap<>();
		return getItems(0, items, (table, values) -> results.computeIfAbsent(table, ignored -> new ArrayList<>()).addAll(values), maxRetry)
			.thenApply(ignored -> results);
	}

	private CompletableFuture<Void> getItems(
		int count,
		Map<String, KeysAndAttributes> items,
		BiConsumer<String, List<Map<String, AttributeValue>>> consumer,
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
						response.responses().forEach(consumer);
						return response.unprocessedKeys().isEmpty()
							? CompletableFuture.<Void>completedFuture(null)
							: getItems(count + 1, response.unprocessedKeys(), consumer, maxRetry);
					}),
				delay
			)
			.thenCompose(result -> result);
	}

}
