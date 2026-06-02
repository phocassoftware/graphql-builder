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
package com.phocassoftware.graphql.builder.nonsealedoutput;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;

// A non-sealed, field-less interface used as an output type. Its @Entity implementations are
// discovered during the scan, so it still produces a union.
public interface Thing {

	@Entity(SchemaOption.TYPE)
	record A(String name) implements Thing {}

	@Entity(SchemaOption.TYPE)
	record B(String name) implements Thing {}
}
