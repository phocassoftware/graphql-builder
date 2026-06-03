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
package com.phocassoftware.graphql.builder.nestedunionoutput;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;

// Field-less sealed interface whose permits list contains a nested sealed interface (LandVehicle) as well
// as a concrete type (Boat). The generated union must flatten LandVehicle to its concrete leaves.
public sealed interface Vehicle permits Vehicle.LandVehicle, Vehicle.Boat {

	sealed interface LandVehicle extends Vehicle permits Car, Truck {}

	@Entity(SchemaOption.TYPE)
	record Car(String name) implements LandVehicle {}

	@Entity(SchemaOption.TYPE)
	record Truck(String name) implements LandVehicle {}

	@Entity(SchemaOption.TYPE)
	record Boat(String name) implements Vehicle {}
}
