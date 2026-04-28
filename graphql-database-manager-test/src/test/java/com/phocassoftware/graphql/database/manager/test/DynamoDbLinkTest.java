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
import com.phocassoftware.graphql.database.manager.test.annotations.DatabaseNames;
import com.phocassoftware.graphql.database.manager.test.annotations.DatabaseOrganisation;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;

final class DynamoDbLinkTest {

	@TestDatabase
	void testSimpleQuery(
		@DatabaseNames({ "prod", "stage" }) @DatabaseOrganisation("fixed") final Database db,
		@DatabaseNames("prod") @DatabaseOrganisation("fixed") final Database dbProd
	)
		throws InterruptedException, ExecutionException {
		var garry = db.put(new SimpleTable("garry")).get();
		var john = db.put(new AnotherTable("john")).get();
		var frank = dbProd.put(new SimpleTable("frank")).get();
		var bob = dbProd.put(new AnotherTable("bob")).get();

		db.link(garry, bob.getClass(), bob.getId()).get();
		dbProd.link(frank, bob.getClass(), bob.getId()).get();
		db.link(garry, john.getClass(), john.getId()).get();

		john = db.get(AnotherTable.class, john.getId()).get();
		bob = db.get(AnotherTable.class, bob.getId()).get();

		var johnLink = db.getLink(john, SimpleTable.class).get();
		Assertions.assertEquals("garry", johnLink.name);

		var bobLinks = db.getLinks(bob, SimpleTable.class).get();

		Assertions.assertEquals(1, bobLinks.size());
		bobLinks.sort(Comparator.comparing(a -> a.name));

		Assertions.assertEquals("frank", bobLinks.get(0).name);
	}

	@TestDatabase
	void testDoubleLinkage(
		@DatabaseNames({ "prod", "stage" }) @DatabaseOrganisation("fixed") final Database db,
		@DatabaseNames("prod") @DatabaseOrganisation("fixed") final Database dbProd
	)
		throws InterruptedException, ExecutionException {
		var garry = db.put(new SimpleTable("garry")).get();
		var frank = dbProd.put(new SimpleTable("frank")).get();
		var bob = dbProd.put(new AnotherTable("bob")).get();

		db.link(garry, bob.getClass(), bob.getId()).get();
		dbProd.link(frank, bob.getClass(), bob.getId()).get();

		bob = db.get(AnotherTable.class, bob.getId()).get();

		var bobLinks = db.getLinks(bob, SimpleTable.class).get();

		Assertions.assertEquals(2, bobLinks.size());
		bobLinks.sort(Comparator.comparing(a -> a.name));

		Assertions.assertEquals("frank", bobLinks.get(0).name);
		Assertions.assertEquals("garry", bobLinks.get(1).name);
	}

	@TestDatabase
	void testUpdate(final Database db) throws InterruptedException, ExecutionException {
		var garry = db.put(new SimpleTable("garry")).get();
		var john = db.put(new AnotherTable("john")).get();
		var bob = db.put(new AnotherTable("bob")).get();

		db.link(garry, john.getClass(), john.getId()).get();
		db.link(garry, bob.getClass(), bob.getId()).get();

		garry = db.get(SimpleTable.class, garry.getId()).get();
		john = db.get(AnotherTable.class, john.getId()).get();
		bob = db.get(AnotherTable.class, bob.getId()).get();

		var bobLinks = db.getLink(bob, SimpleTable.class).get();
		Assertions.assertEquals("garry", bobLinks.name);

		var garryLink = db.getLink(garry, AnotherTable.class).get();
		Assertions.assertEquals("bob", garryLink.getName());

		var johnLink = db.getLinks(john, SimpleTable.class).get();
		Assertions.assertEquals(0, johnLink.size());
	}

	@TestDatabase
	void testDelete(final Database db) throws InterruptedException, ExecutionException {
		var garry = db.put(new SimpleTable("garry")).get();
		var john = db.put(new AnotherTable("john")).get();

		db.link(garry, john.getClass(), john.getId()).get();

		Assertions
			.assertThrows(
				RuntimeException.class,
				() -> {
					db.delete(garry, false).get();
				}
			);

		db.delete(garry, true).get();

		var list = db.getLinks(john, SimpleTable.class).get();
		Assertions.assertEquals(0, list.size());
	}

	@TestDatabase
	void testDeleteLinks(final Database db) throws InterruptedException, ExecutionException {
		var garry = db.put(new SimpleTable("garry")).get();
		var john = db.put(new AnotherTable("john")).get();

		garry = db.link(garry, john.getClass(), john.getId()).get();

		garry = db.deleteLinks(garry).get();

		garry = db.get(SimpleTable.class, garry.getId()).get();

		var list = db.getLinks(john, SimpleTable.class).get();
		Assertions.assertEquals(0, list.size());

		var list2 = db.getLinks(garry, AnotherTable.class).get();
		Assertions.assertEquals(0, list2.size());
	}

