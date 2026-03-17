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

import java.util.Objects;

public class Query<T extends Table> {

	private final Class<T> type;
	private final String startsWith;
	private final String after;
	private final Integer limit;
	private final Integer threadCount;
	private final Integer threadIndex;
	private final Boolean scanIndexForward;

	Query(Class<T> type, String startsWith, String after, Integer limit, Integer threadCount, Integer threadIndex, Boolean scanIndexForward) {
		if (type == null) {
			throw new RuntimeException("type can not be null, did you forget to call .on(Table::class)?");
		}

		if (threadCount != null && !isPowerOfTwo(threadCount)) {
			throw new RuntimeException("Thread count must be a power of two");
		}

		if ((threadCount != null && threadIndex == null) || (threadCount == null && threadIndex != null)) {
			throw new RuntimeException("Thread count and thread index must both be defined if you are doing a parallel request");
		}

		this.type = type;
		this.startsWith = startsWith;
		this.after = after;
		this.limit = limit;
		this.threadCount = threadCount;
		this.threadIndex = threadIndex;
		this.scanIndexForward = scanIndexForward;
	}

	public Class<T> getType() {
		return type;
	}

	public String getStartsWith() {
		return startsWith;
	}

	public String getAfter() {
		return after;
	}

	public Integer getLimit() {
		return limit;
	}

	public Integer getThreadCount() {
		return threadCount;
	}

	public Integer getThreadIndex() {
		return threadIndex;
	}

	public Boolean getScanIndexForward() {
		return scanIndexForward;
	}

	public boolean hasLimit() {
		return getLimit() != null;
	}

	@Override
	public int hashCode() {
		return Objects.hash(after, limit, startsWith, type, threadIndex, threadCount, scanIndexForward);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Query other = (Query) obj;
		return (Objects.equals(after, other.after) &&
			Objects.equals(limit, other.limit) &&
			Objects.equals(startsWith, other.startsWith) &&
			Objects.equals(type, other.type) &&
			Objects.equals(threadCount, other.threadCount) &&
			Objects.equals(threadIndex, other.threadIndex)) &&
			Objects.equals(scanIndexForward, other.scanIndexForward);
	}

	static boolean isPowerOfTwo(int n) {
		if (n == 0) return false;

		return (int) (Math.ceil((Math.log(n) / Math.log(2)))) == (int) (Math.floor(((Math.log(n) / Math.log(2)))));
	}
}
