package com.bookmap.plugins.layer0.bitmex.adapter;

import java.lang.reflect.Type;

public class TopicContainer {

	public final String name;
	public final boolean isAuthNeeded;
	public final Type unitType;
	public final Class<?> clazz;
	
	public TopicContainer(String name, boolean isAuthNeeded, Type unitType, Class<?> clazz) {
		super();
		this.name = name;
		this.isAuthNeeded = isAuthNeeded;
		this.unitType = unitType;
		this.clazz = clazz;
	}
}
