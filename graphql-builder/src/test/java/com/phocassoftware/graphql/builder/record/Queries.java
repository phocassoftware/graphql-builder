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
package com.phocassoftware.graphql.builder.record;

import com.phocassoftware.graphql.builder.annotations.GraphQLDescription;
import com.phocassoftware.graphql.builder.annotations.InnerNullable;
import com.phocassoftware.graphql.builder.annotations.Query;
import com.phocassoftware.graphql.builder.annotations.Union;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public class Queries {

	@Query
	public static InputType passthrough(InputType type) {
		return type;
	}

	@Query
	@Nullable
	public static Boolean nullableTest(@Nullable Boolean type) {
		return type;
	}

	@Query
	@Nullable
	public static List<Boolean> nullableArrayTest(@Nullable List<Boolean> type) {
		return type;
	}

	@Query
	@InnerNullable
	public static List<Boolean> innerNullableArrayTest(@InnerNullable List<Boolean> type) {
		return type;
	}

	@Query
	public static List<Optional<Boolean>> nullableInnerArrayTest(List<Optional<Boolean>> type) {
		return type;
	}

	@Query
	@Union({ DogRecord.class, CatRecord.class })
	public static List<AnimalInterface> recordInheritanceTest() {
		return List.of(new DogRecord("Lola", "LikesBones", Optional.of("fluffy")), new CatRecord("Mavi", "LikesCheese", Optional.of("Angry")));
	}

	@GraphQLDescription("record Type")
	static final record InputType(
		@GraphQLDescription("the name") String name,
	  	int age,
	  	Optional<Integer> weight) {}
}
