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

import com.phocassoftware.graphql.builder.annotations.Context;
import com.phocassoftware.graphql.builder.annotations.GraphQLIgnore;
import com.phocassoftware.graphql.builder.annotations.GraphQLName;
import com.phocassoftware.graphql.builder.annotations.InputIgnore;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Optional;

class EntityUtil {

	static String getName(TypeMeta meta) {
		var type = meta.getType();

		var genericType = meta.getGenericType();

		var name = buildUpName(meta, type, genericType);
		if (meta.isDirect()) {
			name += "_DIRECT";
		}

		return name;
	}

	private static String buildUpName(TypeMeta meta, Class<?> type, Type genericType) {
		String name = type.getSimpleName();

		for (int i = 0; i < type.getTypeParameters().length; i++) {
			if (genericType instanceof ParameterizedType) {
				var t = ((ParameterizedType) genericType).getActualTypeArguments()[i];
				if (t instanceof Class) {
					String extra = ((Class) t).getSimpleName();
					name += "_" + extra;
				} else if (t instanceof TypeVariable) {
					var variable = (TypeVariable) t;
					Class extra = meta.resolveToType(variable);
					if (extra != null) {
						name += "_" + extra.getSimpleName();
					}
				} else if (t instanceof ParameterizedType pType) {
					var rawType = pType.getRawType();
					if (rawType instanceof Class rawClass) {
						var extra = buildUpName(meta, rawClass, pType);
						name += "_" + extra;
					} else {
						throw new RuntimeException("Generics are more complex that logic currently can handle");
					}
				}
			} else {
				Class extra = meta.resolveToType(type.getTypeParameters()[i]);
				if (extra != null) {
					name += "_" + extra.getSimpleName();
				}
			}
		}
		return name;
	}

	public static Optional<String> getter(Method method) {
		if (method.isSynthetic()) {
			return Optional.empty();
		}
		if (method.getDeclaringClass().equals(Object.class)) {
			return Optional.empty();
		}
		if (method.isAnnotationPresent(GraphQLIgnore.class)) {
			return Optional.empty();
		}
		// will also be on implementing class
		if (Modifier.isAbstract(method.getModifiers()) || method.getDeclaringClass().isInterface()) {
			return Optional.empty();
		}
		if (Modifier.isStatic(method.getModifiers())) {
			return Optional.empty();
		} else if (method.getName().matches("(get|is)[A-Z].*")) {
			String name;
			if (method.getName().startsWith("get")) {
				name = method.getName().substring("get".length(), "get".length() + 1).toLowerCase() + method.getName().substring("get".length() + 1);
			} else {
				name = method.getName().substring("is".length(), "is".length() + 1).toLowerCase() + method.getName().substring("is".length() + 1);
			}

			if (fieldSpecifiesIgnore(method, name)) {
				return Optional.empty();
			}

			return Optional.of(getName(name, method));
		}
		return Optional.empty();
	}

	public static Optional<String> setter(Method method) {
		if (method.isSynthetic()) {
			return Optional.empty();
		}
		if (method.getDeclaringClass().equals(Object.class)) {
			return Optional.empty();
		}
		if (method.isAnnotationPresent(GraphQLIgnore.class)) {
			return Optional.empty();
		}
		// will also be on implementing class
		if (Modifier.isAbstract(method.getModifiers()) || method.getDeclaringClass().isInterface()) {
			return Optional.empty();
		}
		if (Modifier.isStatic(method.getModifiers())) {
			return Optional.empty();
		} else if (method.getName().matches("set[A-Z].*")) {
			if (method.getParameterCount() == 1 && !method.isAnnotationPresent(InputIgnore.class)) {
				String name = method.getName().substring("set".length(), "set".length() + 1).toLowerCase() + method.getName().substring("set".length() + 1);

				if (fieldSpecifiesIgnore(method, name)) {
					return Optional.empty();
				}

				return Optional.of(getName(name, method));
			}
		}
		return Optional.empty();
	}

	static boolean fieldSpecifiesIgnore(Method method, String name) {
		var fields = method.getDeclaringClass().getDeclaredFields();
		for (var field : fields) {
			if (!field.getName().equals(name)) {
				continue;
			}
			if (!field.isAnnotationPresent(GraphQLIgnore.class)) {
				return false;
			}

			return field.getType().equals(method.getReturnType());
		}
		return false;
	}

	static String getName(String fallback, AnnotatedElement... annotated) {
		for (var anno : annotated) {
			if (anno.isAnnotationPresent(GraphQLName.class)) {
				return anno.getAnnotation(GraphQLName.class).value();
			}
		}
		return fallback;
	}

	static boolean isContext(Class<?> class1, Annotation[] annotations) {
		for (var annotation : annotations) {
			if (annotation instanceof Context) {
				return true;
			}
		}
		return (class1.isAssignableFrom(GraphQLContext.class) || class1.isAssignableFrom(DataFetchingEnvironment.class) || class1
			.isAnnotationPresent(Context.class));
	}

	static <T extends Annotation> T getAnnotation(Class<?> type, Class<T> annotation) {
		var response = type.getAnnotation(annotation);
		if (response != null) {
			return response;
		}

		if (type.getSuperclass() != null) {
			response = getAnnotation(type.getSuperclass(), annotation);
			if (response != null) {
				return response;
			}
		}

		for (var parent : type.getInterfaces()) {
			response = getAnnotation(parent, annotation);
			if (response != null) {
				return response;
			}
		}
		return null;
	}
}
