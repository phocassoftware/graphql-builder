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
package com.phocassoftware.graphql.builder.oneofoutput;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.OneOf;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;

@OneOf({
	@OneOf.Type(type = Shape.Circle.class, name = "circle"),
	@OneOf.Type(type = Shape.Square.class, name = "square"),
})
public sealed interface Shape {

	@Entity(SchemaOption.BOTH)
	record Circle(double radius) implements Shape {}

	@Entity(SchemaOption.BOTH)
	record Square(double side) implements Shape {}
}
