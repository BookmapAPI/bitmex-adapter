package com.bookmap.plugins.layer0.bitmex.messages;

import java.io.Serializable;

public class ModuleTargetedLeverageMessage implements BitmexAdapterModuleTargetedMessage, Serializable{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String message;
    
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
