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
package com.phocassoftware.graphql.database.manager;

import java.util.function.Consumer;

public class QueryBuilder<V extends Table> {

	private final Class<V> type;
	private String startsWith;
	private String after;
	private Integer limit;
	private Integer threadIndex;
	private Integer threadCount;
	private Boolean scanIndexForward;

	private QueryBuilder(Class<V> type) {
		this.type = type;
	}

	public QueryBuilder<V> startsWith(String prefix) {
		this.startsWith = prefix;
		return this;
	}

	public QueryBuilder<V> limit(Integer limit) {
		this.limit = limit;
		return this;
	}

	public QueryBuilder<V> after(String from) {
		this.after = from;
		return this;
	}

	public QueryBuilder<V> threadCount(Integer threadCount) {
		this.threadCount = threadCount;
		return this;
	}

	public QueryBuilder<V> threadIndex(Integer threadIndex) {
		this.threadIndex = threadIndex;
		return this;
	}

	public QueryBuilder<V> scanIndexForward(Boolean scanIndexForward) {
		this.scanIndexForward = scanIndexForward;
		return this;
	}

	public QueryBuilder<V> applyMutation(Consumer<QueryBuilder<V>> mutator) {
		mutator.accept((QueryBuilder<V>) this);
		return (QueryBuilder<V>) this;
	}

	public Query<V> build() {
		return new Query<V>(type, startsWith, after, limit, threadCount, threadIndex, scanIndexForward);
	}

	public static <V extends Table> QueryBuilder<V> create(Class<V> type) {
		return new QueryBuilder<V>(type);
	}
}
