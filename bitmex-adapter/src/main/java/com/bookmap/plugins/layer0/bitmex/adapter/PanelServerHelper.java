package com.bookmap.plugins.layer0.bitmex.adapter;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.bookmap.plugins.layer0.bitmex.Provider;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.bookmap.plugins.layer0.bitmex.messages.ModuleTargetedLeverageMessage;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import velox.api.layer1.data.SystemTextMessageType;


public class PanelServerHelper {
    private TradeConnector connector;
    private Provider provider;
    private String latestMessage = "";
    
    public PanelServerHelper() {
        super();
    }
    
    public void setConnector(TradeConnector connector) {
        this.connector = connector;
    }
    
    public void setProvider(Provider provider) {
        this.provider = provider;
    }
    
    public void onUserMessage (String message) {
        try {
            if (Class.forName("velox.api.layer1.messages.Layer1ApiUserInterModuleMessage") != null) {
                ModuleTargetedLeverageMessage bitmexMessage = new ModuleTargetedLeverageMessage();
                bitmexMessage.setMessage(message);
                provider.onUserMessage(bitmexMessage);
            }
        } catch (ClassNotFoundException e) {
            // this is the desired behavior for 7.0 so no warnings needed
        }
    }

    public void stop() {
    }

    public void acceptMessage(String message) {
        try {
            if (Class.forName("velox.api.layer1.messages.Layer1ApiUserInterModuleMessage") != null) {
                // do nothing
            }
        } catch (ClassNotFoundException e1) {
            // this is the desired behavior for 7.0 so no warnings needed
        }

        Map<String, Object> map = new HashMap<>();
        Type mapType = new TypeToken<Map<String, Object>>() {
        }.getType();
        map = JsonParser.gson.fromJson(message, mapType);
        String symbol = ((String) map.get("symbol")).split("@")[0];
        printIfChanged("  map parsed " + message);
        
        LinkedHashMap<String, Object> mp = new LinkedHashMap<>();
        mp.put("symbol", symbol);
        
        if (map.get("leverage") != null) {
            if (provider.isCredentialsEmpty()) {
                mp.put("isCredentialsEmpty", true);
                String msg = JsonParser.gson.toJson(mp);
                printIfChanged(" CredentialsEmpty " + msg);
                onUserMessage(msg);
                return;
            }
            
            int leverage = (int) Math.round((Double) map.get("leverage"));
            printIfChanged(" map parsed and lev = " + leverage);
            JsonObject json = new JsonObject();
            json.addProperty("symbol", symbol);
            if (leverage == 0.0) {
                json.addProperty("leverage", "0");
            } else {
                json.addProperty("leverage", leverage);
            }
            String str = connector.require(GeneralType.POSITION, Method.POST, json.toString());
            if (str != null) {
                if (str.contains("error")) {
                    provider.adminListeners
                            .forEach(l -> l.onSystemTextMessage(str, SystemTextMessageType.UNCLASSIFIED));
                } 
            }
            UnitPosition position = JsonParser.gson.fromJson(str, new TypeToken<UnitPosition>() {}.getType());
            provider.listenForPosition(position);
        } else if (map.get("ping") != null) {
            if (provider.isCredentialsEmpty()) {
                mp.put("isCredentialsEmpty", true);
                String msg = JsonParser.gson.toJson(mp);
                LogBitmex.infoClassOf(this.getClass(), "CredentialsEmpty " + msg);
                onUserMessage(msg);
                return;
            }

            Integer leverage = provider.getLeverage(symbol);
            if (leverage != null) {
                mp.put("leverage", leverage);
                int maxLeverage = provider.getConnector().getMaximumLeverage(symbol);
                mp.put("maxLeverage", maxLeverage);
                String msg = JsonParser.gson.toJson(mp);
                LogBitmex.infoClassOf(this.getClass(), "pong " + msg);
                onUserMessage(msg);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("{\"symbol\":\"").append(symbol).append("\"}");
                String filter = sb.toString();
                try {
                    filter = URLEncoder.encode(filter,"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                String address = "?filter=" + filter;
                String result = connector.require(GeneralType.POSITION, Method.GET, null, false, address);
                UnitPosition[] positions = JsonParser.getArrayFromJson(result, UnitPosition[].class);

                if (positions.length == 0) {
                    mp.put("leverage", 0);
                    Integer maxLeverage = provider.maxLeverages.get(symbol);
                    if (maxLeverage == null) {
                        maxLeverage = 1;
                    }
                    mp.put("maxLeverage", maxLeverage);
                    JsonParser.gson.toJson(mp);
                    String msg = JsonParser.gson.toJson(mp);
                    LogBitmex.infoClassOf(this.getClass(), "pong " + msg);
                    onUserMessage(msg);
                } else {
                    UnitPosition position = positions[0];
                    provider.listenForPosition(position);
                }
            }
        }
    }
    
    private void printIfChanged(String text) {
        if (!text.equals(latestMessage)) {
            latestMessage = text;
            LogBitmex.infoClassOf(this.getClass(), latestMessage); 
        }
    }

}
