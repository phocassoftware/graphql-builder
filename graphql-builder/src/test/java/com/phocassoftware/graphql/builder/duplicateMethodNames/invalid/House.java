package com.phocassoftware.graphql.builder.duplicateMethodNames.invalid;

import com.phocassoftware.graphql.builder.annotations.Query;

public class House {

	@Query
	public static House getHouse() {
		return new House();
	}

	public String getStreetAddress() {
		return "House Name";
	}

	public String getStreetAddress(String append) {
		return getStreetAddress() + append;
	}
}
