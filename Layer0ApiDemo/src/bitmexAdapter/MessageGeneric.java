package bitmexAdapter;

import java.util.ArrayList;

public class MessageGeneric<T> {
	private String table;
	private String action;
	private Class <T> cls ;
	private ArrayList<T> data = new ArrayList<>();
	

	public String getTable() {
		return table;
	}

	public String getAction() {
		return action;
	}

	public Class<T> getCls() {
		return cls;
	}

	public ArrayList<T> getData() {
		return data;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public void setCls(Class<T> cls) {
		this.cls = cls;
	}

	public void setData(ArrayList<T> data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return "Message [table=" + table + ", action=" + action + ", data=" + data.toString() + "]";
	}

}
