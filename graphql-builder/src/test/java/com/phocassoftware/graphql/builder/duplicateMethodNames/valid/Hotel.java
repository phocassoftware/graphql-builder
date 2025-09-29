package com.phocassoftware.graphql.builder.duplicateMethodNames.valid;

import com.phocassoftware.graphql.builder.annotations.GraphQLIgnore;
import com.phocassoftware.graphql.builder.annotations.Query;

public class Hotel {

	@Query
	public static Hotel getHotel() {
		return new Hotel();
	}

	@GraphQLIgnore
	public String getStreetAddress() {
		return "House Name";
	}

	public String getStreetAddress(String append) {
		return getStreetAddress() + append;
	}
}
