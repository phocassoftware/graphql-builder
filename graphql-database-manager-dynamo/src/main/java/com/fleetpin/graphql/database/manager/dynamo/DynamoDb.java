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

package com.fleetpin.graphql.database.manager.dynamo;

import static com.fleetpin.graphql.database.manager.util.TableCoreUtil.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetpin.graphql.database.manager.DatabaseDriver;
import com.fleetpin.graphql.database.manager.DatabaseKey;
import com.fleetpin.graphql.database.manager.DatabaseQueryHistoryKey;
import com.fleetpin.graphql.database.manager.DatabaseQueryKey;
import com.fleetpin.graphql.database.manager.KeyFactory;
import com.fleetpin.graphql.database.manager.PutValue;
import com.fleetpin.graphql.database.manager.Query;
import com.fleetpin.graphql.database.manager.QueryBuilder;
import com.fleetpin.graphql.database.manager.RevisionMismatchException;
import com.fleetpin.graphql.database.manager.ScanResult;
import com.fleetpin.graphql.database.manager.ScanResult.Item;
import com.fleetpin.graphql.database.manager.Table;
import com.fleetpin.graphql.database.manager.TableDataLoader;
import com.fleetpin.graphql.database.manager.TableScanQuery;
import com.fleetpin.graphql.database.manager.annotations.Hash;
import com.fleetpin.graphql.database.manager.annotations.Hash.HashExtractor;
import com.fleetpin.graphql.database.manager.annotations.HashLocator;
import com.fleetpin.graphql.database.manager.annotations.HashLocator.HashQueryBuilder;
import com.fleetpin.graphql.database.manager.util.BackupItem;
import com.fleetpin.graphql.database.manager.util.CompletableFutureUtil;
import com.fleetpin.graphql.database.manager.util.HistoryBackupItem;
import com.fleetpin.graphql.database.manager.util.HistoryCoreUtil;
import com.fleetpin.graphql.database.manager.util.TableCoreUtil;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import graphql.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.reflections.Reflections;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest.Builder;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

public class DynamoDb extends DatabaseDriver {

	private static final AttributeValue REVISION_INCREMENT = AttributeValue.builder().n("1").build();
	private static final int BATCH_WRITE_SIZE = 25;
	private static final int MAX_RETRY = 20;

	private final List<String> entityTables; // is in reverse order so easy to override as we go through
	private final String historyTable;
	private final String entityTable;
	private final DynamoDbAsyncClient client;
	private final ObjectMapper mapper;
	private final Supplier<String> idGenerator;
	private final int batchWriteSize;
	private final int maxRetry;
	private final boolean globalEnabled;
	private final boolean hash;
	private final String classPath;

	private final String parallelHashIndex;

	private final ConcurrentHashMap<Class<? extends Table>, Optional<Hash.HashExtractor>> extractorCache = new ConcurrentHashMap<>();

	private enum BackupTableType {
		Entity,
		History,
	}

	private final Map<String, HashQueryBuilder> hashKeyExpander;
	private final Map<String, Class<? extends Table>> classes;

	public DynamoDb(ObjectMapper mapper, List<String> entityTables, List<String> historyTables, DynamoDbAsyncClient client, Supplier<String> idGenerator) {
		this(mapper, entityTables, null, client, idGenerator, BATCH_WRITE_SIZE, MAX_RETRY, true, true, null, null);
	}

	public DynamoDb(
		ObjectMapper mapper,
		List<String> entityTables,
		List<String> historyTables,
		DynamoDbAsyncClient client,
		Supplier<String> idGenerator,
		String parallelHashIndex
	) {
		this(mapper, entityTables, null, client, idGenerator, BATCH_WRITE_SIZE, MAX_RETRY, true, true, null, parallelHashIndex);
	}

	public DynamoDb(
		ObjectMapper mapper,
		List<String> entityTables,
		String historyTable,
		DynamoDbAsyncClient client,
		Supplier<String> idGenerator,
		int batchWriteSize,
		int maxRetry,
		boolean globalEnabled,
		boolean hash,
		String classPath,
		String parallelHashIndex
	) {
		this.mapper = mapper;
		this.entityTables = entityTables;
		this.historyTable = historyTable;
		this.entityTable = entityTables.get(entityTables.size() - 1);
		this.client = client;
		this.idGenerator = idGenerator;
		this.batchWriteSize = batchWriteSize;
		this.maxRetry = maxRetry;
		this.globalEnabled = globalEnabled;
		this.hash = hash;
		this.classPath = classPath;
		this.parallelHashIndex = parallelHashIndex;

		if (classPath != null) {
			var tableObjects = new Reflections(classPath).getSubTypesOf(Table.class);

			this.hashKeyExpander = new HashMap<>();
			this.classes = new HashMap<>();

			for (var obj : tableObjects) {
				classes.put(TableCoreUtil.table(obj), TableCoreUtil.baseClass(obj));
				HashLocator hashLocator = null;
				Class<?> tmp = obj;
				while (hashLocator == null && tmp != null) {
					hashLocator = tmp.getDeclaredAnnotation(HashLocator.class);
					tmp = tmp.getSuperclass();
				}
				if (hashLocator != null) {
					try {
						HashQueryBuilder locator = hashLocator.value().getConstructor().newInstance();
						var old = hashKeyExpander.put(TableCoreUtil.table(obj), locator);
						if (old != null && !old.getClass().equals(obj.getClass())) {
							throw new RuntimeException("Duplicate table name " + obj + " " + old);
						}
					} catch (ReflectiveOperationException e) {
						throw new RuntimeException(e);
					}
				}
			}
		} else {
			hashKeyExpander = null;
			classes = null;
		}
	}

	public <T extends Table> CompletableFuture<List<T>> delete(String organisationId, Class<T> clazz) {
		if (getExtractor(clazz).isPresent()) {
			throw new UnsupportedOperationException("hashed types can not be deleted by type");
		}
		var ofTypeKey = KeyFactory.createDatabaseQueryKey(organisationId, QueryBuilder.create(clazz).build());
		var futureItems = query(ofTypeKey);
		return futureItems.thenCompose(items -> CompletableFutureUtil.sequence(items.stream().map(x -> delete(organisationId, x))));
	}

	public <T extends Table> CompletableFuture<T> delete(String organisationId, T entity) {
		String sourceOrganisation = getSourceOrganisationId(entity);

		if (!sourceOrganisation.equals(organisationId)) {
			// trying to delete a global or something just return without doing anything
			return CompletableFuture.completedFuture(entity);
		}

		String sourceTable = getSourceTable(entity);
		if (sourceTable.equals(entityTable)) {
			Map<String, AttributeValue> key = mapWithKeys(organisationId, entity);

			return client
				.deleteItem(request ->
					request
						.tableName(entityTable)
						.key(key)
						.applyMutation(mutator -> {
							String sourceOrganisationId = getSourceOrganisationId(entity);
							if (!sourceOrganisationId.equals(organisationId)) {
								return;
							}
							if (entity.getRevision() == 0) { // we confirm row does not exist with a revision since entry might predate feature
								mutator.conditionExpression("attribute_not_exists(revision)");
							} else {
								Map<String, AttributeValue> variables = new HashMap<>();
								variables.put(":revision", AttributeValue.builder().n(Long.toString(entity.getRevision())).build());
								// check exists and matches revision
								mutator.expressionAttributeValues(variables);
								mutator.conditionExpression("revision = :revision");
							}
						})
				)
				.thenApply(response -> {
					return entity;
				})
				.exceptionally(failure -> {
					if (failure.getCause() instanceof ConditionalCheckFailedException) {
						throw new RevisionMismatchException(failure.getCause());
					}
					Throwables.throwIfUnchecked(failure);
					throw new RuntimeException(failure);
				});
		} else {
			// we mark as deleted not actual delete
			Map<String, AttributeValue> item = mapWithKeys(organisationId, entity, true);
			item.put("deleted", AttributeValue.builder().bool(true).build());

			return client
				.putItem(request -> request.tableName(entityTable).item(item))
				.thenApply(response -> {
					return entity;
				});
		}
	}

