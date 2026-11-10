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

import static org.junit.jupiter.api.Assertions.*;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;

class DynamoDbClientManagerTest {

	@Test
	void readsRawKeysAndPreservesProjectionAndConsistency() {
		var item = Map.of("historyKey", fromS("org_1"), "status", fromS("SUCCESS"));
		var client = new RecordingClient(request -> CompletableFuture.completedFuture(response(Map.of("history", List.of(item)))));
		var attributes = keys("org_1");
		var results = new DynamoDbClientManager(List.of("history"), client).getItems(attributes, 4).join();

		assertEquals(Map.of("history", List.of(item)), results);
		assertEquals(Map.of("history", attributes), client.requests.getFirst().requestItems());
	}

	@Test
	void retriesOnlyUnprocessedKeysAndAccumulatesAllTables() {
		var first = Map.of("historyKey", fromS("org_1"));
		var second = Map.of("historyKey", fromS("org_2"));
		var pending = Map.of("history", keys("org_2"));
		var client = new RecordingClient(
			request -> CompletableFuture
				.completedFuture(
					request.requestItems().equals(pending)
						? response(Map.of("history", List.of(second)))
						: response(Map.of("history", List.of(first), "archive", List.of(first, second))).toBuilder().unprocessedKeys(pending).build()
				)
		);

		var results = new DynamoDbClientManager(List.of("history", "archive"), client).getItems(keys("org_1", "org_2"), 1).join();

		assertEquals(Map.of("history", List.of(first, second), "archive", List.of(first, second)), results);
		assertEquals(2, client.requests.size());
		assertEquals(pending, client.requests.getLast().requestItems());
	}

	@Test
	void failsAfterBoundedRetriesInsteadOfReturningPartialResults() {
		var client = new RecordingClient(
			request -> CompletableFuture
				.completedFuture(
					response(Map.of("history", List.of(Map.of("historyKey", fromS("org_1")))))
						.toBuilder()
						.unprocessedKeys(request.requestItems())
						.build()
				)
		);
		var manager = new DynamoDbClientManager(List.of("history"), client);

		var failure = assertThrows(CompletionException.class, () -> manager.getItems(keys("org_2"), 1).join());

		assertTrue(failure.getCause().getMessage().contains("Failed to get keys from dynamo"));
		assertEquals(2, client.requests.size());
	}

	@Test
	void zeroRetriesStillMakesTheInitialRequest() {
		var client = new RecordingClient(
			request -> CompletableFuture
				.completedFuture(
					BatchGetItemResponse.builder().unprocessedKeys(request.requestItems()).build()
				)
		);

		assertThrows(CompletionException.class, () -> new DynamoDbClientManager(List.of("history"), client).getItems(keys("org_1"), 0).join());
		assertEquals(1, client.requests.size());
	}

	@Test
	void propagatesClientFailure() {
		var cause = new IllegalStateException("DynamoDB unavailable");
		var client = new RecordingClient(request -> CompletableFuture.failedFuture(cause));

		var failure = assertThrows(CompletionException.class, () -> new DynamoDbClientManager(List.of("history"), client).getItems(keys("org_1"), 4).join());

		assertSame(cause, failure.getCause());
		assertEquals(1, client.requests.size());
	}

	@Test
	void skipsEmptyRequestsAndRejectsInvalidBatchesBeforeCallingTheClient() {
		var client = new RecordingClient(request -> {
			throw new AssertionError("No request expected");
		});
		var manager = new DynamoDbClientManager(List.of("history"), client);

		assertTrue(manager.getItems(keys(), 4).join().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> manager.getItems(keys("org_1"), -1));
		var tooMany = IntStream.range(0, 101).mapToObj(Integer::toString).toArray(String[]::new);
		assertThrows(IllegalArgumentException.class, () -> manager.getItems(keys(tooMany), 4));
		var acrossTables = new DynamoDbClientManager(List.of("history", "archive"), client);
		assertThrows(IllegalArgumentException.class, () -> acrossTables.getItems(keys(Arrays.copyOf(tooMany, 51)), 4));
		assertTrue(client.requests.isEmpty());
	}

	@Test
	void accepts100KeysAcrossTables() {
		var client = new RecordingClient(request -> CompletableFuture.completedFuture(response(Map.of())));
		var entries = IntStream.range(0, 50).mapToObj(Integer::toString).toArray(String[]::new);

		new DynamoDbClientManager(List.of("history", "archive"), client).getItems(keys(entries), 4).join();

		assertEquals(100, client.requests.getFirst().requestItems().values().stream().mapToInt(attributes -> attributes.keys().size()).sum());
	}

	@Test
	void entityReadsStillUseTheSharedRetryPath() {
		var client = new RecordingClient(
			request -> CompletableFuture
				.completedFuture(
					BatchGetItemResponse.builder().unprocessedKeys(request.requestItems()).build()
				)
		);
		var manager = new DynamoDbClientManager(List.of("history"), client);

		assertThrows(CompletionException.class, () -> manager.getItems(keys("org_1").keys(), Flattener.create(List.of("history"), false), 1).join());
		assertEquals(2, client.requests.size());
	}

	private static KeysAndAttributes keys(String... keys) {
		return KeysAndAttributes
			.builder()
			.keys(Arrays.stream(keys).map(key -> Map.of("historyKey", fromS(key))).toList())
			.projectionExpression("historyKey,builds")
			.consistentRead(true)
			.build();
	}

	private static BatchGetItemResponse response(Map<String, List<Map<String, AttributeValue>>> items) {
		return BatchGetItemResponse.builder().responses(items).build();
	}

	private static class RecordingClient implements DynamoDbAsyncClient {
		private final List<BatchGetItemRequest> requests = new ArrayList<>();
		private final Function<BatchGetItemRequest, CompletableFuture<BatchGetItemResponse>> response;

		private RecordingClient(Function<BatchGetItemRequest, CompletableFuture<BatchGetItemResponse>> response) {
			this.response = response;
		}

		@Override
		public CompletableFuture<BatchGetItemResponse> batchGetItem(BatchGetItemRequest request) {
			requests.add(request);
			return response.apply(request);
		}

		@Override
		public String serviceName() {
			return "dynamodb";
		}

		@Override
		public void close() {}
	}
}
