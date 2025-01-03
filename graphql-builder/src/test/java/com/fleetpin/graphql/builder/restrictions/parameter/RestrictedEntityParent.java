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
package com.fleetpin.graphql.builder.restrictions.parameter;

import com.fleetpin.graphql.builder.RestrictType;
import com.fleetpin.graphql.builder.RestrictTypeFactory;
import com.fleetpin.graphql.builder.annotations.Restrict;
import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.CompletableFuture;

@Restrict(RestrictedEntityParent.EntityRestrictions.class)
public abstract class RestrictedEntityParent {

	private boolean allowed;

	public boolean isAllowed() {
		return allowed;
	}

	public void setAllowed(boolean allowed) {
		this.allowed = allowed;
	}

	public static class EntityRestrictions implements RestrictTypeFactory<RestrictedEntityParent> {

		@Override
		public CompletableFuture<RestrictType<RestrictedEntityParent>> create(DataFetchingEnvironment context) {
			return CompletableFuture.completedFuture(new DatabaseRestrict());
		}

		public static class DatabaseRestrict implements RestrictType<RestrictedEntityParent> {

			@Override
			public CompletableFuture<Boolean> allow(RestrictedEntityParent obj) {
				return CompletableFuture.completedFuture(obj.isAllowed());
			}
		}
	}
}
