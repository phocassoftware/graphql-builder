package com.phocassoftware.graphql.builder.type.inheritance.record;

import com.phocassoftware.graphql.builder.annotations.Query;

import java.util.List;

public class FishFinder {

	@Query
	public static List<Fish> findFish() {
		return List.of(
			new Salmon("Steve"),
			new Trout("Trevor")
		);
	}
}
