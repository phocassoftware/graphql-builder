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
package com.phocassoftware.graphql.database.manager.test;

import tools.jackson.annotation.JsonAutoDetect.Visibility;
import tools.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import java.util.function.Supplier;

public class ObjectMapperCreator implements Supplier<ObjectMapper> {

	@Override
	public ObjectMapper get() {
		return new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
			.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
			.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
	}
}
