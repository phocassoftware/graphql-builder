package com.fleetpin.graphql.builder.type.inheritance;

import com.fleetpin.graphql.builder.annotations.Entity;
import com.fleetpin.graphql.builder.annotations.Mutation;

@Entity
public class Dog extends Animal{

	public int getAge() {
		return 6;
	}
	public String getFur() {
		return "shaggy";
	}
	
	@Mutation
	public static Dog getDog() {
		return null;
	}
}
