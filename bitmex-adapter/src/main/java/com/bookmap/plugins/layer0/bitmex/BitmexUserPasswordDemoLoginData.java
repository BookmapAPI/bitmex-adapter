package com.bookmap.plugins.layer0.bitmex;

import velox.api.layer1.data.UserPasswordDemoLoginData;

public class BitmexUserPasswordDemoLoginData extends UserPasswordDemoLoginData {
    
    public final boolean isTradingEnabled;

    public BitmexUserPasswordDemoLoginData(String user, String password, boolean isDemo, boolean isTradingEnabled) {
        super(user, password, isDemo);
        this.isTradingEnabled = isTradingEnabled;
    }
    
    public BitmexUserPasswordDemoLoginData(UserPasswordDemoLoginData loginData, boolean isTradingEnabled) {
        super(loginData.user, loginData.password, loginData.isDemo);
        this.isTradingEnabled = isTradingEnabled;
    }


}
