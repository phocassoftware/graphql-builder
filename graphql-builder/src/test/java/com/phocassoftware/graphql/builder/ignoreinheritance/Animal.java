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
package com.phocassoftware.graphql.builder.ignoreinheritance;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.GraphQLField;
import com.phocassoftware.graphql.builder.annotations.GraphQLIgnore;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;

public sealed interface Animal {

	String name();

	@GraphQLIgnore
	String secret();

	// Inherits the @GraphQLIgnore on secret() from the interface, so secret is hidden.
	@Entity(SchemaOption.TYPE)
	record Dog(String name, String secret) implements Animal {}

	// Overrules the inherited @GraphQLIgnore with @GraphQLField, so secret is exposed.
	@Entity(SchemaOption.TYPE)
	record Cat(String name, String secret) implements Animal {

		@GraphQLField
		@Override
		public String secret() {
			return secret;
		}
	}
}
