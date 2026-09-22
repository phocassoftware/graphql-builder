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

package com.phocassoftware.graphql.database.manager.test;

import com.phocassoftware.graphql.database.manager.Database;
import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.dynamo.DynamoDbManager;
import com.phocassoftware.graphql.database.manager.test.annotations.DatabaseNames;
import com.phocassoftware.graphql.database.manager.test.annotations.DatabaseOrganisation;
import com.phocassoftware.graphql.database.manager.test.annotations.GlobalEnabled;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;

final class DynamoDbPutGetDeleteTest {

	@TestDatabase
	void testSimplePutGetDelete(final Database db) throws InterruptedException, ExecutionException {
		SimpleTable entry1 = new SimpleTable("garry");
		entry1 = db.put(entry1).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertNotNull(entry1.getId());

		String id = entry1.getId();

		entry1 = db.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		db.delete(entry1, false).get();
		entry1 = db.get(SimpleTable.class, id).get();
		Assertions.assertNull(entry1);
	}

	@TestDatabase
	void testListPutGetDelete(final Database db) throws InterruptedException, ExecutionException {
		var entries = List.of(new SimpleTable("garry"), new SimpleTable("bob"));

		var putEntries = db.put(entries).get();

		Assertions.assertEquals(List.of("garry", "bob"), putEntries.stream().map(SimpleTable::getName).toList());
		Assertions.assertTrue(putEntries.stream().allMatch(entry -> entry.getId() != null));
		Assertions.assertEquals(putEntries, db.get(SimpleTable.class, putEntries.stream().map(Table::getId).toList()).get());

		var deletedEntries = db.delete(putEntries, false).get();

		Assertions.assertEquals(putEntries, deletedEntries);
		Assertions.assertTrue(db.get(SimpleTable.class, putEntries.stream().map(Table::getId).toList()).get().stream().allMatch(item -> item == null));
	}

	@TestDatabase
	void testEmptyListPutDelete(final Database db) throws InterruptedException, ExecutionException {
		Assertions.assertEquals(List.of(), db.put(List.<SimpleTable>of(), false).get());
		Assertions.assertEquals(List.of(), db.delete(List.<SimpleTable>of(), false).get());
	}

	@TestDatabase
	void testListDeleteMutuallyLinkedEntities(final Database db) throws InterruptedException, ExecutionException {
		var first = db.put(new SimpleTable("garry")).get();
		var second = db.put(new SimpleTable2("bob")).get();
		db.link(first, second.getClass(), second.getId()).get();

		first = db.get(SimpleTable.class, first.getId()).get();
		second = db.get(SimpleTable2.class, second.getId()).get();

		db.delete(List.<Table>of(first, second), true).get();

		Assertions.assertNull(db.get(SimpleTable.class, first.getId()).get());
		Assertions.assertNull(db.get(SimpleTable2.class, second.getId()).get());
	}

	@TestDatabase
	void testListDeleteValidatesAllLinksBeforeDeleting(final Database db) throws InterruptedException, ExecutionException {
		var unlinked = db.put(new SimpleTable("garry")).get();
		var linked = db.put(new SimpleTable("bob")).get();
		var target = db.put(new SimpleTable2("john")).get();
		db.link(linked, target.getClass(), target.getId()).get();
		var linkedEntry = db.get(SimpleTable.class, linked.getId()).get();

		var delete = Assertions.assertDoesNotThrow(() -> db.delete(List.of(unlinked, linkedEntry), false));
		Assertions.assertThrows(ExecutionException.class, delete::get);

		Assertions.assertNotNull(db.get(SimpleTable.class, unlinked.getId()).get());
		Assertions.assertNotNull(db.get(SimpleTable.class, linked.getId()).get());
		Assertions.assertNotNull(db.get(SimpleTable2.class, target.getId()).get());
	}

