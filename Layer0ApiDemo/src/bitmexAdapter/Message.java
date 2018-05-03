package bitmexAdapter;

import java.util.ArrayList;

public class Message {
	public String table;
	public String action;
	public ArrayList<DataUnit> data = new ArrayList<>();

	public Message(String table, String action, ArrayList<DataUnit> data) {
		super();
		this.table = table;
		this.action = action;
		this.data = data;
	}
	
	public Message() {
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

	public void setData(ArrayList<DataUnit> data) {
		this.data = data;
	}

	public ArrayList<DataUnit> getData() {
		return data;
	}

	@Override
	public String toString() {
		return "Message [table=" + table + ", action=" + action + ", data=" + data.toString() + "]";
	}

}
