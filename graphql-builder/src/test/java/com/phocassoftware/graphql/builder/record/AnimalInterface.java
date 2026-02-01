package com.phocassoftware.graphql.builder.record;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonSubTypes({
	@JsonSubTypes.Type(value = CatRecord.class, name = "Cat"),
	@JsonSubTypes.Type(value = DogRecord.class, name = "Dog"),
})
public sealed interface AnimalInterface
	permits CatRecord, DogRecord {

	String name();

	Optional<String> description();

	AnimalType type();

	enum AnimalType {
		@JsonProperty("Cat")
		Cat,
		@JsonProperty("Dog")
		Dog
	}
}
