package com.phocassoftware.graphql.builder.oneofoutput;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.OneOf;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;

@OneOf({
	@OneOf.Type(type = Shape.Circle.class, name = "circle"),
	@OneOf.Type(type = Shape.Square.class, name = "square"),
})
public sealed interface Shape {

	@Entity(SchemaOption.BOTH)
	record Circle(double radius) implements Shape {}

	@Entity(SchemaOption.BOTH)
	record Square(double side) implements Shape {}
}