	private CompletableFuture<?> conditionalBulkWrite(List<PutValue> items) {
		var all = items
			.stream()
			.map(i ->
				put(i.getOrganisationId(), i.getEntity(), i.getCheck(), true)
					.whenComplete((res, e) -> {
						if (e == null) {
							i.resolve();
						} else {
							i.fail(e);
						}
					})
			)
			.toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(all);
	}

	private CompletableFuture<?> nonConditionalBulkPutChunk(List<PutValue> items) {
		var writeRequests = items.stream().map(i -> buildWriteRequest(i)).collect(Collectors.toList());
		var data = Map.of(entityTable, writeRequests);
		return putItems(0, data)
			.handle((response, error) -> {
				if (error == null) {
					items.forEach(i -> {
						i.resolve();
					});
				} else {
					items.forEach(i -> i.fail(error));
				}
				return CompletableFuture.completedFuture(null);
			})
			.thenCompose(r -> r)
			.exceptionally(ex -> {
				items.forEach(i -> i.fail(ex));
				return null;
			});
	}

	private CompletableFuture<?> nonConditionalBulkWrite(List<PutValue> items) {
		if (items.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}
		if (items.size() > batchWriteSize) {
			ArrayListMultimap<String, PutValue> byPartition = ArrayListMultimap.create();
			items
				.stream()
				.forEach(t -> {
					var key = mapWithKeys(t.getOrganisationId(), t.getEntity());
					byPartition.put(key.get("organisationId").s(), t);
				});

			var futures = byPartition
				.keySet()
				.stream()
				.map(key -> {
					var partition = byPartition.get(key);

					var toReturn = new CompletableFuture();
					sendBackToBack(toReturn, Lists.partition(partition, batchWriteSize).iterator());
					return toReturn;
				})
				.toArray(CompletableFuture[]::new);

			return CompletableFuture.allOf(futures);
		} else {
			return nonConditionalBulkPutChunk(items);
		}
	}

	private void sendBackToBack(CompletableFuture<?> atEnd, Iterator<List<PutValue>> it) {
		nonConditionalBulkPutChunk(it.next())
			.whenComplete((response, error) -> {
				if (error != null) {
					while (it.hasNext()) {
						var f = it.next();
						for (var e : f) {
							e.fail(error);
						}
					}
					atEnd.completeExceptionally(error);
				} else {
					if (it.hasNext()) {
						sendBackToBack(atEnd, it);
					} else {
						atEnd.complete(null);
					}
				}
			});
	}

	private CompletableFuture<?> putItems(int count, Map<String, List<WriteRequest>> data) {
		if (count > maxRetry) {
			throw new RuntimeException("Failed to put items into dynamo after " + maxRetry + " attempts");
		}
		var delay = CompletableFuture.delayedExecutor(100 * count * count, TimeUnit.MILLISECONDS);
		return CompletableFuture
			.supplyAsync(
				() -> {
					return client
						.batchWriteItem(builder -> builder.requestItems(data))
						.thenCompose(response -> {
							if (!response.unprocessedItems().isEmpty()) {
								return putItems(count + 1, response.unprocessedItems());
							} else {
								return CompletableFuture.completedFuture(null);
							}
						});
				},
				delay
			)
			.thenCompose(t -> t);
	}

	@Override
	public CompletableFuture<Void> bulkPut(List<PutValue> values) {
		try {
			var conditional = Lists.partition(values.stream().filter(v -> v.getCheck()).collect(Collectors.toList()), batchWriteSize);
			var nonConditional = values.stream().filter(v -> !v.getCheck()).collect(Collectors.toList());

			var conditionalFuture = CompletableFuture.allOf(conditional.stream().map(part -> conditionalBulkWrite(part)).toArray(CompletableFuture[]::new));

			var nonConditionalFuture = nonConditionalBulkWrite(nonConditional);

			return CompletableFuture.allOf(conditionalFuture, nonConditionalFuture);
		} catch (Exception e) {
			for (var v : values) {
				v.fail(e);
			}
			return CompletableFuture.completedFuture(null);
		}
	}

	public <T extends Table> Map<String, AttributeValue> buildPutEntity(String organisationId, T entity, boolean update) {
		long revision = entity.getRevision();
		if (update) {
			if (entity.getId() == null) {
				entity.setId(idGenerator.get());
				setCreatedAt(entity, Instant.now());
			}
			if (entity.getCreatedAt() == null) {
				setCreatedAt(entity, Instant.now()); // if missing for what ever reason
			}
			setUpdatedAt(entity, Instant.now());
			revision++;
		}
		Map<String, AttributeValue> item = mapWithKeys(organisationId, entity, true);

		var entries = TableUtil.toAttributes(mapper, entity);
		entries.remove("revision"); // needs to be at the top level as a limit on dynamo to be able to perform an atomic addition
		item.put("revision", AttributeValue.builder().n(Long.toString(revision)).build());
		if (HistoryCoreUtil.hasHistory(entity)) {
			item.put("history", AttributeValue.builder().bool(true).build());
		}
		item.put("item", AttributeValue.builder().m(entries).build());

		Map<String, AttributeValue> links = new HashMap<>();
		getLinks(entity)
			.forEach((table, link) -> {
				if (!link.isEmpty()) {
					links.put(table, AttributeValue.builder().ss(link).build());
				}
			});

		item.put("links", AttributeValue.builder().m(links).build());
		setSource(entity, entityTable, getLinks(entity), organisationId);

		String secondaryOrganisation = TableUtil.getSecondaryOrganisation(entity);
		String secondaryGlobal = TableUtil.getSecondaryGlobal(entity);

		if (secondaryGlobal != null) {
			var index = AttributeValue.builder().s(table(entity.getClass()) + ":" + secondaryGlobal).build();
			item.put("secondaryGlobal", index);
		}
		if (secondaryOrganisation != null) {
			var index = AttributeValue.builder().s(table(entity.getClass()) + ":" + secondaryOrganisation).build();
			item.put("secondaryOrganisation", index);
		}
		return item;
	}

	public WriteRequest buildWriteRequest(PutValue value) {
		var item = buildPutEntity(value.getOrganisationId(), value.getEntity(), true);

		return WriteRequest.builder().putRequest(builder -> builder.item(item)).build();
	}

	private <T extends Table> CompletableFuture<T> put(String organisationId, T entity, boolean check, boolean updateEntity) {
		final long revision = entity.getRevision();
		String sourceTable = getSourceTable(entity);
		var item = buildPutEntity(organisationId, entity, updateEntity);

		return client
			.putItem(request ->
				request
					.tableName(entityTable)
					.item(item)
					.applyMutation(mutator -> {
						if (check) {
							String sourceOrganisationId = getSourceOrganisationId(entity);

							if (sourceTable != null && !sourceTable.equals(entityTable) || !sourceOrganisationId.equals(organisationId) || revision == 0) { // we confirm row does not exist with a
								// revision since entry might predate
								// feature
								mutator.conditionExpression("attribute_not_exists(revision)");
							} else {
								Map<String, AttributeValue> variables = new HashMap<>();
								variables.put(":revision", AttributeValue.builder().n(Long.toString(revision)).build());
								// check exists and matches revision
								mutator.expressionAttributeValues(variables);
								mutator.conditionExpression("revision = :revision");
							}
						}
					})
			)
			.exceptionally(failure -> {
				if (failure.getCause() instanceof ConditionalCheckFailedException) {
					throw new RevisionMismatchException(failure.getCause());
				}
				Throwables.throwIfUnchecked(failure);
				throw new RuntimeException(failure);
			})
			.thenApply(response -> entity);
	}

