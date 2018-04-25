package bitmexAdapter;

import java.util.Arrays;
import java.util.LinkedList;

import com.google.gson.annotations.SerializedName;

public class Query {
	
	public enum Subscription {
		subscribe, unsubscribe;
	}

    @SerializedName("op")
	private String operation;
	private LinkedList<QueryArgument> args = new LinkedList<>();
	
	public Query(String operation, QueryArgument arg) {
		super();
		this.operation = operation;
		this.args.add(arg);
	}
	
	public Query(String operation, QueryArgument... args) {
		super();
		this.operation = operation;
		this.args.addAll(Arrays.asList(args));
	}

	public String getOperation() {
		return operation;
	}

	
		
		
	

}
