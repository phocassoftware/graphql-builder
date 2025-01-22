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
package com.phocassoftware.graphql.builder.inputgenerics;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;

@Entity(SchemaOption.BOTH)
public class AnimalOuterWrapper<T extends Animal> {

	String id;
	AnimalWrapper<T> animal;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public AnimalWrapper<T> getAnimal() {
		return animal;
	}

	public void setAnimal(AnimalWrapper<T> animal) {
		this.animal = animal;
	}
}
