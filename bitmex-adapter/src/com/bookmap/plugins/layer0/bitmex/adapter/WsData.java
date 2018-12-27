package com.bookmap.plugins.layer0.bitmex.adapter;

import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.WebSocketOperation;

public class WsData {
	@SuppressWarnings("unused")
	private String op;
	@SuppressWarnings("unused")
	private Object[] args;

	public WsData(WebSocketOperation op, Object... args) {
		super();
		this.op = ConnectorUtils.webSocketOperationToString.get(op);
		this.args = args;
	}
	
//	this constructor is used for topics that involve instrument Symbol only
	public WsData(String symbol, WebSocketOperation op, Object... args) {
		super();
		this.op = ConnectorUtils.webSocketOperationToString.get(op);
		this.args = processArgs(symbol, args);
	}
	
	private Object[] processArgs(String symbol, Object[] args){
		Object[] newArgs = new Object[args.length];
		for(int i = 0, n = args.length; i < n; i++){
			StringBuilder sb = new StringBuilder();
			sb.append(args[i]).append(":").append(symbol);
			newArgs[i] = sb.toString();
		}
		return newArgs;
	}
}
