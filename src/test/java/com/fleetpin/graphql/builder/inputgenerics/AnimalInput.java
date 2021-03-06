package com.fleetpin.graphql.builder.inputgenerics;

import com.fleetpin.graphql.builder.annotations.Entity;
import com.fleetpin.graphql.builder.annotations.SchemaOption;

@Entity(SchemaOption.INPUT)
public class AnimalInput<T extends Animal> {
	String id;
	T animal;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public T getAnimal() {
		return animal;
	}

	public void setAnimal(T animal) {
		this.animal = animal;
	}

}