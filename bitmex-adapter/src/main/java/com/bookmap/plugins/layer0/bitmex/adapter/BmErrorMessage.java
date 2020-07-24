package com.bookmap.plugins.layer0.bitmex.adapter;

public class BmErrorMessage {

    public static class errorMessage {
        public String message;
        public String name;
    }
    
	public errorMessage error;

	public String getMessage() {
	    return error.message;	    
	}
}
