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

import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.ParameterizedType;
import java.util.concurrent.CompletableFuture;

public interface RestrictTypeFactory<T> {
	public CompletableFuture<RestrictType<T>> create(DataFetchingEnvironment context);

	default Class<T> extractType() {
		for (var inter : getClass().getGenericInterfaces()) {
			if (inter instanceof ParameterizedType) {
				var param = (ParameterizedType) inter;
				if (RestrictTypeFactory.class.equals(param.getRawType())) {
					return (Class<T>) param.getActualTypeArguments()[0];
				}
			}
		}
		return null;
	}
}
