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
package com.phocassoftware.graphql.builder.exceptions;

import java.util.Arrays;

public class DuplicateMethodNameException extends RuntimeException {

	private static final String MESSAGE_TEMPLATE = "Class: %s has overloaded method(s): %s. GraphQLBuilder does not support overloading methods";

	public DuplicateMethodNameException(String typeName, String... methodNames) {
		super(MESSAGE_TEMPLATE.formatted(typeName, Arrays.toString(methodNames)));
	}
}