	@TestDatabase
	void testGlobalPutGetDelete(@DatabaseNames({ "db" }) final Database db, @DatabaseNames({ "db" }) final Database dbProd) throws InterruptedException,
		ExecutionException {
		SimpleTable entry1 = new SimpleTable("garry");
		entry1 = db.putGlobal(entry1).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertNotNull(entry1.getId());

		String id = entry1.getId();

		entry1 = db.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		entry1 = dbProd.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		db.delete(entry1, false).get();

		// will not actually delete as is in global space
		entry1 = db.get(SimpleTable.class, id).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());
	}

	@TestDatabase
	void testGlobalDisabledPutGetDelete(
		@DatabaseNames({ "db" }) @GlobalEnabled(false) final Database db,
		@DatabaseNames({ "db" }) @GlobalEnabled(false) final Database dbProd
	)
		throws InterruptedException, ExecutionException {
		SimpleTable entry1 = new SimpleTable("garry");
		entry1 = db.putGlobal(entry1).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertNotNull(entry1.getId());

		String id = entry1.getId();

		entry1 = db.get(SimpleTable.class, id).get();

		Assertions.assertNull(entry1);

		entry1 = dbProd.get(SimpleTable.class, id).get();

		Assertions.assertNull(entry1);
	}

	@TestDatabase
	void testClimbingSimplePutGetDelete(
		final @DatabaseNames({ "prod", "stage" }) @DatabaseOrganisation("fixed") Database db,
		@DatabaseNames("prod") @DatabaseOrganisation("fixed") final Database dbProd
	)
		throws InterruptedException, ExecutionException {
		SimpleTable entry1 = new SimpleTable("garry");
		entry1 = dbProd.put(entry1).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertNotNull(entry1.getId());

		String id = entry1.getId();

		entry1 = db.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		db.delete(entry1, false).get();
		entry1 = db.get(SimpleTable.class, id).get();
		Assertions.assertNull(entry1);

		entry1 = dbProd.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		var entry2 = new SimpleTable("two");
		entry2.setId(entry1.getId());
		db.put(entry2).get();

		entry1 = dbProd.get(SimpleTable.class, id).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		entry1 = db.get(SimpleTable.class, id).get();
		Assertions.assertEquals("two", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());
	}

	@TestDatabase
	void testClimbingGlobalPutGetDelete(
		@DatabaseNames({ "prod", "stage" }) @DatabaseOrganisation("fixed") final Database db,
		@DatabaseNames("prod") @DatabaseOrganisation("fixed") final Database dbProd
	)
		throws InterruptedException, ExecutionException {
		SimpleTable entry1 = new SimpleTable("garry");
		entry1 = dbProd.putGlobal(entry1).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertNotNull(entry1.getId());

		String id = entry1.getId();

		entry1 = db.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		// is global so should do nothing
		db.delete(entry1, false).get();
		entry1 = db.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		entry1 = dbProd.get(SimpleTable.class, id).get();

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		var entry2 = new SimpleTable("two");
		entry2.setId(entry1.getId());
		db.put(entry2).get();

		entry1 = dbProd.get(SimpleTable.class, id).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		entry1 = db.get(SimpleTable.class, id).get();
		Assertions.assertEquals("two", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());
	}

	@TestDatabase
	void testTwoOrganisationsPutGetDelete(final Database db, @DatabaseOrganisation("org-777") final Database db2)
		throws InterruptedException, ExecutionException {
		SimpleTable entry1 = new SimpleTable("garry");
		entry1 = db.put(entry1).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertNotNull(entry1.getId());

		String id = entry1.getId();

		entry1 = db.get(SimpleTable.class, id).get();
		Assertions.assertNull(db2.get(SimpleTable.class, id).get());

		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertEquals(id, entry1.getId());

		db.delete(entry1, false).get();
		entry1 = db.get(SimpleTable.class, id).get();
		Assertions.assertNull(entry1);
	}

	@TestDatabase
	void testTwoManagedDatabasesOnSameOrganisationPutGetDelete(final DynamoDbManager dynamoDbManager) throws ExecutionException, InterruptedException {
		final var db = dynamoDbManager.getDatabase("test");
		final var db2 = dynamoDbManager.getDatabase("test");

		final var joPutEntry = db.put(new SimpleTable("jo")).get();
		Assertions.assertEquals("jo", joPutEntry.getName());
		Assertions.assertNotNull(joPutEntry.getId());

		final var joGetEntry = db.get(SimpleTable.class, joPutEntry.getId()).get();
		Assertions.assertNotNull(joGetEntry.getId());

		final var joWasFoundEntry = db2.get(SimpleTable.class, joPutEntry.getId()).get();
		Assertions.assertNotNull(joWasFoundEntry.getId());

		db.delete(joGetEntry, false).get();

		final var joWasDeleted = db.get(SimpleTable.class, joGetEntry.getId()).get();
		Assertions.assertNull(joWasDeleted);
	}

	@TestDatabase
	void testTwoManagedDatabasesPutGetDelete(final DynamoDbManager dynamoDbManager) throws ExecutionException, InterruptedException {
		final var db = dynamoDbManager.getDatabase("test");
		final var db2 = dynamoDbManager.getDatabase("test2");

		final var janePutEntry = db.put(new SimpleTable("jane")).get();
		Assertions.assertEquals("jane", janePutEntry.getName());
		Assertions.assertNotNull(janePutEntry.getId());

		final var janeGetEntry = db.get(SimpleTable.class, janePutEntry.getId()).get();
		Assertions.assertNotNull(janeGetEntry.getId());

		final var janeNotExists = db2.get(SimpleTable.class, janePutEntry.getId()).get();
		Assertions.assertNull(janeNotExists);

		db.delete(janeGetEntry, false).get();

		final var janeWasDeleted = db.get(SimpleTable.class, janeGetEntry.getId()).get();
		Assertions.assertNull(janeWasDeleted);
	}

	@TestDatabase
	void testSameIdDifferentTypes(final Database db) throws InterruptedException, ExecutionException {
		SimpleTable entry1 = new SimpleTable("garry");
		SimpleTable2 entry2 = new SimpleTable2("bob");
		entry1.setId("iamthesame");
		entry2.setId("iamthesame");
		entry1 = db.put(entry1).get();
		entry2 = db.put(entry2).get();
		Assertions.assertEquals("garry", entry1.getName());
		Assertions.assertNotNull(entry1.getId());

		String id = entry1.getId();

		var entry1Future = db.get(SimpleTable.class, id);
		var entry2Future = db.get(SimpleTable2.class, id);

		Assertions.assertEquals("garry", entry1Future.get().getName());
		Assertions.assertEquals("bob", entry2Future.get().getName());
	}

	static class SimpleTable extends Table {

		private String name;

		public SimpleTable() {}

		public SimpleTable(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	static class SimpleTable2 extends Table {

		private String name;

		public SimpleTable2() {}

		public SimpleTable2(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}
}
