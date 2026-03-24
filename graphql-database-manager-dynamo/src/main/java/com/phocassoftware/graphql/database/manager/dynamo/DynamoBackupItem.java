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

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.util.BackupItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class DynamoBackupItem implements Comparable<DynamoBackupItem>, BackupItem {

	private String table;
	private Map<String, Object> item;
	private String id;

	private Map<String, Set<String>> links;
	private String organisationId;
	private boolean hashed;
	private String parallelHash;

	public DynamoBackupItem() {}

	public DynamoBackupItem(String table, Map<String, AttributeValue> item, ObjectMapper objectMapper) {
		this.table = table;
		this.item = (Map<String, Object>) TableUtil.convertTo(objectMapper, item, Map.class);

		this.links = new HashMap<>();

		var links = item.get("links");
		if (links != null) {
			links
				.m()
				.forEach((t, value) -> {
					this.links.put(t, new HashSet<>(value.ss()));
				});
		}
		this.id = item.get("id").s();

		this.organisationId = item.get("organisationId").s();

		var hashed = item.get("hashed");
		var parallelHash = item.get("parallelHash");

		if (hashed != null) {
			this.hashed = hashed.bool().booleanValue();
		}
		if (parallelHash != null) {
			this.parallelHash = parallelHash.s();
		}
	}

	public String getTable() {
		return table;
	}

	public Map<String, Object> getItem() {
		return item;
	}

	@JsonIgnore
	public Map<String, Set<String>> getLinks() {
		return links;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public int compareTo(DynamoBackupItem o) {
		return getId().compareTo(o.getId());
	}

	@Override
	public String getOrganisationId() {
		return organisationId;
	}

	@Override
	public boolean isHashed() {
		return hashed;
	}

	@Override
	public String getParallelHash() {
		return parallelHash;
	}
}
