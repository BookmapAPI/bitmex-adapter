package bitmexAdapter;

public class ResponseByRest {

	public Container error;

	public Container getError() {
		return error;
	}

	public void setError(Container error) {
		this.error = error;
	}

	public class Container {
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
	
	
	

}
