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
package com.phocassoftware.graphql.builder.context;

import com.phocassoftware.graphql.builder.annotations.Context;
import com.phocassoftware.graphql.builder.annotations.Query;
import graphql.GraphQLContext;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;

public class Queries {

	@Query
	public static boolean entireContext(GraphQLContext context) {
		return context != null;
	}

	@Query
	public static boolean env(DataFetchingEnvironment context) {
		return context != null;
	}

	@Query
	public static boolean deprecatedContext(GraphContext context) {
		return context != null;
	}

	@Query
	public static boolean namedContext(GraphContext named) {
		return named != null;
	}

	@Query
	public static boolean namedParemeterContext(@Context String context) {
		return context != null;
	}

	@Query
	public static boolean missingContext(GraphContext notPresent) {
		return false;
	}

	@Query
	public static DataFetcherResult<Person> localContext(GraphContext context) {
		return DataFetcherResult.<Person>newResult().data(new Person()).localContext(new GraphContext("John")).build();
	}

	public static class Person {

		public String getName(GraphContext context) {
			return context.getSomething();
		}

	}
}
