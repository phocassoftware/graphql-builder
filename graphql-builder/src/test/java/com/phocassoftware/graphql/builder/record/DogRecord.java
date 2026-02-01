package com.phocassoftware.graphql.builder.record;

import java.util.Optional;

public record DogRecord(
	String name,
	String somethingToDoWithDogs,
	Optional<String> description
) implements AnimalInterface {
	@Override
	public AnimalType type() {
		return AnimalType.Dog;
	}
}