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
package com.phocassoftware.graphql.builder.ignore;

import com.phocassoftware.graphql.builder.annotations.GraphQLIgnore;
import com.phocassoftware.graphql.builder.annotations.Query;

public class Queries {

	@Query
	public static Person person() {
		return new Person("John", 30, "Engineer");
	}

	public static class Person {

		@GraphQLIgnore
		private final String name;
		private final int age;
		@GraphQLIgnore
		private final String occupation;

		public Person(String name, int age, String occupation) {
			super();
			this.name = name;
			this.age = age;
			this.occupation = occupation;
		}

		public String getName() {
			return name;
		}

		public int getAge() {
			return age;
		}

		public boolean getOccupation() {
			return true;
		}
	}

}
