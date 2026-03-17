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

package com.phocassoftware.graphql.database.manager.test.hashed;

import com.phocassoftware.graphql.database.manager.Database;
import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.annotations.Hash;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;

final class DynamoDbQueryBuilderTest {

	@Hash(SimplerHasher.class)
	static class Ticket extends Table {

		private String value;

		public Ticket(String id, String value) {
			setId(id);
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return "Ticket{" + "id='" + getId() + '\'' + ", value='" + value + '\'' + '}';
		}
	}

	static class BigData extends Table {

		private String name;
		private Double[][] matrix;

		public BigData(String id, String name, Double[][] matrix) {
			setId(id);
			this.name = name;
			this.matrix = matrix;
		}
	}

	private Double[][] createMatrix(Integer size) {
		Double[][] m = new Double[size][size];
		Random r = new Random();
		Double k = r.nextDouble();
		m[0][0] = r.nextDouble();
		for (int i = 0; i < m.length; i++) {
			for (int j = 0; j < m[i].length; j++) {
				if (i == 0 && j == 0) continue;
				else if (j == 0) {
					m[i][j] = m[i - 1][m[i - 1].length - 1] + k;
				} else m[i][j] = m[i][j - 1] + k;
			}
		}

		return m;
	}

	private void swallow(CompletableFuture<?> f) {
		try {
			f.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException();
		}
	}

	@TestDatabase
	void testAfter(final Database db) throws InterruptedException, ExecutionException {
		db.put(new Ticket("budgetId1:sales;trinkets:2020/10", "6 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2020/11", "7 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2020/12", "8 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2021/01", "9 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2021/02", "10 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets;whatchamacallits:2020/10", "11 whatchamacallits")).get();
		db.put(new Ticket("budgetId1:sales;trinkets;whatchamacallits:2020/11", "12 whatchamacallits")).get();
		db.put(new Ticket("budgetId1:sales;trinkets;whatchamacallits:2020/12", "13 whatchamacallits")).get();
		db.put(new Ticket("budgetId2:usa;expenses;flights;domestic:2020/10", "1 million dollars")).get();
		db.put(new Ticket("budgetId1:sales;widgets:2020/10", "1 widgets")).get();
		db.put(new Ticket("budgetId1:sales;widgets:2020/11", "2 widgets")).get();
		db.put(new Ticket("budgetId1:sales;widgets:2020/12", "3 widgets")).get();
		db.put(new Ticket("budgetId1:sales;widgets:2021/01", "4 widgets")).get();
		db.put(new Ticket("budgetId1:sales;widgets:2021/02", "5 widgets")).get();

		var result2 = db.query(Ticket.class, builder -> builder.startsWith("budgetId1:").after("budgetId1:sales;trinkets:2020/10").limit(10)).get();
		Assertions.assertEquals("budgetId1:sales;trinkets:2020/11", result2.get(0).getId());
		Assertions.assertEquals(10, result2.size());
	}

	static String getId(int i) {
		return String.format("%04d", i);
	}

	@TestDatabase
	void parallelQuery(final Database db) throws InterruptedException, ExecutionException {
		var n = 20;
		List<String> ids = Stream.iterate(1, i -> i + 1).map(i -> getId(i)).limit(n).collect(Collectors.toList());

		var l = Stream
			.iterate(1, i -> i + 1)
			.limit(n)
			// Must pick a sufficiently sized matrix in order to force multiple pages to test limit, 100 works well
			.map(i -> new BigData(ids.get(i - 1), "bigdata-" + i.toString(), createMatrix(100)))
			.collect(Collectors.toList());

		l.stream().map(db::put).forEach(this::swallow);

		var allItems = db.query(BigData.class, builder -> builder).get();
		var result1 = db.query(BigData.class, builder -> builder.threadCount(2).threadIndex(0)).get();
		var result2 = db.query(BigData.class, builder -> builder.threadCount(2).threadIndex(1)).get();

		Assertions.assertEquals(20, allItems.size());
		Assertions.assertEquals(20, result1.size() + result2.size());
		Assertions.assertNotEquals(20, result1.size());
		Assertions.assertNotEquals(20, result2.size());

		var allItemsPage = Stream.concat(result1.stream(), result2.stream()).map(s -> s.name).collect(Collectors.toList());
		Assertions.assertEquals(true, allItems.stream().map(s -> s.name).collect(Collectors.toList()).containsAll(allItemsPage));
	}

	@TestDatabase
	void parallelPagingQuery(final Database db) throws InterruptedException, ExecutionException {
		var n = 20;
		List<String> ids = Stream.iterate(1, i -> i + 1).map(i -> getId(i)).limit(n).collect(Collectors.toList());

		var l = Stream
			.iterate(1, i -> i + 1)
			.limit(n)
			// Must pick a sufficiently sized matrix in order to force multiple pages to test limit, 100 works well
			.map(i -> new BigData(ids.get(i - 1), "bigdata-" + i.toString(), createMatrix(100)))
			.collect(Collectors.toList());

		l.stream().map(db::put).forEach(this::swallow);

		var allItems = db.query(BigData.class, builder -> builder).get();
		var result1Page1 = db.query(BigData.class, builder -> builder.threadCount(2).threadIndex(0).limit(5)).get();
		var result2Page2 = db.query(BigData.class, builder -> builder.threadCount(2).threadIndex(1).limit(5)).get();

		Assertions.assertEquals(20, allItems.size());

		var lastPageIndex1 = result1Page1.get(result1Page1.size() - 1).getId();
		var result1Page3 = db.query(BigData.class, builder -> builder.threadCount(2).after(lastPageIndex1).threadIndex(0).limit(5)).get();

		var lastPageIndex2 = result2Page2.get(result2Page2.size() - 1).getId();
		var result2Page4 = db.query(BigData.class, builder -> builder.threadCount(2).after(lastPageIndex2).threadIndex(1).limit(5)).get();

		var firstSide = Stream.concat(result1Page1.stream(), result2Page2.stream());
		var secondSide = Stream.concat(result1Page3.stream(), result2Page4.stream());
		var allItemsPage = Stream.concat(firstSide, secondSide).map(s -> s.name).collect(Collectors.toList());

		Assertions.assertTrue(allItems.stream().map(s -> s.name).collect(Collectors.toList()).containsAll(allItemsPage));
	}

	@TestDatabase
	void testScanIndexForward(final Database db) throws InterruptedException, ExecutionException {
		db.put(new Ticket("budgetId1:sales;trinkets:2020/10", "6 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2020/11", "7 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2020/12", "8 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2021/01", "9 trinkets")).get();
		db.put(new Ticket("budgetId1:sales;trinkets:2021/02", "10 trinkets")).get();

		var forward = db.query(Ticket.class, builder -> builder.startsWith("budgetId1:").scanIndexForward(true)).get();
		Assertions.assertEquals("budgetId1:sales;trinkets:2020/10", forward.get(0).getId());
		Assertions.assertEquals("budgetId1:sales;trinkets:2021/02", forward.get(forward.size() - 1).getId());

		var reverse = db.query(Ticket.class, builder -> builder.startsWith("budgetId1:").scanIndexForward(false)).get();
		Assertions.assertEquals("budgetId1:sales;trinkets:2021/02", reverse.get(0).getId());
		Assertions.assertEquals("budgetId1:sales;trinkets:2020/10", reverse.get(reverse.size() - 1).getId());
	}
}
