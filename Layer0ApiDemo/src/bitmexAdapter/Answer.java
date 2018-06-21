package bitmexAdapter;


public class Answer {
	private String table;
	private String action;
	private Integer status;
	
	

	private ContainerReq request;


	//	private Container error;
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
	
	public Integer getStatus() {
		return status;
	}
	public void setStatus(Integer status) {
		this.status = status;
	}
	
	public ContainerReq getRequest() {
		return request;
	}
	
	class Container {
		String message;
		String name;
		public String getMessage() {
			return message;
		}
		public String getName() {
			return name;
		}
		public void setMessage(String message) {
			this.message = message;
		}
		public void setName(String name) {
			this.name = name;
		}
	}
	
	class ContainerReq {
		String op;

		public String getOp() {
			return op;
		}

		public void setOp(String op) {
			this.op = op;
		}
		
	}
	

}
