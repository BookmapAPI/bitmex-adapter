package com.bookmap.plugins.layer0.bitmex.messages;

import java.io.Serializable;

import org.apache.http.Header;

import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;

public class ModuleTargetedHttpRequestFeedbackMessage implements BitmexAdapterModuleTargetedMessage, Serializable{

    private static final long serialVersionUID = 1L;
    
    public final GeneralType genType;
    public final Method method;
    public final String data;
    public final boolean isOrderListBeingCanceled;
    public final String requestParameters;
    
    public final Header[] headers;
    public final int statusCode;
    public final String response;

    public ModuleTargetedHttpRequestFeedbackMessage(GeneralType genType, Method method, String data,
            boolean isOrderListBeingCanceled, String requestParameters, Header[] headers, int statusCode,
            String response) {
        super();
        this.genType = genType;
        this.method = method;
        this.data = data;
        this.isOrderListBeingCanceled = isOrderListBeingCanceled;
        this.requestParameters = requestParameters;
        this.headers = headers;
        this.statusCode = statusCode;
        this.response = response;
    }

}
