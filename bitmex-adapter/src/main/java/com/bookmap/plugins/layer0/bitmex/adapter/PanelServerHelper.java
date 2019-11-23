package com.bookmap.plugins.layer0.bitmex.adapter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bookmap.plugins.layer0.bitmex.Provider;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import velox.api.layer1.data.SystemTextMessageType;


public class PanelServerHelper {
    private ServerSocket server;
    private Socket client;
    private TradeConnector connector;
    private Provider provider;
    private AtomicBoolean isConnected = new AtomicBoolean(false); 
    private AtomicBoolean isConnecting = new AtomicBoolean(); 
    private AtomicBoolean isEnabled= new AtomicBoolean(true);
    private Object lock = new Object();
    private PrintWriter pw;
    private BufferedReader br;
    private DataInputStream is;
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
    
    public void sendMessage (String message) {
        printIfChanged(" to client " + message);

        if (isConnected.get()) {
            pw.println(message);

            if (pw.checkError()) {
                printIfChanged(" Client not accessible");
                closeServer();
              if (!isConnecting.get()) startInputConnection();
            }
        }
    }
    
    public void startInputConnection() {
        isConnecting.set(true);
        
        Thread connectingThread = new Thread(() -> {
            printIfChanged(" starting server thread");
            while (isEnabled.get() && isConnecting.get()) {
                try {
                    server = new ServerSocket(Constants.portNumber);
                    client = server.accept();
                    pw = new PrintWriter(
                            new BufferedWriter(new OutputStreamWriter(new DataOutputStream(client.getOutputStream()))),
                            true);
                    is = new DataInputStream(client.getInputStream());
                    br = new BufferedReader(new InputStreamReader(is));
                    isConnecting.set(false);
                    isConnected.set(true);
                    printIfChanged(" server started");
                    break;
                } catch (Exception e) {
                    printIfChanged(" closing server");
                    closeServer();
                    printIfChanged(" server closed");
                    isConnected.set(false);
                    
                    synchronized (lock) {
                        if (!isConnecting.get()) {
                            printIfChanged(" starting server thread in catch block");
                            startInputConnection();
                        }    
                    }
                }
            }
            startReading();
        });
        connectingThread.setName("->com.bookmap.plugins.layer0.bitmex.adapter.PanelServerHelper: server connecting thread");
        connectingThread.start();
    }

    public void stop() {
        isConnected.set(false);
        isEnabled.set(false);
        closeServer();
    }
      
    private void startReading() {
        Thread readingThread = new Thread(() -> {
            printIfChanged(" start server reading thread");

            while (isEnabled.get() && isConnected.get()) {
                printIfChanged(" reading from client ...");
                
                try {
                    String message = br.readLine();
                    printIfChanged(" from client " + message);
                    if (message != null) {
                        acceptMessage(message);
                    } else {
                        throw new IOException();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    printIfChanged(" no client ");
                    printIfChanged(" closing server reading thread");
                    closeServer();
                    printIfChanged(" server reading thread closed");
                    isConnected.set(false);

                    synchronized (lock) {
                        if (!isConnecting.get()) {
                            startInputConnection();
                        }
                    }
                    break;
                }
            }
        });
        readingThread.setName("->com.bookmap.plugins.layer0.bitmex.adapter.PanelServerHelper: server reading thread");
        readingThread.start();
    }

    private void closeServer() {
        try {
            if (client != null) client.close();
            printIfChanged(" server: client socket closed");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (server != null) server.close();
            printIfChanged(" server: server socket closed");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void acceptMessage(String message) {
        printIfChanged(" from client" + message);

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
                sendMessage(msg);
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
                sendMessage(msg);
                return;
            }

            Integer leverage = provider.getLeverage(symbol);
            if (leverage != null) {
                mp.put("leverage", leverage);
                int maxLeverage = provider.getConnector().getMaximumLeverage(symbol);
                mp.put("maxLeverage", maxLeverage);
                String msg = JsonParser.gson.toJson(mp);
                LogBitmex.infoClassOf(this.getClass(), "pong " + msg);
                sendMessage(msg);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("/api/v1/position?filter={\"symbol\":\"").append(symbol).append("\"}");
                String addr = sb.toString();

                String str = connector.makeRestGetQuery(addr);
                UnitPosition[] positions = JsonParser.getArrayFromJson(str, UnitPosition[].class);

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
                    sendMessage(msg);
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
