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
package com.phocassoftware.graphql.builder.scalar;

import com.phocassoftware.graphql.builder.annotations.Scalar;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

@Scalar(Shape.ShapeCoercing.class)
@Capture
public class Shape {

	private final String input;

	public Shape(String input) {
		this.input = input;
	}

	public String getInput() {
		return input;
	}

	public static class ShapeCoercing implements Coercing<Shape, Shape> {

		@Override
		public Shape serialize(Object dataFetcherResult) throws CoercingSerializeException {
			return convertImpl(dataFetcherResult);
		}

		@Override
		public Shape parseValue(Object input) throws CoercingParseValueException {
			return convertImpl(input);
		}

		@Override
		public Shape parseLiteral(Object input) throws CoercingParseLiteralException {
			return convertImpl(input);
		}

		private Shape convertImpl(Object input) {
			if (input instanceof Shape) {
				return (Shape) input;
			} else if (input instanceof String) {
				return new Shape((String) input);
			}
			throw new CoercingParseLiteralException();
		}
	}
}