	@Override
	public int maxBatchSize() {
		int size = 100 / entityTables.size();
		if (globalEnabled) {
			size = size / 2;
		}
		return size;
	}

	@Override
	public <T extends Table> CompletableFuture<List<T>> get(List<DatabaseKey<T>> keys) {
		List<Map<String, AttributeValue>> entries = new ArrayList<>(keys.size() * 2);

		keys.forEach(key -> {
			if (key.getOrganisationId() != null) {
				var organisation = mapWithKeys(key.getOrganisationId(), key.getType(), key.getId());
				entries.add(organisation);
			}
			if (globalEnabled) {
				var global = mapWithKeys("global", key.getType(), key.getId());
				entries.add(global);
			}
		});

		Map<String, KeysAndAttributes> items = new HashMap<>();

		for (String table : this.entityTables) {
			items.put(table, KeysAndAttributes.builder().keys(entries).consistentRead(true).build());
		}
		return getItems(0, items, Flattener.create(this.entityTables, false))
			.thenApply(flattener -> {
				var toReturn = new ArrayList<T>();
				for (var key : keys) {
					var item = flattener.get(getExtractor(key.getType()), key.getType(), key.getId());
					if (item == null) {
						toReturn.add(null);
					} else {
						toReturn.add(item.convertTo(mapper, key.getType()));
					}
				}
				return toReturn;
			});
	}

	private CompletableFuture<Flattener> getItems(int count, Map<String, KeysAndAttributes> items, Flattener flattener) {
		if (count > maxRetry) {
			throw new RuntimeException("Failed to get keys from dynamo after " + maxRetry + " attempts");
		}
		var delay = CompletableFuture.delayedExecutor(100 * count * count, TimeUnit.MILLISECONDS);
		return CompletableFuture
			.supplyAsync(
				() -> {
					return client
						.batchGetItem(builder -> builder.requestItems(items))
						.thenCompose(response -> {
							var responseItems = response.responses();
							entityTables.forEach(table -> {
								flattener.add(table, responseItems.get(table));
							});

							if (!response.unprocessedKeys().isEmpty()) {
								return getItems(count, response.unprocessedKeys(), flattener);
							} else {
								return CompletableFuture.completedFuture(flattener);
							}
						});
				},
				delay
			)
			.thenCompose(t -> t);
	}

	@Override
	public <T extends Table> CompletableFuture<List<T>> getViaLinks(
		String organisationId,
		Table entry,
		Class<T> type,
		TableDataLoader<DatabaseKey<Table>> items
	) {
		if (getExtractor(entry.getClass()).isPresent() || getExtractor(type).isPresent()) {
			throw new UnsupportedOperationException("hashed objects can not be linked");
		}

		String tableTarget = table(type);
		var links = getLinks(entry).get(tableTarget);
		if (links == null) {
			links = Collections.emptySet();
		}
		Class<Table> query = (Class<Table>) type;
		List<DatabaseKey<Table>> keys = links.stream().map(link -> createDatabaseKey(organisationId, query, link)).collect(Collectors.toList());
		return items.loadMany(keys);
	}

	@Override
	public <T extends Table> CompletableFuture<List<T>> query(DatabaseQueryKey<T> key) {
		var futures = entityTables
			.stream()
			.flatMap(table -> {
				if (globalEnabled) {
					return Stream.of(Map.entry(table, "global"), Map.entry(table, key.getOrganisationId()));
				} else {
					return Stream.of(Map.entry(table, key.getOrganisationId()));
				}
			})
			.map(pair -> {
				return query(pair.getValue(), pair.getKey(), key.getQuery());
			});

		var future = CompletableFutureUtil.sequence(futures);

		return future.thenApply(results -> {
			var flattener = Flattener.create(this.entityTables, false);

			results.forEach(list -> flattener.addItems(list));
			return flattener.results(mapper, key.getQuery().getType(), Optional.ofNullable(key.getQuery().getLimit()));
		});
	}

	@Override
	public <T extends Table> CompletableFuture<List<T>> queryHistory(DatabaseQueryHistoryKey<T> key) {
		if (this.historyTable == null) {
			throw new RuntimeException("Cannot query history table, because it's null.");
		}
		var queryHistory = key.getQueryHistory();
		var organisationIdType = AttributeValue.builder().s(key.getOrganisationId() + ":" + table(queryHistory.getType())).build();

		var builder = QueryRequest.builder();
		builder.tableName(historyTable);

		if (queryHistory.getId() != null) {
			builder = queryHistoryWithId(key, builder, organisationIdType);
		} else {
			builder = queryHistoryWithStarts(key, builder, organisationIdType);
		}

		var targetException = new AtomicReference<Exception>();

		List<T> toReturn = new ArrayList<T>();
		return client
			.queryPaginator(builder.build())
			.subscribe(response -> {
				try {
					response.items().forEach(item -> toReturn.add(new DynamoItem(historyTable, item).convertTo(mapper, queryHistory.getType())));
				} catch (Exception e) {
					targetException.set(e);
				}
			})
			.thenApply(__ -> {
				if (targetException.get() != null) {
					throw new RuntimeException(targetException.get());
				}
				return toReturn;
			});
	}

	private <T extends Table> Builder queryHistoryWithId(DatabaseQueryHistoryKey<T> key, Builder builder, AttributeValue organisationIdType) {
		var queryHistory = key.getQueryHistory();

		var id = queryHistory.getId();
		if (queryHistory.getFromRevision() != null || queryHistory.getToRevision() != null) {
			var from = queryHistory.getFromRevision() != null ? queryHistory.getFromRevision() : 0L;
			var to = queryHistory.getToRevision() != null ? queryHistory.getToRevision() : Long.MAX_VALUE;
			var keyConditions = idWithFromTo(id, from, to, organisationIdType);
			builder
				.keyConditionExpression("organisationIdType = :organisationIdType AND idRevision BETWEEN :fromId  AND :toId")
				.expressionAttributeValues(keyConditions);
		} else if (queryHistory.getFromUpdatedAt() != null || queryHistory.getToUpdatedAt() != null) {
			var from = queryHistory.getFromUpdatedAt() != null ? queryHistory.getFromUpdatedAt().toEpochMilli() : 0L;
			var to = queryHistory.getToUpdatedAt() != null ? queryHistory.getToUpdatedAt().toEpochMilli() : Long.MAX_VALUE;
			var keyConditions = idWithFromTo(id, from, to, organisationIdType);
			builder
				.keyConditionExpression("organisationIdType = :organisationIdType AND idDate BETWEEN :fromId  AND :toId")
				.expressionAttributeValues(keyConditions)
				.indexName("idDate");
		} else {
			var idAttribute = HistoryUtil.toId(id);
			Map<String, AttributeValue> keyConditions = new HashMap<>();
			keyConditions.put(":id", idAttribute);
			keyConditions.put(":organisationIdType", organisationIdType);

			builder
				.keyConditionExpression("organisationIdType = :organisationIdType AND begins_with (idRevision, :id)")
				.expressionAttributeValues(keyConditions);
		}
		return builder;
	}

