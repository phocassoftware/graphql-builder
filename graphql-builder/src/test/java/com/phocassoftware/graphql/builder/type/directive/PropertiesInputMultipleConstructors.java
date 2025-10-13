package com.phocassoftware.graphql.builder.type.directive;

import com.phocassoftware.graphql.builder.annotations.Entity;
import com.phocassoftware.graphql.builder.annotations.SchemaOption;
import jakarta.validation.constraints.Size;

@Entity(SchemaOption.INPUT)
public class PropertiesInputMultipleConstructors {
	@Size(min = 3)
	String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public PropertiesInputMultipleConstructors(String name) {
		this.name = name;
	}

	public PropertiesInputMultipleConstructors() {
		this.name = "Default";
	}
}
