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
import com.phocassoftware.graphql.builder.annotations.SchemaOption;

// Overrules the inherited @GraphQLIgnore on getInternal() with @GraphQLField, so internal is exposed.
@Entity(SchemaOption.TYPE)
public class Bike extends Base {

	@GraphQLField
	@Override
	public String getInternal() {
		return "shown";
	}

	public String getModel() {
		return "model";
	}
}
