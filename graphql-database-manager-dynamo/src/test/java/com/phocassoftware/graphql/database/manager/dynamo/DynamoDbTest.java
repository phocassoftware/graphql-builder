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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.annotations.TimeToLive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class DynamoDbTest {

	@Test
	public void testParallelHash() {
		assertEquals("00000000", DynamoDb.parallelHash(""));
		assertEquals("11000011", DynamoDb.parallelHash("1234"));
		assertEquals("00010111", DynamoDb.parallelHash("2"));
		assertEquals(8, DynamoDb.parallelHash(UUID.randomUUID().toString()).length());
	}

	@Test
	public void testTimeToLiveCopiedToTopLevelAsEpochSeconds() {
		var expiresAt = Instant.parse("2026-07-20T12:34:56.789Z");
		var entity = new ExpiringTable(expiresAt);
		entity.setId("entry");

		var item = dynamoDb().buildPutEntity("organisation", entity, false);

		assertEquals(Long.toString(expiresAt.getEpochSecond()), item.get("ttl").n());
		assertEquals(expiresAt.toString(), item.get("item").m().get("expiresAt").s());
	}

	@Test
	public void testNullTimeToLiveIsNotCopiedToTopLevel() {
		var entity = new ExpiringTable(null);
		entity.setId("entry");

		var item = dynamoDb().buildPutEntity("organisation", entity, false);

		assertFalse(item.containsKey("ttl"));
	}

	@Test
	public void testTimeToLiveMustAnnotateAnInstant() {
		var entity = new InvalidExpiringTable();
		entity.setId("entry");

		assertThrows(IllegalArgumentException.class, () -> dynamoDb().buildPutEntity("organisation", entity, false));
	}

	private DynamoDb dynamoDb() {
		return new DynamoDb(new ObjectMapper(), List.of("table"), List.of(), null, () -> "id");
	}

	static class ExpiringTable extends Table {

		@TimeToLive
		private Instant expiresAt;

		ExpiringTable(Instant expiresAt) {
			this.expiresAt = expiresAt;
		}

		public Instant getExpiresAt() {
			return expiresAt;
		}
	}

	static class InvalidExpiringTable extends Table {

		@TimeToLive
		private String expiresAt;

		public String getExpiresAt() {
			return expiresAt;
		}
	}
}
