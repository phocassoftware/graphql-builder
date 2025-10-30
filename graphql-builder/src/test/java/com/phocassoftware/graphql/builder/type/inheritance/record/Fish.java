package com.phocassoftware.graphql.builder.type.inheritance.record;

import com.phocassoftware.graphql.builder.annotations.OneOf;

@OneOf({
	@OneOf.Type(name = "salmon", type = Salmon.class),
	@OneOf.Type(name = "trout", type = Trout.class)
})
public interface Fish {
	String name();
}
