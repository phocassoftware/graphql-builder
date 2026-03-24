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
import com.phocassoftware.graphql.database.manager.util.TableCoreUtil;
import java.util.*;
import java.util.stream.Collectors;

public final class FlattenerMulti extends Flattener {

	private final Map<String, DynamoItem> lookup;
	private final boolean includeOrganisationId;
	private final List<String> tables;

	FlattenerMulti(List<String> tables, boolean includeOrganisationId) {
		this.tables = tables;
		lookup = new HashMap<>();
		this.includeOrganisationId = includeOrganisationId;
	}

	private String getId(DynamoItem item) {
		if (includeOrganisationId) {
			return item.getOrganisationId() + ":" + item.getId();
		} else {
			return item.getId();
		}
	}

	@Override
	public DynamoItem get(Optional<Hash.HashExtractor> extractor, Class<? extends Table> type, String id) {
		String key;
		if (extractor.isPresent()) {
			key = TableCoreUtil.table(type) + ":" + extractor.get().hashId(id) + "\t" + extractor.get().sortId(id);
		} else {
			key = TableCoreUtil.table(type) + ":" + id;
		}
		var got = this.lookup.get(key);
		if (got != null && got.isDeleted()) {
			return null;
		} else {
			return got;
		}
	}

	protected void addItem(DynamoItem item) {
		lookup.merge(getId(item), item, this::merge);
	}

	private DynamoItem merge(DynamoItem existing, DynamoItem replace) {
		if (tables.indexOf(existing.getTable()) > tables.indexOf(replace.getTable())) {
			var tmp = existing;
			existing = replace;
			replace = tmp;
		}

		var item = new HashMap<>(replace.getItem());
		// only links in parent
		if (item.get("item") == null) {
			item.put("item", existing.getItem().get("item"));
		}
		var toReturn = new DynamoItem(replace.getTable(), item);
		for (var entry : existing.getLinks().entrySet()) {
			var current = toReturn.getLinks().get(entry.getKey());
			if (current == null) {
				toReturn.getLinks().put(entry.getKey(), entry.getValue());
			} else {
				current.addAll(entry.getValue());
			}
		}

		return toReturn;
	}

	public <T extends Table> List<T> results(ObjectMapper mapper, Class<T> type, Optional<Integer> limit) {
		var items = new ArrayList<DynamoItem>(lookup.values());
		Collections.sort(items);
		return items.stream().limit(limit.orElse(Integer.MAX_VALUE)).map(t -> t.convertTo(mapper, type)).collect(Collectors.toList());
	}
}
