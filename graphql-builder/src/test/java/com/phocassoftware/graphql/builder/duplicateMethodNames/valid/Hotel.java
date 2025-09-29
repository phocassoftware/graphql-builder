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
package com.phocassoftware.graphql.builder.duplicateMethodNames.valid;

import com.phocassoftware.graphql.builder.annotations.GraphQLIgnore;
import com.phocassoftware.graphql.builder.annotations.Query;

public class Hotel {

	@Query
	public static Hotel getHotel() {
		return new Hotel();
	}

	@GraphQLIgnore
	public String getStreetAddress() {
		return "House Name";
	}

	public String getStreetAddress(String append) {
		return getStreetAddress() + append;
	}
}
