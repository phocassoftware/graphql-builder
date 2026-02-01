package com.phocassoftware.graphql.builder.record;

import java.util.Optional;

public record CatRecord(
    String name,
    String somethingToDoWithCats,
    Optional<String> description
) implements AnimalInterface {
    public AnimalType type() {
        return AnimalType.Cat;
    }
}