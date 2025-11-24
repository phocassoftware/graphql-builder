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

import java.lang.reflect.AnnotatedElement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.Database;
import com.phocassoftware.graphql.database.manager.VirtualDatabase;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import com.phocassoftware.graphql.database.manager.test.annotations.*;
import com.phocassoftware.graphql.database.manager.test.annotations.TestDatabase;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;

public final class TestDatabaseProvider implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

	private static ServerWrapper serverWrapper;

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		if (parameterContext.getParameter().getType().isAssignableFrom(DynamoDbManager.class)) {
			return true;
		}
		if (parameterContext.getParameter().getType().isAssignableFrom(HistoryProcessor.class)) {
			return true;
		}
		if (parameterContext.getParameter().getType().isAssignableFrom(Database.class)) {
			return true;
		}
		if (parameterContext.getParameter().getType().isAssignableFrom(VirtualDatabase.class)) {
			return true;
		}
		if (parameterContext.getParameter().getType().isAssignableFrom(DynamoDbAsyncClient.class)) {
			return true;
		}
		if (parameterContext.getParameter().getType().isAssignableFrom(DynamoDbClient.class)) {
			return true;
		}

		final var testMethod = extensionContext.getRequiredTestMethod();
		var testDatabase = getTestDatabase(testMethod);

		if (testDatabase != null) {
			return testDatabase
				.providers()
				.stream()
				.anyMatch(provider -> parameterContext.getParameter().getType().isAssignableFrom(provider.type()));
		}

		return false;
	}

	private ProviderFunction<?> create(Class<? extends ProviderFunction<?>> provider) {
		try {
			return provider.getConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		var store = extensionContext.getStore(Namespace.create(extensionContext.getUniqueId()));
		var arguments = store.get("arguments", List.class);
		return arguments.get(parameterContext.getIndex());
	}

	@Override
	public void beforeEach(ExtensionContext extensionContext) throws Exception {
		var wrapper = getServer();
		final var testMethod = extensionContext.getRequiredTestMethod();

		TestDatabaseSetup setup;

		var testDatabase = getTestDatabase(testMethod);

		var classPath = testDatabase.classPath();
		var hashed = testDatabase.hashed();
		var objectMapper = testDatabase.objectMapper();

		final var withHistory = Arrays
			.stream(testMethod.getParameters())
			.map(parameter -> parameter.getType().isAssignableFrom(HistoryProcessor.class))
			.filter(p -> p)
			.findFirst()
			.orElse(false);

		var uniqueId = UUID.randomUUID().toString();

		var toDelete = new HashSet<String>();

		var store = extensionContext.getStore(Namespace.create(extensionContext.getUniqueId()));
		final var argumentsList = Arrays
			.stream(testMethod.getParameters())
			.map(parameter -> {
				var provider = new ArgumentProvider(uniqueId, wrapper, parameter, withHistory, hashed, classPath, objectMapper, toDelete::add);
				try {
					var type = parameter.getType();
					if (type.isAssignableFrom(DynamoDbManager.class)) {
						return provider.getDynamoDbManager(true);
					} else if (type.isAssignableFrom(HistoryProcessor.class)) {
						return provider.getHistoryProcessor();
					} else if (type.isAssignableFrom(DynamoDbAsyncClient.class)) {
						return provider.getClientAsync();
					} else if (type.isAssignableFrom(DynamoDbClient.class)) {
						return provider.getClient();
					} else if (type.isAssignableFrom(VirtualDatabase.class)) {
						return provider.getVirtualDatabase();
					} else if (type.isAssignableFrom(Database.class)) {
						return provider.getDatabase();
					} else {
						var builder = testDatabase
							.providers()
							.stream()
							.filter(p -> type.isAssignableFrom(p.type()))
							.findAny()
							.orElse(null);
						if (builder == null) {
							return null;
						}
						return builder.create(provider);

					}
				} catch (final Exception e) {
					throw new RuntimeException("Could not build parameters", e);
				}
			})
			.collect(Collectors.toList());
		store.put("arguments", argumentsList);
		store.put("toDelete", toDelete);
	}

	@Override
	public void afterEach(ExtensionContext extensionContext) throws Exception {
		var store = extensionContext.getStore(Namespace.create(extensionContext.getUniqueId()));
		Set<String> toDelete = store.get("toDelete", Set.class);
		if (toDelete != null) {
			var wrapper = getServer();
			var client = wrapper.client();
			toDelete.forEach(table -> client.deleteTable(b -> b.tableName(table)));
		}
	}

	private ServerWrapper getServer() throws Exception {
		if (serverWrapper == null) {
			synchronized (TestDatabaseProvider.class) {
				if (serverWrapper == null) {
					final String port = findFreePort();
					startDynamoServer(port);
					final var clientAsync = startDynamoAsyncClient(port);
					final var client = startDynamoClient(port);

					final var streamClient = startDynamoStreamClient(port);

					createTable(client, "table");
					serverWrapper = new ServerWrapper(client, clientAsync, streamClient);
				}

			}
		}

		return serverWrapper;

	}

	private TestDatabaseSetup getTestDatabase(AnnotatedElement annotatedElement) {
		try {
			var annotation = annotatedElement.getAnnotation(TestDatabase.class);
			var classBased = annotatedElement.getAnnotation(TestDatabaseBuilder.class);
			if (annotation == null && classBased == null) {
				for (var a : annotatedElement.getAnnotations()) {
					var setup = getTestDatabase(a.annotationType());
					if (setup != null) {
						return setup;
					}
				}
			}
			if (annotation != null) {
				var providers = Stream
					.of(annotation.providers())
					.map(t -> create(t))
					.toList();

				var objectMapper = annotation.objectMapper().getConstructor().newInstance().get();
				return new TestDatabaseAnnotationSetup(annotation.classPath(), annotation.hashed(), objectMapper, providers);
			}

			return classBased.value().getConstructor().newInstance();

		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	record ServerWrapper(DynamoDbClient client, DynamoDbAsyncClient clientAsync, DynamoDbStreamsAsyncClient streamClient) {}

	record TestDatabaseAnnotationSetup(String classPath, boolean hashed, ObjectMapper objectMapper, List<? extends ProviderFunction<?>> providers) implements
		TestDatabaseSetup {}
}
