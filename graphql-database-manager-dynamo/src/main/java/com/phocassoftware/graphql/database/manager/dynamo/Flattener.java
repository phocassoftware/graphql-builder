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

package com.phocassoftware.graphql.database.manager.dynamo;

import tools.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.annotations.Hash;

import java.util.*;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public abstract class Flattener {

	public static Flattener create(List<String> entityTables, boolean b) {
		if (entityTables.size() > 1) {
			return new FlattenerMulti(entityTables, b);
		}
		return new FlattenerSingle(b);
	}

	public abstract DynamoItem get(Optional<Hash.HashExtractor> extractor, Class<? extends Table> type, String id);

	protected abstract void addItem(DynamoItem item);

	public final void addItems(List<DynamoItem> list) {
		list.forEach(item -> {
			addItem(item);
		});
	}

	public final void add(String table, List<Map<String, AttributeValue>> list) {
		list.forEach(item -> {
			var i = new DynamoItem(table, item);
			addItem(i);
		});
	}

	public final <T extends Table> List<T> results(ObjectMapper mapper, Class<T> type) {
		return results(mapper, type, Optional.empty());
	}

	public abstract <T extends Table> List<T> results(ObjectMapper mapper, Class<T> type, Optional<Integer> limit);
}
