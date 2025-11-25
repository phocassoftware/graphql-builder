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

import com.phocassoftware.graphql.builder.annotations.InnerNullable;

import graphql.execution.DataFetcherResult;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;

public class TypeMeta {

	enum Flag {
		ASYNC,
		ARRAY,
		OPTIONAL,
		SUBSCRIPTION,
		DIRECT,
	}

	private List<Flag> flags;

	private Type genericType;
	private Class<?> type;

	private boolean direct;

	private final TypeMeta parent;

	private List<Class<?>> types;

	private AnnotatedElement element;

	TypeMeta(TypeMeta parent, Class<?> type, Type genericType) {
		this(parent, type, genericType, null);
	}

	TypeMeta(TypeMeta parent, Class<?> type, Type genericType, AnnotatedElement element) {
		this.parent = parent;
		this.element = element;
		flags = new ArrayList<>();
		types = new ArrayList<>();
		process(type, genericType, element);
		Collections.reverse(flags);
	}

	private void processGeneric(TypeMeta target, TypeVariable type, AnnotatedElement element) {
		var owningClass = target.genericType;
		if (owningClass == null) {
			owningClass = target.type;
		}
		if (owningClass instanceof Class) {
			findType(target, type, (Class) owningClass, element);
		} else if (owningClass instanceof ParameterizedType) {
			var pt = (ParameterizedType) owningClass;
			if (!matchType(target, type.getTypeName(), pt, true, element)) {
				throw new UnsupportedOperationException("Does not handle type " + owningClass);
			}
		} else {
			throw new UnsupportedOperationException("Does not handle type " + owningClass);
		}
	}

	private boolean matchType(TypeMeta target, String typeName, ParameterizedType type, boolean parent, AnnotatedElement element) {
		var raw = (Class) type.getRawType();
		while (raw != null) {
			for (int i = 0; i < raw.getTypeParameters().length; i++) {
				var arg = type.getActualTypeArguments()[i];
				var param = raw.getTypeParameters()[i];
				if (param.getTypeName().equals(typeName)) {
					if (arg instanceof TypeVariable) {
						if (parent) {
							processGeneric(target.parent, (TypeVariable) arg, element);
						} else {
							processGeneric(target, (TypeVariable) arg, element);
						}
						return true;
					} else if (arg instanceof WildcardType) {
						for (var bound : param.getBounds()) {
							if (bound instanceof ParameterizedType) {
								process((Class<?>) ((ParameterizedType) bound).getRawType(), bound, element);
							} else if (bound instanceof TypeVariable) {
								processGeneric(target, (TypeVariable) bound, element);
							} else {
								process((Class<?>) bound, null, element);
							}
						}
						return true;
					} else if (arg instanceof ParameterizedType pType) {
						process((Class) pType.getRawType(), pType, element);
						return true;
					} else {
						var klass = (Class) arg;
						process(klass, klass, element);
						return true;
					}
				}
			}
			raw = raw.getSuperclass();
		}
		return false;
	}

	private void findType(TypeMeta target, TypeVariable type, Class start, AnnotatedElement element) {
		var startClass = (Class) start;
		var genericDeclaration = type.getGenericDeclaration();
		if (start.equals(genericDeclaration)) {
			// we don't have any implementing logic we are at this level so take the bounds
			for (var bound : type.getBounds()) {
				if (bound instanceof ParameterizedType) {
					process((Class<?>) ((ParameterizedType) bound).getRawType(), bound, element);
				} else if (bound instanceof TypeVariable) {
					processGeneric(target, (TypeVariable) bound, element);
				} else {
					process((Class<?>) bound, null, element);
				}
			}
		}
		if (startClass.getSuperclass() != null && startClass.getSuperclass().equals(genericDeclaration)) {
			var generic = (ParameterizedType) startClass.getGenericSuperclass();
			if (matchType(target, type.getTypeName(), generic, false, element)) {
				return;
			}
		}
		for (var inter : startClass.getGenericInterfaces()) {
			if (inter instanceof ParameterizedType) {
				var generic = (ParameterizedType) inter;
				if (generic.getRawType().equals(genericDeclaration)) {
					if (matchType(target, type.getTypeName(), generic, false, element)) {
						return;
					}
				}
			}
		}
		if (startClass.getSuperclass() != null) {
			findType(target, type, startClass.getSuperclass(), element);
		}

		for (var inter : startClass.getInterfaces()) {
			findType(target, type, inter, element);
		}
	}

