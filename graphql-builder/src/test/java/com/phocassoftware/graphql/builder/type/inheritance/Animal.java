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
package com.phocassoftware.graphql.builder.type.inheritance;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.GraphQLDescription;
import com.phocassoftware.graphql.builder.annotations.Mutation;
import com.phocassoftware.graphql.builder.annotations.OneOf;
import com.phocassoftware.graphql.builder.annotations.Query;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;
import java.util.Arrays;
import java.util.List;

@Entity(SchemaOption.BOTH)
@GraphQLDescription("animal desc")
@OneOf(value = { @OneOf.Type(name = "cat", type = Cat.class), @OneOf.Type(name = "dog", type = Dog.class, description = "A dog") })
public abstract class Animal {

	private String name = "name";

	@GraphQLDescription("the name")
	public String getName() {
		return name;
	}

	@Query
	public static List<Animal> animals() {
		return Arrays.asList(new Cat(), new Dog());
	}

	public void setName(String name) {
		this.name = name;
	}

	@Mutation
	public static List<Animal> myAnimals(List<Animal> animals) {
		return animals;
	}
}
