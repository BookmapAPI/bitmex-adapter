package bitmexAdapter;

import java.util.ArrayList;


public class Msg {
	public String table;
	public String action;
	public ArrayList<DataUnit> data = new ArrayList<>();
	
	
	
	public String getTable() {
		return table;
	}



	public String getAction() {
		return action;
	}



	public ArrayList<DataUnit> getData() {
		return data;
	}



	@Override
	public String toString() {
		return "Msg [table=" + table + ", action=" + action + ", data=" + data.toString() + "]";
	}


	
}
