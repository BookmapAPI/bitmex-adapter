package com.bookmap.plugins.layer0.bitmex.adapter;

import velox.api.layer1.common.Log;

public class LogBitmex {
    
    public static final String prefix = constructPrefix();
    
    public static void info(String message) {
        Log.info(addPrefix(message));
    }
    
    public static void info(String message, Exception ex) {
        Log.info(addPrefix(message), ex);
    }
    
    public static void infoClassOf(Class<?> clazz, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getSimpleName())
        .append(" : ")
        .append(message);
        
        Log.info(addPrefix(sb.toString()));
    }
    
    public static void infoClassOf(Class<?> clazz, String message, Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getSimpleName())
        .append(" : ")
        .append(message);
        
        Log.info(addPrefix(sb.toString()), ex);
    }

    static String addPrefix(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(message);
        
        return sb.toString();
    }
    
    static String constructPrefix() {
        StringBuilder sb = new StringBuilder();
        sb.append("[bitmex-")
        .append(Constants.version)
        .append("] ");
        
        return sb.toString();
    }

}