	private <T extends Table> Builder queryHistoryWithStarts(DatabaseQueryHistoryKey<T> key, Builder builder, AttributeValue organisationIdType) {
		var queryHistory = key.getQueryHistory();

		var starts = queryHistory.getStartsWith();
		var idStarts = AttributeValue.builder().s(table(queryHistory.getType()) + ":" + starts).build();
		if (queryHistory.getFromUpdatedAt() != null && queryHistory.getToUpdatedAt() != null) {
			var from = queryHistory.getFromUpdatedAt().toEpochMilli();
			var to = queryHistory.getToUpdatedAt().toEpochMilli();
			var keyConditions = startsWithFromTo(starts, from, to, organisationIdType, "between");
			keyConditions.put(":idStarts", idStarts);

			builder
				.keyConditionExpression("organisationIdType = :organisationIdType AND startsWithUpdatedAt BETWEEN :fromId  AND :toId")
				.expressionAttributeValues(keyConditions)
				.indexName("startsWithUpdatedAt")
				.filterExpression("updatedAt BETWEEN :fromUpdatedAt AND :toUpdatedAt AND begins_with (id, :idStarts)");
		} else if (queryHistory.getFromUpdatedAt() != null) {
			var from = queryHistory.getFromUpdatedAt().toEpochMilli();
			var to = Long.MAX_VALUE;
			var keyConditions = startsWithFromTo(starts, from, to, organisationIdType, "from");
			keyConditions.put(":idStarts", idStarts);

			builder
				.keyConditionExpression("organisationIdType = :organisationIdType AND startsWithUpdatedAt BETWEEN :fromId  AND :toId")
				.expressionAttributeValues(keyConditions)
				.indexName("startsWithUpdatedAt")
				.filterExpression("updatedAt >= :fromUpdatedAt AND begins_with (id, :idStarts)");
		} else if (queryHistory.getToUpdatedAt() != null) {
			var from = 0L;
			var to = queryHistory.getToUpdatedAt().toEpochMilli();
			var keyConditions = startsWithFromTo(starts, from, to, organisationIdType, "to");
			keyConditions.put(":idStarts", idStarts);

			builder
				.keyConditionExpression("organisationIdType = :organisationIdType AND startsWithUpdatedAt BETWEEN :fromId  AND :toId")
				.expressionAttributeValues(keyConditions)
				.indexName("startsWithUpdatedAt")
				.filterExpression("updatedAt <= :toUpdatedAt AND begins_with (id, :idStarts)");
		}
		return builder;
	}

	private Map<String, AttributeValue> idWithFromTo(String id, Long from, Long to, AttributeValue organisationIdType) {
		var fromIdAttribute = HistoryUtil.toRevisionId(id, from);
		var toIdAttribute = HistoryUtil.toRevisionId(id, to);
		Map<String, AttributeValue> keyConditions = new HashMap<>();
		keyConditions.put(":fromId", fromIdAttribute);
		keyConditions.put(":toId", toIdAttribute);
		keyConditions.put(":organisationIdType", organisationIdType);
		return keyConditions;
	}

	private Map<String, AttributeValue> startsWithFromTo(String starts, Long from, Long to, AttributeValue organisationIdType, String type) {
		var fromIdAttribute = HistoryUtil.toUpdatedAtId(starts, from, true);
		var toIdAttribute = HistoryUtil.toUpdatedAtId(starts, to, false);
		Map<String, AttributeValue> keyConditions = new HashMap<>();
		keyConditions.put(":fromId", fromIdAttribute);
		keyConditions.put(":toId", toIdAttribute);
		keyConditions.put(":organisationIdType", organisationIdType);

		var fromUpdatedAt = AttributeValue.builder().n(Long.toString(from)).build();
		var toUpdatedAt = AttributeValue.builder().n(Long.toString(to)).build();

		if (type == "between") {
			keyConditions.put(":fromUpdatedAt", fromUpdatedAt);
			keyConditions.put(":toUpdatedAt", toUpdatedAt);
		} else if (type == "from") {
			keyConditions.put(":fromUpdatedAt", fromUpdatedAt);
		} else if (type == "to") {
			keyConditions.put(":toUpdatedAt", toUpdatedAt);
		}

		return keyConditions;
	}

	@Override
	public <T extends Table> CompletableFuture<List<T>> queryGlobal(Class<T> type, String value) {
		var id = AttributeValue.builder().s(table(type) + ":" + value).build();

		CompletableFuture<List<List<DynamoItem>>> future = CompletableFuture.completedFuture(new ArrayList<>());
		for (var table : entityTables) {
			future =
				future.thenCombine(
					queryGlobal(table, id),
					(a, b) -> {
						a.add(b);
						return a;
					}
				);
		}
		return future.thenApply(results -> {
			var flattener = Flattener.create(this.entityTables, true);
			results.forEach(list -> flattener.addItems(list));
			return flattener.results(mapper, type);
		});
	}

	private CompletableFuture<List<DynamoItem>> queryGlobal(String table, AttributeValue id) {
		Map<String, AttributeValue> keyConditions = new HashMap<>();
		keyConditions.put(":secondaryGlobal", id);

		var toReturn = new ArrayList<DynamoItem>();
		return client
			.queryPaginator(r ->
				r
					.tableName(table)
					.indexName("secondaryGlobal")
					.keyConditionExpression("secondaryGlobal = :secondaryGlobal")
					.expressionAttributeValues(keyConditions)
			)
			.subscribe(response -> {
				response.items().forEach(item -> toReturn.add(new DynamoItem(table, item)));
			})
			.thenApply(__ -> {
				return toReturn;
			});
	}

	@Override
	public <T extends Table> CompletableFuture<List<T>> querySecondary(
		Class<T> type,
		String organisationId,
		String value,
		TableDataLoader<DatabaseKey<Table>> item
	) {
		if (getExtractor(type).isPresent()) {
			throw new UnsupportedOperationException("hashed objects do not support secondary queries");
		}

		var organisationIdAttribute = AttributeValue.builder().s(organisationId).build();
		var id = AttributeValue.builder().s(table(type) + ":" + value).build();

		CompletableFuture<Set<String>> future = CompletableFuture.completedFuture(new HashSet<>());
		for (var table : entityTables) {
			future =
				future.thenCombine(
					querySecondary(table, organisationIdAttribute, id),
					(a, b) -> {
						a.addAll(b);
						return a;
					}
				);
		}

		return future.thenCompose(results -> {
			List<DatabaseKey<Table>> keys = results
				.stream()
				.map(i -> (DatabaseKey<Table>) createDatabaseKey(organisationId, type, i))
				.collect(Collectors.toList());
			return item.loadMany(keys);
		});
	}

	private CompletableFuture<List<String>> querySecondary(String table, AttributeValue organisationId, AttributeValue id) {
		Map<String, AttributeValue> keyConditions = new HashMap<>();
		keyConditions.put(":organisationId", organisationId);
		keyConditions.put(":secondaryOrganisation", id);

		var toReturn = new ArrayList<String>();
		return client
			.queryPaginator(r ->
				r
					.tableName(table)
					.indexName("secondaryOrganisation")
					.keyConditionExpression("organisationId = :organisationId AND secondaryOrganisation = :secondaryOrganisation")
					.expressionAttributeValues(keyConditions)
					.projectionExpression("id")
			)
			.subscribe(response -> {
				response
					.items()
					.stream()
					.map(item -> item.get("id").s())
					.map(itemId -> {
						return itemId.substring(itemId.indexOf(':') + 1); // Id contains entity name
					})
					.forEach(toReturn::add);
			})
			.thenApply(__ -> {
				return toReturn;
			});
	}

