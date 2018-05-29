package bitmexAdapter;

public class Answer {
	private String table;
	private String action;
	private String error;
	
	public Answer(String table, String action, String error) {
		super();
		this.table = table;
		this.action = action;
		this.error = error;
	}

	public String getTable() {
		return table;
	}

	public String getAction() {
		return action;
	}

	public String getError() {
		return error;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public void setError(String error) {
		this.error = error;
	}
	
	

}
