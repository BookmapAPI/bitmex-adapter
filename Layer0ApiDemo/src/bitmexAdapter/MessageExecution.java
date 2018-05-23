package bitmexAdapter;

import java.util.ArrayList;

public class MessageExecution {
	public String table;
	public String action;
	public ArrayList<BmOrder> data = new ArrayList<>();

	public MessageExecution(String table, String action, ArrayList<BmOrder> data) {
		super();
		this.table = table;
		this.action = action;
		this.data = data;
	}
	
	public MessageExecution() {
		super();
	}

	public String getTable() {
		return table;
	}

	public void setTable(String table) {
		this.table = table;
	}
	
	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public void setData(ArrayList<BmOrder> data) {
		this.data = data;
	}

	public ArrayList<BmOrder> getData() {
		return data;
	}

	@Override
	public String toString() {
		return "Message [table=" + table + ", action=" + action + ", data=" + data.toString() + "]";
	}

}