	private void process(Class<?> type, Type genericType, AnnotatedElement element) {
		if (element != null && (element.isAnnotationPresent(Nullable.class) || element.isAnnotationPresent(jakarta.annotation.Nullable.class))) {
			if (!flags.contains(Flag.OPTIONAL)) {
				flags.add(Flag.OPTIONAL);
			}
		}

		if (type.isArray()) {
			flags.add(Flag.ARRAY);
			types.add(type);
			process(type.getComponentType(), null, element);
			return;
		}

		if (Collection.class.isAssignableFrom(type)) {
			flags.add(Flag.ARRAY);
			types.add(type);
			genericType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
			if (genericType instanceof ParameterizedType) {
				process((Class<?>) ((ParameterizedType) genericType).getRawType(), genericType, element);
			} else if (genericType instanceof TypeVariable) {
				processGeneric(parent, (TypeVariable) genericType, element);
			} else {
				process((Class<?>) genericType, null, element);
			}
			return;
		}
		if (CompletableFuture.class.isAssignableFrom(type)) {
			flags.add(Flag.ASYNC);
			types.add(type);
			genericType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
			if (genericType instanceof ParameterizedType) {
				process((Class<?>) ((ParameterizedType) genericType).getRawType(), genericType, element);
			} else if (genericType instanceof TypeVariable) {
				processGeneric(parent, (TypeVariable) genericType, element);
			} else {
				process((Class<?>) genericType, null, element);
			}
			return;
		}
		if (DataFetcherResult.class.isAssignableFrom(type)) {
			types.add(type);
			genericType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
			if (genericType instanceof ParameterizedType) {
				process((Class<?>) ((ParameterizedType) genericType).getRawType(), genericType, element);
			} else if (genericType instanceof TypeVariable) {
				processGeneric(parent, (TypeVariable) genericType, element);
			} else {
				process((Class<?>) genericType, null, element);
			}
			return;
		}

		if (Optional.class.isAssignableFrom(type)) {
			flags.add(Flag.OPTIONAL);
			types.add(type);
			genericType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
			if (genericType instanceof ParameterizedType) {
				process((Class<?>) ((ParameterizedType) genericType).getRawType(), genericType, element);
			} else if (genericType instanceof TypeVariable) {
				processGeneric(parent, (TypeVariable) genericType, element);
			} else {
				process((Class<?>) genericType, null, element);
			}
			return;
		}

		if (Publisher.class.isAssignableFrom(type)) {
			flags.add(Flag.SUBSCRIPTION);
			types.add(type);
			genericType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
			if (genericType instanceof ParameterizedType) {
				process((Class<?>) ((ParameterizedType) genericType).getRawType(), genericType, element);
			} else if (genericType instanceof TypeVariable) {
				processGeneric(parent, (TypeVariable) genericType, element);
			} else {
				process((Class<?>) genericType, null, element);
			}
			return;
		}

		if (genericType != null && genericType instanceof TypeVariable) {
			processGeneric(parent, (TypeVariable) genericType, element);
			return;
		}

		if (element != null && (element.isAnnotationPresent(InnerNullable.class))) {
			if (!flags.contains(Flag.OPTIONAL)) {
				flags.add(Flag.OPTIONAL);
			}
		}

		this.type = type;
		this.genericType = genericType;
		types.add(type);
	}

	public Class<?> getType() {
		return type;
	}

	public Type getGenericType() {
		return genericType;
	}

	public List<Flag> getFlags() {
		return flags;
	}

	public List<Class<?>> getTypes() {
		return types;
	}

	public boolean hasUnmappedGeneric() {
		if (type.getTypeParameters().length == 0) {
			return false;
		}
		if (genericType == null || !(genericType instanceof ParameterizedType)) {
			return true;
		}

		ParameterizedType pt = (ParameterizedType) genericType;

		for (var type : pt.getActualTypeArguments()) {
			if (type instanceof TypeVariable) {
				if (resolveToType((TypeVariable) type) == null) {
					return true;
				}
			} else if (!(type instanceof Class)) {
				return true;
			}
		}
		return false;
	}

	public Class resolveToType(TypeVariable variable) {
		var parent = this.parent;

		var parentType = type;
		var genericType = this.genericType;

		while (true) {
			if (genericType instanceof ParameterizedType) {
				var pt = parentType.getTypeParameters();
				for (int i = 0; i < pt.length; i++) {
					var p = pt[i];
					if (p.equals(variable)) {
						var generic = (ParameterizedType) genericType; // safe as has to if equal vaiable
						var implementingType = generic.getActualTypeArguments()[i];
						if (implementingType instanceof Class) {
							return (Class) implementingType;
						} else if (implementingType instanceof TypeVariable) {
							return resolveToType((TypeVariable) implementingType);
						} else {
							throw new RuntimeException("Generics are more complex that logic currently can handle");
						}
					}
				}
			}

			var pt = parentType.getSuperclass().getTypeParameters();
			for (int i = 0; i < pt.length; i++) {
				var p = pt[i];
				if (p.equals(variable)) {
					var superClass = (ParameterizedType) parentType.getGenericSuperclass(); // safe as has to if equal vaiable
					var implementingType = superClass.getActualTypeArguments()[i];

					if (implementingType instanceof Class) {
						return (Class) implementingType;
					} else if (implementingType instanceof TypeVariable) {
						return resolveToType((TypeVariable) implementingType);
					} else {
						throw new RuntimeException("Generics are more complex that logic currently can handle");
					}
				}
			}

			if (parent == null) {
				break;
			}
			parentType = parent.getType();
			genericType = parent.getGenericType();
			parent = parent.parent;
		}
		return null;
	}

	public void optional() {
		flags.add(Flag.OPTIONAL);
	}

	public TypeMeta direct() {
		var toReturn = new TypeMeta(parent, type, genericType, element);
		toReturn.direct = true;
		return toReturn;
	}

	public boolean isDirect() {
		return direct;
	}

	public TypeMeta notDirect() {
		var toReturn = new TypeMeta(parent, type, genericType, element);
		return toReturn;
	}
}
