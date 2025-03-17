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
package com.phocassoftware.graphql.builder;

import com.phocassoftware.graphql.builder.annotations.DataFetcherWrapper;
import com.phocassoftware.graphql.builder.annotations.Directive;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLDirective;
import org.reactivestreams.Publisher;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DirectivesSchema {

	private final Collection<RestrictTypeFactory<?>> global;
	private final Map<Class<? extends Annotation>, DirectiveCaller<?>> targets;
	private final Collection<Class<? extends Annotation>> directives;
	private final Collection<Class<? extends Annotation>> jakartaDirectives;
	private Map<Class<? extends Annotation>, DirectiveProcessor> directiveProcessors;

	private DirectivesSchema(
		Collection<RestrictTypeFactory<?>> global,
		Map<Class<? extends Annotation>, DirectiveCaller<?>> targets,
		Collection<Class<? extends Annotation>> directives,
		Collection<Class<? extends Annotation>> jakartaDirectives
	) {
		this.global = global;
		this.targets = targets;
		this.directives = directives;
		this.jakartaDirectives = jakartaDirectives;
	}

	//TODO:mess of exceptions
	public static DirectivesSchema build(
		List<RestrictTypeFactory<?>> globalDirectives,
		Set<Class<?>> directiveTypes,
		Set<Class<?>> jakartaDirectiveTypes
	) throws ReflectiveOperationException {
		Map<Class<? extends Annotation>, DirectiveCaller<?>> targets = new HashMap<>();

		Collection<Class<? extends Annotation>> allDirectives = new ArrayList<>();
		for (Class<?> directiveType : directiveTypes) {
			if (directiveType.isAnnotationPresent(DataFetcherWrapper.class)) {
				Class<? extends DirectiveOperation<?>> caller = directiveType.getAnnotation(DataFetcherWrapper.class).value();
				if (DirectiveCaller.class.isAssignableFrom(caller)) {
					// TODO error for no zero args constructor
					var callerInstance = (DirectiveCaller<?>) caller.getConstructor().newInstance();
					targets.put((Class<? extends Annotation>) directiveType, callerInstance);
				}
				continue;
			}
			if (!directiveType.isAnnotationPresent(Directive.class)) {
				continue;
			}
			if (!directiveType.isAnnotation()) {
				throw new RuntimeException("@Directive Annotation must only be placed on annotations");
			}
			allDirectives.add((Class<? extends Annotation>) directiveType);
		}

		Collection<Class<? extends Annotation>> jakartaDirectives = new ArrayList<>();
		for (Class<?> jakartaDirectiveType : jakartaDirectiveTypes) {
			if (!jakartaDirectiveType.isAnnotation()) {
				continue;
			}
			jakartaDirectives.add((Class<? extends Annotation>) jakartaDirectiveType);
		}

		return new DirectivesSchema(globalDirectives, targets, allDirectives, jakartaDirectives);
	}

	private DirectiveCaller<?> get(Annotation annotation) {
		return targets.get(annotation.annotationType());
	}

	private <T extends Annotation> DataFetcher<?> wrap(DirectiveCaller<T> directive, T annotation, DataFetcher<?> fetcher) {
		return env -> directive.process(annotation, env, fetcher);
	}

	public Stream<GraphQLDirective> getSchemaDirective() {
		return directiveProcessors.values().stream().map(DirectiveProcessor::getDirective);
	}

	private DataFetcher<?> wrap(RestrictTypeFactory<?> directive, DataFetcher<?> fetcher) {
		// TODO: hate having this cache here would love to scope against the env object but nothing to hook into dataload caused global leak
		Map<DataFetchingEnvironment, CompletableFuture<RestrictType>> cache = Collections.synchronizedMap(new WeakHashMap<>());

		return env ->
			cache
				.computeIfAbsent(env, key -> directive.create(key).thenApply(t -> t))
				.thenCompose(restrict -> {
					try {
						Object response = fetcher.get(env);
						if (response instanceof CompletionStage) {
							return ((CompletionStage) response).thenCompose(r -> applyRestrict(restrict, r));
						}
						return applyRestrict(restrict, response);
					} catch (Exception e) {
						if (e instanceof RuntimeException runtimeException) {
							throw runtimeException;
						}
						throw new RuntimeException(e);
					}
				});
	}

	public boolean target(Method method, TypeMeta meta) {
		for (var globalRestricts : this.global) {
			//TODO: extract class
			if (globalRestricts.extractType().isAssignableFrom(meta.getType())) {
				return true;
			}
		}
		for (Annotation annotation : method.getAnnotations()) {
			if (get(annotation) != null) {
				return true;
			}
		}
		return false;
	}

	public DataFetcher<?> wrap(Method method, TypeMeta meta, DataFetcher<?> fetcher) {
		for (var g : global) {
			if (g.extractType().isAssignableFrom(meta.getType())) {
				fetcher = wrap(g, fetcher);
			}
		}
		for (Annotation annotation : method.getAnnotations()) {
			DirectiveCaller directive = get(annotation);
			if (directive != null) {
				fetcher = wrap(directive, annotation, fetcher);
			}
		}
		return fetcher;
	}

	private CompletableFuture<Object> applyRestrict(RestrictType restrict, Object response) {
		if (response instanceof List) {
			return restrict.filter((List) response);
		} else if (response instanceof Publisher) {
			return CompletableFuture.completedFuture(new FilteredPublisher((Publisher) response, restrict));
		} else if (response instanceof Optional) {
			var optional = (Optional) response;
			if (optional.isEmpty()) {
				return CompletableFuture.completedFuture(response);
			}
			var target = optional.get();
			if (target instanceof List) {
				return restrict.filter((List) target);
			} else {
				return restrict
					.allow(target)
					.thenApply(allow -> {
						if (allow == Boolean.TRUE) {
							return response;
						} else {
							return Optional.empty();
						}
					});
			}
		} else {
			return restrict
				.allow(response)
				.thenApply(allow -> {
					if (allow == Boolean.TRUE) {
						return response;
					} else {
						return null;
					}
				});
		}
	}

	public void addSchemaDirective(AnnotatedElement element, Class<?> location, Consumer<GraphQLAppliedDirective> builder) {
		for (Annotation annotation : element.getAnnotations()) {
			var processor = this.directiveProcessors.get(annotation.annotationType());
			if (processor != null) {
				try {
					processor.apply(annotation, builder);
				} catch (InvocationTargetException | IllegalAccessException e) {
					throw new RuntimeException("Could not process applied directive: " + location.getName());
				}
			}
		}
	}

	public void processDirectives(EntityProcessor ep) { // Replacement of processSDL
		Map<Class<? extends Annotation>, DirectiveProcessor> directiveProcessorsList = new HashMap<>();

		this.directives.forEach(dir -> directiveProcessorsList.put(dir, DirectiveProcessor.build(ep, dir, false)));
		this.jakartaDirectives.forEach(dir -> directiveProcessorsList.put(dir, DirectiveProcessor.build(ep, dir, true)));
		this.directiveProcessors = directiveProcessorsList;
	}
}
