package com.phocassoftware.graphql.builder.scalartest;

import java.time.ZoneId;

public record TimedWidget(String name, ZoneId timeZone) implements Widget {}
