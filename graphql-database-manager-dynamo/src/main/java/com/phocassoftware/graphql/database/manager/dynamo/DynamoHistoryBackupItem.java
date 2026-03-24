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
import com.phocassoftware.graphql.database.manager.util.HistoryBackupItem;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class DynamoHistoryBackupItem extends DynamoBackupItem implements HistoryBackupItem {

	private String organisationIdType;

	private byte[] idRevision;

	private byte[] idDate;

	private byte[] startsWithUpdatedAt;

	private Long updatedAt;

	public DynamoHistoryBackupItem() {}

	public DynamoHistoryBackupItem(String table, Map<String, AttributeValue> item, ObjectMapper objectMapper) {
		super(table, item, objectMapper);
		this.organisationIdType = item.get("organisationIdType").s();

		this.idRevision = item.get("idRevision").b().asByteArray();

		this.idDate = item.get("idDate").b().asByteArray();

		this.startsWithUpdatedAt = item.get("startsWithUpdatedAt").b().asByteArray();

		this.updatedAt = Long.parseLong(item.get("updatedAt").n());
	}

	@Override
	public String getOrganisationIdType() {
		return this.organisationIdType;
	}

	@Override
	public byte[] getIdRevision() {
		return this.idRevision;
	}

	@Override
	public byte[] getIdDate() {
		return this.idDate;
	}

	@Override
	public byte[] getStartsWithUpdatedAt() {
		return this.startsWithUpdatedAt;
	}

	@Override
	public Long getUpdatedAt() {
		return this.updatedAt;
	}
}
