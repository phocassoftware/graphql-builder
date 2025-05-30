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

package com.phocassoftware.graphql.database.manager.test.annotations;

import java.lang.annotation.*;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phocassoftware.graphql.database.manager.test.TestDatabaseProvider;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Test
@ExtendWith(TestDatabaseProvider.class)
public @interface TestDatabase {
	boolean hashed() default false;

	String classPath() default "";

	Class<? extends Supplier<ObjectMapper>> objectMapper();

	Class<? extends ProviderFunction<?>>[] providers() default {};
}