	@TestDatabase
	void testLinkingBetweenMultiOrganisations(@DatabaseOrganisation("bestorg") final Database db0, @DatabaseOrganisation("amazingorg") final Database db1)
		throws ExecutionException, InterruptedException {
		final var putAlexBestOrg = db0.put(new SimpleTable("alex")).get();
		Assertions.assertNotNull(db0.get(SimpleTable.class, putAlexBestOrg.getId()).get());

		final var putPineappleAmazingOrg = db1.put(new AnotherTable("pineapple")).get();
		Assertions.assertNotNull(db1.get(AnotherTable.class, putPineappleAmazingOrg.getId()).get());

		db0.link(putAlexBestOrg, putPineappleAmazingOrg.getClass(), putPineappleAmazingOrg.getId()).get();
		Assertions.assertTrue(db0.getLinks(putAlexBestOrg, AnotherTable.class).get().isEmpty());
	}

	@TestDatabase
	void testLinkAfterNonConditionalPut(final Database db) throws InterruptedException, ExecutionException {
		// put(entity, false) writes items without a links attribute (buildPutEntity skips empty links).
		// Subsequent link() must handle the missing attribute via its fallback paths.
		var garry = new SimpleTable("garry");
		garry.setRevision(0);
		garry = db.put(garry, false).get();

		var john = new AnotherTable("john");
		john.setRevision(0);
		john = db.put(john, false).get();

		// updateEntityLinks updates garry (source), addLinks updates john (target) — both lack links attribute
		db.link(garry, john.getClass(), john.getId()).get();

		john = db.get(AnotherTable.class, john.getId()).get();
		var johnLink = db.getLink(john, SimpleTable.class).get();
		Assertions.assertEquals("garry", johnLink.name);
	}

	@TestDatabase
	void testLinkToOverwrittenEntity(final Database db) throws InterruptedException, ExecutionException {
		// Overwriting an existing linked entity with put(entity, false) removes its links attribute.
		// link() to that overwritten entity must handle the missing links on the target side (addLinks fallback).
		var dashboard = db.put(new SimpleTable("dashboard")).get();
		var tab = db.put(new AnotherTable("tab")).get();
		db.link(dashboard, tab.getClass(), tab.getId()).get();

		// Overwrite dashboard — the replacement item has no links attribute
		var restored = new SimpleTable("dashboard");
		restored.setId(dashboard.getId());
		restored.setRevision(0);
		restored = db.put(restored, false).get();

		// New entity linking TO the overwritten dashboard
		var newTab = db.put(new AnotherTable("newTab")).get();
		db.link(newTab, restored.getClass(), restored.getId()).get();

		restored = db.get(SimpleTable.class, restored.getId()).get();
		var links = db.getLinks(restored, AnotherTable.class).get();
		Assertions.assertEquals(1, links.size());
		Assertions.assertEquals("newTab", links.get(0).getName());
	}

	@TestDatabase
	void testRelinkAfterNonConditionalPut(final Database db) throws InterruptedException, ExecutionException {
		// When an entity is linked, then the target is overwritten via put(entity, false), re-linking
		// with different IDs triggers removeLinks on the overwritten target (which has no links attribute).
		// removeLinks must not fail when the target item lacks a links attribute.
		var garry = db.put(new SimpleTable("garry")).get();
		var bob = db.put(new AnotherTable("bob")).get();
		var frank = db.put(new AnotherTable("frank")).get();

		garry = db.link(garry, AnotherTable.class, bob.getId()).get();

		// Overwrite bob — loses its links attribute
		var restoredBob = new AnotherTable("bob");
		restoredBob.setId(bob.getId());
		restoredBob.setRevision(0);
		db.put(restoredBob, false).get();

		// Re-link garry to frank instead of bob — triggers removeLinks on bob (no links attr)
		garry = db.get(SimpleTable.class, garry.getId()).get();
		garry = db.link(garry, AnotherTable.class, frank.getId()).get();

		garry = db.get(SimpleTable.class, garry.getId()).get();
		var garryLinks = db.getLinks(garry, AnotherTable.class).get();
		Assertions.assertEquals(1, garryLinks.size());
		Assertions.assertEquals("frank", garryLinks.get(0).getName());
	}

	@TestDatabase
	void unlink(final Database db) throws InterruptedException, ExecutionException {
		var garry = db.put(new SimpleTable("garry")).get();
		var bob = db.put(new AnotherTable("bob")).get();

		db.link(garry, bob.getClass(), bob.getId()).get();

		garry = db.get(SimpleTable.class, garry.getId()).get();
		bob = db.get(AnotherTable.class, bob.getId()).get();

		var bobLinks = db.getLink(bob, SimpleTable.class).get();
		Assertions.assertEquals("garry", bobLinks.name);

		var garryLink = db.getLink(garry, AnotherTable.class).get();
		Assertions.assertEquals("bob", garryLink.getName());

		db.unlink(garry, AnotherTable.class, bob.getId()).get();

		var unlinked = db.getLink(garry, AnotherTable.class).get();
		Assertions.assertNull(unlinked);
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

	static class AnotherTable extends Table {

		private String name;

		public AnotherTable() {}

		public AnotherTable(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}
}
