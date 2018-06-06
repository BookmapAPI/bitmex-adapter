package bitmexAdapter;

import java.util.ArrayList;

public class MessageGeneric<T> {
	private String table;
	private String action;
	private Class <T> cls ;
	private ArrayList<T> data = new ArrayList<>();
	
	public MessageGeneric(String table, String action, Class<T> cls, ArrayList<T> data) {
		super();
		this.table = table;
		this.action = action;
		this.cls = cls;
		this.data = data;
	}

	public MessageGeneric() {
		super();
		// TODO Auto-generated constructor stub
	}

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
