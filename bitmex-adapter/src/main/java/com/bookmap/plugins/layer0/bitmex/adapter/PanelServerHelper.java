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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import velox.api.layer1.common.Log;


public class PanelServerHelper {
    private ServerSocket server;
    private Socket client;
    private TradeConnector connector;
    
    private AtomicBoolean isConnected = new AtomicBoolean(false); 
    private AtomicBoolean isConnecting = new AtomicBoolean(); 
    private AtomicBoolean isEnabled= new AtomicBoolean(true);
    private Object lock = new Object();
    
    private PrintWriter pw;
    private BufferedReader br;
    
    public PanelServerHelper(TradeConnector connector) {
        super();
        this.connector = connector;
    }

    public void sendMessage (String message) {
        if (isConnected.get()) {
            pw.println(message);

            if (pw.checkError()) {
                Log.info("Client not accessible");
                closeServer();
              if (!isConnecting.get()) startInputConnection();
            }
        }
    }
    
    public void startInputConnection() {
        isConnecting.set(true);
        
        Thread connectingThread = new Thread(() -> {
            Log.info("START SERVER THREAD");
            while (isEnabled.get() && isConnecting.get()) {
                try {
                    server = new ServerSocket(Constants.portNumber);
                    client = server.accept();
                    pw = new PrintWriter(
                            new BufferedWriter(new OutputStreamWriter(new DataOutputStream(client.getOutputStream()))),
                            true);
                    br = new BufferedReader(new InputStreamReader(new DataInputStream(client.getInputStream())));
                    isConnecting.set(false);
                    isConnected.set(true);
                    break;
                } catch (Exception e) {
                    closeServer();
                    isConnected.set(false);
                    
                    synchronized (lock) {
                        if (!isConnecting.get()) {
                            startInputConnection();
                        }    
                    }
                }
            }
            startReading();
        });
        connectingThread.setName("->com.bookmap.plugins.layer0.bitmex.adapter: server connecting thread");
        connectingThread.start();
    }
    

    public void stop() {
        isConnected.set(false);
        isEnabled.set(false);
        closeServer();
    }
      
    private void startReading() {
        Thread readingThread = new Thread(() -> {
            Log.info("START SERVER READING THREAD");

            while (isEnabled.get() && isConnected.get()) {
                try {
                    String message = br.readLine();
                    if (message != null ) {
                        System.out.println("FROM CLIENT " + message);
                        acceptMessage(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    closeServer();
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
        readingThread.setName("->com.bookmap.plugins.layer0.bitmex.adapter: server reading thread");
        readingThread.start();
    }

    private void closeServer() {
        try {
            if (client != null) client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (server != null) server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void acceptMessage(String message) {
        Map<String, Object> map = new HashMap<>();
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        map = JsonParser.gson.fromJson(message, mapType);
        String symbol = ((String) map.get("symbol")).split("@")[0];
        Log.info("map parsed");
        if (map.get("leverage") != null){
                double leverage = (double) map.get("leverage");
                Log.info("map parsed and lev = " + leverage);
                JsonObject json = new JsonObject();
                json.addProperty("symbol", symbol);
                json.addProperty("leverage", leverage);
                connector.require(GeneralType.POSITION, Method.POST, json.toString());
        }
    }

}
