package bitmexAdapter;

import java.util.ArrayList;

public class MessagePosition {
	public String table;
	public String action;
	public ArrayList<Position> data = new ArrayList<>();

	public MessagePosition(String table, String action, ArrayList<Position> data) {
		super();
		this.table = table;
		this.action = action;
		this.data = data;
	}
	
	public MessagePosition() {
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

	public void setData(ArrayList<Position> data) {
		this.data = data;
	}

	public ArrayList<Position> getData() {
		return data;
	}

	@Override
	public String toString() {
		return "Message [table=" + table + ", action=" + action + ", data=" + data.toString() + "]";
	}

}