	private CompletableFuture<List<DynamoItem>> query(String organisationId, String table, Query<?> query) {
		var keys = mapWithKeys(organisationId, query.getType(), query.getStartsWith());
		var organisationIdAttribute = keys.get("organisationId");
		var id = keys.get("id");
		Map<String, AttributeValue> keyConditions = new HashMap<>();
		keyConditions.put(":organisationId", organisationIdAttribute);

		String index = null;
		boolean consistentRead = true;
		boolean parallelRequest;

		if (query.getThreadIndex() != null && query.getThreadCount() != null) {
			consistentRead = false;
			parallelRequest = true;
			index = this.parallelHashIndex;
			keyConditions.put(":hash", AttributeValue.builder().s(toPaddedBinary(query.getThreadIndex(), query.getThreadCount())).build());
		} else {
			parallelRequest = false;
			if (id != null && !id.s().trim().isEmpty()) {
				index = null;
				keyConditions.put(":table", id);
			}
		}

		var s = new DynamoQuerySubscriber(table, query.getLimit());
		Boolean finalConsistentRead = consistentRead;
		String finalIndex = index;
		client
			.queryPaginator(r -> {
				r
					.tableName(table)
					.indexName(finalIndex)
					.consistentRead(finalConsistentRead)
					.expressionAttributeValues(keyConditions)
					.applyMutation(b -> {
						var conditionalExpression = "organisationId = :organisationId";

						if (keyConditions.containsKey(":table")) {
							conditionalExpression += " AND begins_with(id, :table)";
						} else if (keyConditions.containsKey(":hash")) {
							conditionalExpression += " AND begins_with(parallelHash, :hash)";
						}

						b.keyConditionExpression(conditionalExpression);

						if (query.getLimit() != null) {
							b.limit(query.getLimit());
						}

						if (query.getAfter() != null) {
							var start = mapWithKeys(organisationId, query.getType(), query.getAfter());
							if (parallelRequest) {
								start.put("parallelHash", AttributeValue.builder().s(parallelHash(query.getAfter())).build());
							}
							b.exclusiveStartKey(start);
						}
					});
			})
			.subscribe(s);

		return s.getFuture();
	}

	@Override
	public CompletableFuture<Void> restoreBackup(List<BackupItem> entities) {
		return restoreBackup(entities, BackupTableType.Entity, TableUtil::toAttributes);
	}

	@Override
	public CompletableFuture<Void> restoreHistoryBackup(List<HistoryBackupItem> entities) {
		return restoreBackup(entities, BackupTableType.History, HistoryUtil::toAttributes);
	}

