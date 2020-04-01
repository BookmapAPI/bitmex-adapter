package com.bookmap.plugins.layer0.bitmex.messages;

import java.io.Serializable;

import com.bookmap.plugins.layer0.bitmex.adapter.Constants;

public class ProviderTargetedLeverageMessage implements BitmexAdapterProviderTargetedMessage, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 0L;

    private static final String version = "0.0.0.1";
    
    private String message;
    
    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getProviderProgrammaticName() {
        return Constants.programmaticName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}