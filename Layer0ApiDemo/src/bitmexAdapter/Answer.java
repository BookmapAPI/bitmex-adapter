package bitmexAdapter;

import com.google.gson.annotations.Expose;

public class Answer {
	private String table;
	private String action;
	
	private String error;
	private String info;
	private Boolean success;
	
	public String getTable() {
		return table;
	}
	public String getError() {
		return error;
	}
	public Boolean getSuccess() {
		return success;
	}
	public void setTable(String table) {
		this.table = table;
	}
	public void setError(String error) {
		this.error = error;
	}
	public void setSuccess(Boolean success) {
		this.success = success;
	}
	public String getInfo() {
		return info;
	}
	public void setInfo(String info) {
		this.info = info;
	}
	public String getAction() {
		return action;
	}
//	public String getData() {
//		return data;
//	}
	public void setAction(String action) {
		this.action = action;
	}
	
//	public void setData(String data) {
//		this.data = data;
//	}
	
	
	

}