	private <T extends BackupItem> CompletableFuture<Void> restoreBackup(
		List<T> entities,
		BackupTableType backupTableType,
		BiFunction<ObjectMapper, T, Map<String, AttributeValue>> toAttributes
	) {
		List<CompletableFuture<BatchWriteItemResponse>> completableFutures = Lists
			.partition(
				entities
					.stream()
					.map(item -> {
						return WriteRequest.builder().putRequest(builder -> builder.item(toAttributes.apply(mapper, item)).build()).build();
					})
					.collect(Collectors.toList()),
				BATCH_WRITE_SIZE
			)
			.stream()
			.map(putRequestBatch -> {
				final var batchPutRequest = BatchWriteItemRequest
					.builder()
					.requestItems(Map.of(backupTableType == BackupTableType.History ? historyTable : entityTable, putRequestBatch))
					.build();

				return client
					.batchWriteItem(batchPutRequest)
					.thenCompose(this::batchWriteRetry)
					.exceptionally(failure -> {
						if (failure.getCause() instanceof ConditionalCheckFailedException) {
							throw new RevisionMismatchException(failure.getCause());
						}
						Throwables.throwIfUnchecked(failure);
						throw new RuntimeException(failure);
					});
			})
			.collect(Collectors.toList());

		return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[completableFutures.size()]));
	}

	private CompletableFuture<BatchWriteItemResponse> batchWriteRetry(BatchWriteItemResponse items) {
		if (items.unprocessedItems().size() > 0) {
			return client.batchWriteItem(BatchWriteItemRequest.builder().requestItems(items.unprocessedItems()).build()).thenCompose(this::batchWriteRetry);
		} else {
			return CompletableFuture.completedFuture(items);
		}
	}

	@Override
	public CompletableFuture<List<BackupItem>> takeBackup(String organisationId) {
		CompletableFuture<List<List<BackupItem>>> future = CompletableFuture.completedFuture(new ArrayList<>());
		AttributeValue orgId = AttributeValue.builder().s(organisationId).build();
		for (var table : entityTables) {
			future =
				future.thenCombine(
					takeBackup(table, orgId),
					(a, b) -> {
						a.add(b);
						return a;
					}
				);
		}

		return future.thenApply(results -> {
			return results.stream().flatMap(List::stream).collect(Collectors.toList());
		});
	}

	@Override
	public CompletableFuture<List<HistoryBackupItem>> takeHistoryBackup(String organisationId) {
		if (this.classPath == null) {
			throw new RuntimeException("classPath is required to obtain the tables' name to query the history table");
		}

		// Using reflections to loop through tables and get the tables' name
		Set<Class<? extends Table>> tableObjects = new Reflections(classPath).getSubTypesOf(Table.class);
		List<HistoryBackupItem> toReturn = Collections.synchronizedList(new ArrayList<HistoryBackupItem>());

		Set<String> orgIdTypes = tableObjects.stream().map(obj -> organisationId + ":" + TableCoreUtil.table(obj)).collect(Collectors.toSet());

		var queries = orgIdTypes
			.stream()
			.map(orgIdType -> {
				Map<String, AttributeValue> keyConditions = new HashMap<>();
				AttributeValue orgIdTypeAttr = AttributeValue.builder().s(orgIdType).build();
				keyConditions.put(":organisationIdType", orgIdTypeAttr);
				return client
					.queryPaginator(r ->
						r
							.tableName(historyTable)
							.consistentRead(true)
							.keyConditionExpression("organisationIdType = :organisationIdType")
							.expressionAttributeValues(keyConditions)
					)
					.subscribe(response -> {
						response
							.items()
							.forEach(item -> {
								toReturn.add(new DynamoHistoryBackupItem(historyTable, item, mapper));
							});
					});
			})
			.toArray(CompletableFuture[]::new);

		return CompletableFuture.allOf(queries).thenApply(__ -> toReturn);
	}

	private CompletableFuture<List<BackupItem>> takeBackup(String table, AttributeValue organisationId) {
		if (hash && hashKeyExpander == null) {
			throw new UnsupportedOperationException("To perform backups on hashed databases must specify hashLocators");
		}

		Map<String, AttributeValue> keyConditions = new HashMap<>();

		List<CompletableFuture<Void>> hashAdds = new ArrayList<>();

		keyConditions.put(":organisationId", organisationId);

		var toReturn = Collections.synchronizedList(new ArrayList<BackupItem>());
		var future = client
			.queryPaginator(r ->
				r.tableName(table).consistentRead(true).keyConditionExpression("organisationId = :organisationId").expressionAttributeValues(keyConditions)
			)
			.subscribe(response -> {
				response
					.items()
					.forEach(item -> {
						if (this.hash) {
							var id = item.get("id").s();
							var type = id.substring(0, id.indexOf(':'));
							var expander = this.hashKeyExpander.get(type);
							if (expander != null) {
								var extra = expander.extractHashQueries(id.substring(id.indexOf(':') + 1));
								for (var query : extra) {
									var typeName = TableCoreUtil.table(query.getType());

									var key = organisationId.s() + ":" + typeName + ":" + query.getHashId();
									var hashFuture = client
										.queryPaginator(builder ->
											builder
												.tableName(entityTable)
												.keyConditionExpression("organisationId = :organisationId")
												.expressionAttributeValues(Map.of(":organisationId", AttributeValue.builder().s(key).build()))
										)
										.subscribe(hashResponse -> {
											hashResponse.items().forEach(i -> toReturn.add(new DynamoBackupItem(table, i, mapper)));
										});
									hashAdds.add(hashFuture);
								}
							}
						}

						toReturn.add(new DynamoBackupItem(table, item, mapper));
					});
			});

		future = future.thenCompose(__ -> CompletableFuture.allOf(hashAdds.toArray(CompletableFuture[]::new)));

		return future.thenApply(__ -> toReturn);
	}

	private CompletableFuture<?> removeLinks(
		String organisationId,
		Class<? extends Table> fromTable,
		Set<String> fromIds,
		String targetTable,
		String targetId
	) {
		var targetIdAttribute = AttributeValue.builder().ss(targetId).build();
		var futures = fromIds
			.stream()
			.map(fromId -> {
				Map<String, AttributeValue> targetKey = mapWithKeys(organisationId, fromTable, fromId);

				Map<String, AttributeValue> v = new HashMap<>();
				v.put(":val", targetIdAttribute);
				v.put(":revisionIncrement", REVISION_INCREMENT);

				Map<String, String> k = new HashMap<>();
				k.put("#table", targetTable);

				return client.updateItem(request ->
					request
						.tableName(entityTable)
						.key(targetKey)
						.updateExpression("DELETE links.#table :val ADD revision :revisionIncrement")
						.expressionAttributeNames(k)
						.expressionAttributeValues(v)
				);
			})
			.toArray(CompletableFuture[]::new);

		return CompletableFuture.allOf(futures);
	}

	private CompletableFuture<?> addLinks(String organisationId, Class<? extends Table> fromTable, Set<String> fromIds, String targetTable, String targetId) {
		var targetIdAttribute = AttributeValue.builder().ss(targetId).build();

		var futures = fromIds
			.stream()
			.map(fromId -> {
				Map<String, AttributeValue> targetKey = mapWithKeys(organisationId, fromTable, fromId);

				Map<String, AttributeValue> v = new HashMap<>();
				v.put(":val", targetIdAttribute);
				v.put(":revisionIncrement", REVISION_INCREMENT);
				Map<String, String> k = new HashMap<>();
				k.put("#table", targetTable);

				return client
					.updateItem(request ->
						request
							.tableName(entityTable)
							.key(targetKey)
							.conditionExpression("attribute_exists(links)")
							.updateExpression("ADD links.#table :val, revision :revisionIncrement")
							.expressionAttributeNames(k)
							.expressionAttributeValues(v)
					)
					.handle((r, e) -> {
						if (e != null) {
							if (e.getCause() instanceof ConditionalCheckFailedException) {
								Map<String, AttributeValue> m = new HashMap<>();
								m.put(targetTable, targetIdAttribute);
								v.put(":val", AttributeValue.builder().m(m).build());
								v.put(":revisionIncrement", REVISION_INCREMENT);

								return client.updateItem(request ->
									request
										.tableName(entityTable)
										.key(targetKey)
										.conditionExpression("attribute_not_exists(links)")
										.updateExpression("SET links = :val ADD revision :revisionIncrement")
										.expressionAttributeValues(v)
								);
							} else {
								throw new RuntimeException(e);
							}
						}
						return CompletableFuture.completedFuture(r);
					})
					.thenCompose(a -> a)
					.handle((r, e) -> { // nasty if attribute now exists use first approach again...
						if (e != null) {
							if (e.getCause() instanceof ConditionalCheckFailedException) {
								return client.updateItem(request ->
									request
										.tableName(entityTable)
										.key(targetKey)
										.conditionExpression("attribute_exists(links)")
										.updateExpression("ADD links.#table :val, revision :revisionIncrement")
										.expressionAttributeNames(k)
										.expressionAttributeValues(v)
								);
							} else {
								throw new RuntimeException(e);
							}
						}
						return CompletableFuture.completedFuture(r);
					})
					.thenCompose(a -> a);
			})
			.toArray(CompletableFuture[]::new);

		return CompletableFuture.allOf(futures);
	}

	private <T extends Table> CompletableFuture<T> updateEntityLinks(
		String organisationId,
		T entity,
		Class<? extends Table> targetType,
		Collection<String> targetId
	) {
		Map<String, AttributeValue> key = mapWithKeys(organisationId, entity);

		String targetTable = table(targetType);

		Map<String, String> k = new HashMap<>();
		k.put("#table", targetTable);

		Map<String, AttributeValue> values = new HashMap<>();
		if (targetId.isEmpty()) {
			values.put(":val", AttributeValue.builder().nul(true).build());
		} else {
			values.put(":val", AttributeValue.builder().ss(targetId).build());
		}
		values.put(":revisionIncrement", REVISION_INCREMENT);

		String extraConditions;

		String sourceTable = getSourceTable(entity);
		String sourceOrganisationId = getSourceOrganisationId(entity);
		// revision checks don't really work when reading from one env and writing to another, or read from global write to organisation.
		// revision would only practically be empty if reading object before revision concept is present
		if (sourceTable.equals(entityTable) && sourceOrganisationId.equals(organisationId) && entity.getRevision() != 0) {
			values.put(":revision", AttributeValue.builder().n(Long.toString(entity.getRevision())).build());
			extraConditions = " AND revision = :revision";
		} else {
			extraConditions = "";
		}

		var destination = client
			.updateItem(request ->
				request
					.tableName(entityTable)
					.key(key)
					.conditionExpression("attribute_exists(links)" + extraConditions)
					.updateExpression("SET links.#table = :val ADD revision :revisionIncrement")
					.expressionAttributeNames(k)
					.expressionAttributeValues(values)
					.returnValues(ReturnValue.UPDATED_NEW)
			)
			.handle((r, e) -> {
				if (e != null) {
					if (e.getCause() instanceof ConditionalCheckFailedException) {
						Map<String, AttributeValue> m = new HashMap<>();
						m.put(targetTable, values.get(":val"));
						values.put(":val", AttributeValue.builder().m(m).build());

						return client.updateItem(request ->
							request
								.tableName(entityTable)
								.key(key)
								.conditionExpression("attribute_not_exists(links)" + extraConditions)
								.updateExpression("SET links = :val ADD revision :revisionIncrement")
								.expressionAttributeValues(values)
								.returnValues(ReturnValue.UPDATED_NEW)
						);
					} else {
						throw new RuntimeException(e);
					}
				}
				return CompletableFuture.completedFuture(r);
			})
			.thenCompose(a -> a)
			.handle((r, e) -> {
				if (e != null) {
					if (e.getCause() instanceof ConditionalCheckFailedException) {
						return client.updateItem(request ->
							request
								.tableName(entityTable)
								.key(key)
								.conditionExpression("attribute_exists(links)" + extraConditions)
								.updateExpression("SET links.#table = :val ADD revision :revisionIncrement")
								.expressionAttributeNames(k)
								.expressionAttributeValues(values)
								.returnValues(ReturnValue.UPDATED_NEW)
						);
					} else {
						throw new RuntimeException(e);
					}
				}
				return CompletableFuture.completedFuture(r);
			})
			.thenCompose(a -> a);
		return destination
			.thenApply(response -> {
				entity.setRevision(Long.parseLong(response.attributes().get("revision").n()));
				return entity;
			})
			.exceptionally(failure -> {
				if (failure.getCause() instanceof ConditionalCheckFailedException) {
					throw new RevisionMismatchException(failure.getCause());
				}
				Throwables.throwIfUnchecked(failure);
				throw new RuntimeException(failure);
			});
	}

	@Override
	public <T extends Table> CompletableFuture<T> link(String organisationId, T entity, Class<? extends Table> class1, List<String> groupIds) {
		if (getExtractor(entity.getClass()).isPresent() || getExtractor(class1).isPresent()) {
			throw new UnsupportedOperationException("hashed objects can not be linked");
		}

		var source = table(entity.getClass());

		var target = table(class1);
		var existing = getLinks(entity).get(target);

		var toAdd = new HashSet<>(groupIds);
		if (existing == null) {
			existing = Collections.emptySet();
		}
		toAdd.removeAll(existing);

		var toRemove = new HashSet<>(existing);
		toRemove.removeAll(groupIds);

		var entityFuture = updateEntityLinks(organisationId, entity, class1, groupIds);

		return entityFuture.thenCompose(e -> {
			// wait until the entity has been updated in-case that fails then update the other targets.

			// remove links that have been removed
			CompletableFuture<?> removeFuture = removeLinks(organisationId, class1, toRemove, source, entity.getId());
			// add the new links
			CompletableFuture<?> addFuture = addLinks(organisationId, class1, toAdd, source, entity.getId());

			return CompletableFuture
				.allOf(removeFuture, addFuture)
				.thenApply(__ -> {
					setLinks(entity, target, groupIds);
					return e;
				});
		});
	}

	@Override
	public <T extends Table> CompletableFuture<T> unlink(
		final String organisationId,
		final T entity,
		final Class<? extends Table> clazz,
		final String targetId
	) {
		if (getExtractor(entity.getClass()).isPresent() || getExtractor(clazz).isPresent()) {
			throw new UnsupportedOperationException("hashed objects can not be linked");
		}

		final var updateEntityLinksRequest = createRemoveLinkRequest(organisationId, entity, clazz, targetId);

		return client
			.updateItem(updateEntityLinksRequest)
			.thenCompose(ignore -> get(List.of(createDatabaseKey(organisationId, clazz, targetId))))
			.thenCompose(targetEntities -> {
				if (targetEntities.isEmpty()) {
					throw new RuntimeException("Could not find link on the target: " + targetId);
				}

				final var updateTargetLinksRequest = createRemoveLinkRequest(organisationId, targetEntities.get(0), entity.getClass(), entity.getId());

				return client.updateItem(updateTargetLinksRequest);
			})
			.thenApply(ignore -> {
				var links = getLinks(entity).get(table(clazz));
				if (links != null) {
					links.remove(targetId);
				}

				return entity;
			});
	}

	private <T extends Table> UpdateItemRequest createRemoveLinkRequest(
		final String organisationId,
		final T entity,
		final Class<? extends Table> clazz,
		final String targetId
	) {
		final AttributeValue linkMap = AttributeValue
			.builder()
			.m(
				getLinks(entity)
					.entrySet()
					.stream()
					.filter(entry -> !entry.getValue().contains(targetId) && !entry.getKey().equals(table(clazz)))
					.collect(Collectors.toMap(Map.Entry::getKey, entry -> AttributeValue.builder().ss(entry.getValue()).build()))
			)
			.build();

		// It would be nice to pull the revision logic out
		final var revisionNumber = entity.getRevision() != 0 ? String.valueOf(entity.getRevision() + Integer.parseInt(REVISION_INCREMENT.n())) : "0";

		final var revision = AttributeValueUpdate.builder().action(AttributeAction.PUT).value(AttributeValue.builder().n(revisionNumber).build()).build();

		final var linksAttributeMap = Map.of("revision", revision, "links", AttributeValueUpdate.builder().action(AttributeAction.PUT).value(linkMap).build());

		final Map<String, AttributeValue> entityItem = mapWithKeys(organisationId, entity);

		return UpdateItemRequest.builder().tableName(entityTable).key(entityItem).attributeUpdates(linksAttributeMap).build();
	}

	public <T extends Table> CompletableFuture<T> deleteLinks(String organisationId, T entity) {
		if (getLinks(entity).isEmpty()) {
			return CompletableFuture.completedFuture(entity);
		}
		if (getExtractor(entity.getClass()).isPresent()) {
			throw new UnsupportedOperationException("hashed objects can not be linked");
		}

		var organisationIdAttribute = AttributeValue.builder().s(organisationId).build();
		var id = AttributeValue.builder().s(table(entity.getClass()) + ":" + entity.getId()).build();
		// we first clear out our own object

		long revision = entity.getRevision();
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":val", AttributeValue.builder().m(new HashMap<>()).build());
		values.put(":revisionIncrement", REVISION_INCREMENT);

		Map<String, AttributeValue> sourceKey = new HashMap<>();
		sourceKey.put("organisationId", organisationIdAttribute);
		sourceKey.put("id", id);

		var clearEntity = client
			.updateItem(request ->
				request
					.tableName(entityTable)
					.key(sourceKey)
					.updateExpression("SET links = :val ADD revision :revisionIncrement")
					.returnValues(ReturnValue.UPDATED_NEW)
					.applyMutation(mutator -> {
						String sourceTable = getSourceTable(entity);
						// revision checks don't really work when reading from one env and writing to another.
						if (sourceTable != null && !sourceTable.equals(entityTable)) {
							return;
						}
						String sourceOrganisationId = getSourceOrganisationId(entity);
						if (!sourceOrganisationId.equals(organisationId)) {
							return;
						}
						if (revision == 0) { // we confirm row does not exist with a revision since entry might predate feature
							mutator.conditionExpression("attribute_not_exists(revision)");
						} else {
							values.put(":revision", AttributeValue.builder().n(Long.toString(entity.getRevision())).build());
							mutator.conditionExpression("revision = :revision");
						}
					})
					.expressionAttributeValues(values)
			)
			.thenApply(response -> {
				entity.setRevision(Long.parseLong(response.attributes().get("revision").n()));
				return entity;
			})
			.exceptionally(failure -> {
				if (failure.getCause() instanceof ConditionalCheckFailedException) {
					throw new RevisionMismatchException(failure.getCause());
				}
				Throwables.throwIfUnchecked(failure);
				throw new RuntimeException(failure);
			});

		// after we successfully clear out our object we clear the remote references
		return clearEntity.thenCompose(r -> {
			var val = AttributeValue.builder().ss(entity.getId()).build();
			String source = table(entity.getClass());
			CompletableFuture<?> future = getLinks(entity)
				.entrySet()
				.stream()
				.flatMap(s -> s.getValue().stream().map(v -> Map.entry(s.getKey(), v)))
				.map(link -> {
					var targetIdAttribute = AttributeValue.builder().s(link.getKey() + ":" + link.getValue()).build();
					Map<String, AttributeValue> targetKey = new HashMap<>();
					targetKey.put("organisationId", organisationIdAttribute);
					targetKey.put("id", targetIdAttribute);

					Map<String, AttributeValue> v = new HashMap<>();
					v.put(":val", val);
					v.put(":revisionIncrement", REVISION_INCREMENT);

					Map<String, String> k = new HashMap<>();
					k.put("#table", source);

					return client.updateItem(request ->
						request
							.tableName(entityTable)
							.key(targetKey)
							.updateExpression("DELETE links.#table :val ADD revision :revisionIncrement")
							.expressionAttributeNames(k)
							.expressionAttributeValues(v)
					);
				})
				.reduce(CompletableFuture.completedFuture(null), (a, b) -> a.thenCombine(b, (c, d) -> d));
			getLinks(entity).clear();
			return future.thenApply(__ -> r);
		});
	}

	@Override
	public CompletableFuture<Boolean> destroyOrganisation(final String organisationId) {
		if (hash && hashKeyExpander == null) {
			throw new UnsupportedOperationException("To destoryOrganisations on hashed databases must specify hashLocators");
		}

		var keys = Collections.synchronizedList(new ArrayList<Map<String, AttributeValue>>());

		List<CompletableFuture<Void>> hashDeletes = new ArrayList<>();
		var future = client
			.queryPaginator(builder ->
				builder
					.tableName(entityTable)
					.projectionExpression("id, organisationId")
					.keyConditionExpression("organisationId = :organisationId")
					.expressionAttributeValues(Map.of(":organisationId", AttributeValue.builder().s(organisationId).build()))
			)
			.subscribe(response -> {
				if (this.hash) {
					for (var item : response.items()) {
						var id = item.get("id").s();
						var type = id.substring(0, id.indexOf(':'));
						var expander = this.hashKeyExpander.get(type);
						if (expander != null) {
							var extra = expander.extractHashQueries(id.substring(id.indexOf(':') + 1));
							for (var query : extra) {
								var typeName = TableCoreUtil.table(query.getType());

								var hashFuture = client
									.queryPaginator(builder ->
										builder
											.tableName(entityTable)
											.projectionExpression("id, organisationId")
											.keyConditionExpression("organisationId = :organisationId")
											.expressionAttributeValues(
												Map.of(
													":organisationId",
													AttributeValue.builder().s(organisationId + ":" + typeName + ":" + query.getHashId()).build()
												)
											)
									)
									.subscribe(hashResponse -> {
										keys.addAll(hashResponse.items());
									});
								hashDeletes.add(hashFuture);
							}
						}
					}
				}

				keys.addAll(response.items());
			});

		future = future.thenCompose(__ -> CompletableFuture.allOf(hashDeletes.toArray(CompletableFuture[]::new)));

		var delete = future.thenCompose(__ -> {
			if (keys.isEmpty()) {
				return CompletableFuture.completedFuture(null);
			}

			var all = Lists
				.partition(
					keys
						.stream()
						.map(item -> {
							final var deleteRequest = DeleteRequest.builder().key(item).build();
							return WriteRequest.builder().deleteRequest(deleteRequest).build();
						})
						.collect(Collectors.toList()),
					BATCH_WRITE_SIZE
				)
				.stream()
				.map(deleteRequestBatch -> {
					return putItems(0, Map.of(entityTable, deleteRequestBatch));
				})
				.toArray(CompletableFuture[]::new);
			return CompletableFuture.allOf(all);
		});

		return delete.thenApply(__ -> true);
	}

	@Override
	public String newId() {
		return idGenerator.get();
	}

	private <T extends Table> Map<String, AttributeValue> mapWithKeys(String organisationId, T entity) {
		return mapWithKeys(organisationId, entity.getClass(), entity.getId());
	}

	private <T extends Table> Map<String, AttributeValue> mapWithKeys(String organisationId, T entity, boolean addHash) {
		return mapWithKeys(organisationId, entity.getClass(), entity.getId(), addHash);
	}

	private <T extends Table> Map<String, AttributeValue> mapWithKeys(String organisationId, Class<T> type, String id) {
		return mapWithKeys(organisationId, type, id, false);
	}

	private <T extends Table> Optional<HashExtractor> getExtractor(Class<T> type) {
		if (!hash) {
			return Optional.empty();
		}
		return extractorCache.computeIfAbsent(
			type,
			t -> {
				Class<?> tmp = t;
				Hash hash = null;
				while (hash == null && tmp != null) {
					hash = tmp.getDeclaredAnnotation(Hash.class);
					tmp = tmp.getSuperclass();
				}
				if (hash == null) {
					return Optional.empty();
				} else {
					try {
						return Optional.of(hash.value().getConstructor().newInstance());
					} catch (ReflectiveOperationException e) {
						throw new RuntimeException("Unable to build hash extractor", e);
					}
				}
			}
		);
	}

	private <T extends Table> Map<String, AttributeValue> mapWithKeys(String organisationId, Class<T> type, final String id, boolean addHash) {
		Map<String, AttributeValue> item = new HashMap<>();

		var hashExtractor = getExtractor(type);
		if (hashExtractor.isPresent()) {
			if (id == null) {
				throw new RuntimeException("No id or startswith specified to calculate hash");
			}
			var hashSuffix = hashExtractor.get().hashId(id);
			var sortId = hashExtractor.get().sortId(id);
			var organisationIdAttribute = AttributeValue.builder().s(organisationId + ":" + table(type) + ":" + hashSuffix).build();
			var idAttribute = AttributeValue.builder().s(sortId).build();
			item.put("organisationId", organisationIdAttribute);
			if (!id.isEmpty()) {
				item.put("id", idAttribute);
			}
			if (addHash) {
				item.put("hashed", AttributeValue.builder().bool(true).build());
				item.put("parallelHash", AttributeValue.builder().s(parallelHash(id)).build());
			}
			return item;
		}

		var organisationIdAttribute = AttributeValue.builder().s(organisationId).build();
		var tmpId = id;
		if (tmpId == null) {
			tmpId = "";
		}
		var idAttribute = AttributeValue.builder().s(table(type) + ":" + tmpId).build();
		item.put("organisationId", organisationIdAttribute);
		item.put("id", idAttribute);

		if (addHash && hash) {
			item.put("parallelHash", AttributeValue.builder().s(parallelHash(tmpId)).build());
		}
		return item;
	}

	public static String toPaddedBinary(int number, int powerOfTwo) {
		var paddingLength = (int) ((Math.log(powerOfTwo) / Math.log(2)));
		StringBuilder binaryString = new StringBuilder(Integer.toBinaryString(number));
		while (binaryString.length() < paddingLength) {
			binaryString.insert(0, "0");
		}
		return binaryString.toString();
	}

	@VisibleForTesting
	protected static String parallelHash(String id) {
		String empty = "00000000";
		var hash = Hashing.murmur3_32().hashString(id, StandardCharsets.UTF_8);
		var bits = hash.asInt() & 0xFF;
		var toReturn = Integer.toBinaryString(bits);
		return empty.substring(0, empty.length() - toReturn.length()) + toReturn;
	}

	@Override
	protected ScanResult startTableScan(TableScanQuery tableScanQuery, int segment, Object from) {
		return startTableScan(tableScanQuery, segment, from, entityTable);
	}

	private ScanResult startTableScan(TableScanQuery tableScanQuery, int segment, Object from, String table) {
		var builder = ScanRequest.builder().tableName(table).totalSegments(tableScanQuery.parallelism()).segment(segment);

		if (from != null) {
			var startKey = TableUtil.toAttributes(mapper, from);
			builder.exclusiveStartKey(startKey);
		}

		var scan = client.scan(b -> b.tableName(entityTables.getLast()).totalSegments(tableScanQuery.parallelism()).segment(segment)).join();

		var items = new ArrayList<ScanResult.Item<?>>();
		for (var item : scan.items()) {
			if (!item.containsKey("id") || !item.containsKey("organisationId") || !item.containsKey("item")) {
				continue;
			}
			var id = item.get("id").s();
			var organisationId = item.get("organisationId").s();
			var innerItem = item.get("item").m();
			if (innerItem == null || !innerItem.containsKey("id")) {
				continue;
			}
			var fullId = innerItem.get("id").s();

			String typeId;
			if (id.endsWith(fullId)) {
				// not hashed
				typeId = id.substring(0, id.indexOf(":"));
			} else {
				//hashed
				var parts = organisationId.split(":");
				typeId = parts[1];
				organisationId = parts[0];
			}
			var type = this.classes.get(typeId);
			if (type != null) {
				var entity = new DynamoItem(table, item).convertTo(mapper, type);

				var orgIdFinal = organisationId;
				items.add(
					new Item<Table>(
						organisationId,
						entity,
						replacement -> put(orgIdFinal, replacement, true, false).join(),
						delete -> {
							delete(orgIdFinal, delete);
						}
					)
				);
			}
		}

		Object next = null;
		if (!scan.lastEvaluatedKey().isEmpty()) {
			next = TableUtil.convertTo(mapper, scan.lastEvaluatedKey(), Object.class);
		}

		return new ScanResult(items, next);
	}
}
