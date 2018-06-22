package bitmexAdapter;

import java.lang.reflect.Type;

public class TopicContainer {

	public final String name;
	public final boolean isAuthNeeded;
//	public final Type type;
	public final Type unitType;
	public final Class cls;
	
	public TopicContainer(String name, boolean isAuthNeeded, Type unitType, Class cls) {
//		public TopicContainer(String name, boolean isAuthNeeded, Type type, Type unitType) {
		super();
		this.name = name;
		this.isAuthNeeded = isAuthNeeded;
//		this.type = type;
		this.unitType = unitType;
		this.cls = cls;
	}
	
	
	
	
	
	
	
	
}
