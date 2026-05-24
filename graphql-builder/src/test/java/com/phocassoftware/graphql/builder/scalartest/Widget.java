package com.phocassoftware.graphql.builder.scalartest;

import com.phocassoftware.graphql.builder.annotations.GraphQLDescription;
import com.phocassoftware.graphql.builder.annotations.OneOf;
import com.phocassoftware.graphql.builder.annotations.Query;

import java.util.List;

@OneOf({
	@OneOf.Type(name = "timedWidget", type = TimedWidget.class),
	@OneOf.Type(name = "simpleWidget", type = SimpleWidget.class),
})
@GraphQLDescription("A widget.")
public sealed interface Widget permits TimedWidget, SimpleWidget {

	@Query
	static List<Widget> widgets() {
		return List.of(new SimpleWidget("hello"), new TimedWidget("world", java.time.ZoneId.of("UTC")));
	}
}
