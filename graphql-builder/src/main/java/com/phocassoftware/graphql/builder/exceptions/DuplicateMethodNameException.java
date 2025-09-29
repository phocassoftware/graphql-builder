package com.phocassoftware.graphql.builder.exceptions;

import java.util.Arrays;

public class DuplicateMethodNameException extends RuntimeException {

	private static final String MESSAGE_TEMPLATE = "Class: %s has overloaded method(s): %s. GraphQLBuilder does not support overloading methods";

	public DuplicateMethodNameException(String typeName, String... methodNames) {
		super(MESSAGE_TEMPLATE.formatted(typeName, Arrays.toString(methodNames)));
	}
}
