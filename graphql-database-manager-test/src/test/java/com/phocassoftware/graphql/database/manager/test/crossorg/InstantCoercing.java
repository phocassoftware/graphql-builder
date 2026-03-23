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
package com.phocassoftware.graphql.database.manager.test.crossorg;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.*;

import java.time.Instant;
import java.util.Locale;

public class InstantCoercing implements Coercing<Instant, Instant> {
	public static final GraphQLScalarType INSTANCE = new GraphQLScalarType.Builder().name("Instant").coercing(new InstantCoercing()).build();

	@Override
	public Instant serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale)
		throws CoercingSerializeException {
		return convertImpl(dataFetcherResult);
	}

	@Override
	public Instant parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
		return convertImpl(input);
	}

	@Override
	public Instant parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
		return convertImpl(input);
	}

	private Instant convertImpl(Object input) {
		if (input instanceof Instant instant) {
			return instant;
		} else if (input instanceof StringValue sv) {
			return Instant.parse(sv.getValue());
		} else if (input instanceof String string) {
			return Instant.parse(string);
		}
		if (input instanceof Long milli) {
			return Instant.ofEpochMilli(milli);
		}
		return null;
	}
}
