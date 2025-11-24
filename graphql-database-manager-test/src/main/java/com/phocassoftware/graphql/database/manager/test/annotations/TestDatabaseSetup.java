package com.phocassoftware.graphql.database.manager.test.annotations;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface TestDatabaseSetup {

	default List<? extends ProviderFunction<?>> providers() {
		return List.of();
	}

	String classPath();

	boolean hashed();

	ObjectMapper objectMapper();

}
