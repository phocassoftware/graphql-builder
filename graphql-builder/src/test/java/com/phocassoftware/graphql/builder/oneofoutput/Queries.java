package com.phocassoftware.graphql.builder.oneofoutput;

import com.phocassoftware.graphql.builder.annotations.Mutation;
import com.phocassoftware.graphql.builder.annotations.Query;

public class Queries {

	@Query
	public static Shape getShape() {
		return new Shape.Circle(5.0);
	}

	@Mutation
	public static Shape putShape(Shape shape) {
		return shape;
	}
}
