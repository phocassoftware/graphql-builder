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
package com.phocassoftware.graphql.builder.type.directive;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.Mutation;
import com.phocassoftware.graphql.builder.annotations.Query;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Entity
public class Cat {

	public boolean isCalico() {
		return true;
	}

	public int getAge() {
		return 3;
	}

	public boolean getFur() {
		return true;
	}

	@Query
	@Capture(color = "meow")
	public static Cat getCat() {
		return new Cat();
	}

	@Query
	@Uppercase
	public static Cat getUpper() {
		return new Cat();
	}

	@Mutation
	public static String setName(@Size(min = 3) String name) {
		return name;
	}

	@Mutation
	public static int setAge(@Min(value = 3) @Max(value = 99) int age) {
		return age;
	}

	@Query
	@Admin("tabby")
	public static String allowed(String name) {
		return name;
	}

	@Query
	public static String getNickname(@Input("TT") String nickName) {
		return nickName;
	}
}
