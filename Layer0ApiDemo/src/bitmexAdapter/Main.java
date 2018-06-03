package bitmexAdapter;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import bitmexAdapter.TradeConnector.Key;
import groovy.json.JsonBuilder;

public class Main {

	public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {

		TradeConnector connr = new TradeConnector();

		
		String symbol = "XBTUSD";
		System.out.println("{\"op\":\"unsubscribe\", \"args\":[\"orderBookL2:" + symbol + "\",\"trade:" + symbol + "\", \"order:" + symbol + "\",\"execution:" + symbol + "\",\"position:" + symbol + "\"]}");

		JsonObject json = new JsonObject();
		json.addProperty("orderBookL2", symbol);
		json.addProperty("trade", symbol);
		json.addProperty("order", symbol);
		json.addProperty("execution", symbol);
		json.addProperty("position", symbol);
//		json.a
		String data = json.toString();
		
		Gson json2 = new GsonBuilder().create();
		Map<String, String> map = new HashMap<>();
		List<String> list = new LinkedList<>();
		list.add("one");
		list.add("two");
		map.put("orderBookL2", symbol);
		map.put("trade", symbol);
		map.put("order", symbol);
		map.put("execution", symbol);
		map.put("position", symbol);
		map.put("args", json.toString());
		String data2 = json2.toJson(map);
		
		
		
		

		
		System.out.println(data);
		System.out.println(data2);


	}

}