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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.phocassoftware.graphql.builder.DirectiveCaller;
import com.phocassoftware.graphql.builder.annotations.DataFetcherWrapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@DataFetcherWrapper(Admin.Processor.class)
@Retention(RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Admin {
	String value();

	Class<?>[] toIgnore() default {};
	

	static class Processor implements DirectiveCaller<Admin> {

		@Override
		public Object process(Admin annotation, DataFetchingEnvironment env, DataFetcher<?> fetcher) throws Exception {
			if (env.getArgument("name").equals(annotation.value())) {
				return fetcher.get(env);
			}
			throw new RuntimeException("forbidden");
		}
	}
}
