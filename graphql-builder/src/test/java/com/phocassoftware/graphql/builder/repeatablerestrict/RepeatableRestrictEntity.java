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
package com.phocassoftware.graphql.builder.repeatablerestrict;

import com.phocassoftware.graphql.builder.RestrictType;
import com.phocassoftware.graphql.builder.RestrictTypeFactory;
import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.Query;
import com.phocassoftware.graphql.builder.annotations.Restrict;
import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.CompletableFuture;

/**
 * Exercises a class carrying more than one {@link Restrict} annotation. Java folds the repeated
 * annotation into the {@code @Restricts} container, which classgraph reports under both {@code Restrict}
 * and {@code Restricts}; the schema build must handle that without failing.
 */
@Entity
@Restrict(RepeatableRestrictEntity.FirstRestrictor.class)
@Restrict(RepeatableRestrictEntity.SecondRestrictor.class)
public class RepeatableRestrictEntity {

	private final boolean value;

	public RepeatableRestrictEntity(boolean value) {
		this.value = value;
	}

	public boolean isValue() {
		return value;
	}

	@Query
	public static RepeatableRestrictEntity entity() {
		return new RepeatableRestrictEntity(true);
	}

	public static class FirstRestrictor implements RestrictTypeFactory<RepeatableRestrictEntity>, RestrictType<RepeatableRestrictEntity> {

		@Override
		public CompletableFuture<RestrictType<RepeatableRestrictEntity>> create(DataFetchingEnvironment context) {
			return CompletableFuture.completedFuture(this);
		}

		@Override
		public CompletableFuture<Boolean> allow(RepeatableRestrictEntity obj) {
			return CompletableFuture.completedFuture(true);
		}
	}

	public static class SecondRestrictor implements RestrictTypeFactory<RepeatableRestrictEntity>, RestrictType<RepeatableRestrictEntity> {

		@Override
		public CompletableFuture<RestrictType<RepeatableRestrictEntity>> create(DataFetchingEnvironment context) {
			return CompletableFuture.completedFuture(this);
		}

		@Override
		public CompletableFuture<Boolean> allow(RepeatableRestrictEntity obj) {
			return CompletableFuture.completedFuture(true);
		}
	}
}
