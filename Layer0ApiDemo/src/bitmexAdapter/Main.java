package bitmexAdapter;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;

import com.google.gson.JsonObject;

import bitmexAdapter.TradeConnector.Key;

public class Main {

	public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {

		TradeConnector connr = new TradeConnector();
		Map<String, TradeConnector.Key> keys = new HashMap<String, TradeConnector.Key>();
//		TradeConnector.Key regularKey = connr.new Key("9lB3AlKaGCM3_ea1Y6-U6Hcd",
//				"TagdIwfBWp8YuMbZfocdaVtEO9qoIZla37BCsp3fWAhimLoq");
		TradeConnector.Key orderKey = connr.new Key("PLc0jF_9Jh2-gYU6ye-6BS4q",
				"xyMWpfSlONCWCwrntm0GotQN42ia291Vv2aWANlp-f0Kb5-I");
//		keys.put("regularKey", regularKey);
		keys.put("orderKey", orderKey);
		connr.setKeys(keys);

		// ************TRADE ORDER WORKS, FORM SYNTHAX
//		 String path = "/api/v1/order";
//		 String data =
//		 "?symbol=XBTUSD&side=Buy&price=8800&orderQty=1.0&ordType=Limit";
//		 long moment = System.currentTimeMillis() + 10000000;
//		 String reguOrderKey = connr.getKeys().get("orderKey").getApiKey();
//		 String secretOrderKey =
//		 connr.getKeys().get("orderKey").getApiSecretKey();
//		 String messageBody = connr.createMessageBody("POST", path + data,
//		 data, moment);
//		 String signature = connr.generateSignature(secretOrderKey,
//		 messageBody);
//		 String answer = connr.post(connr.restApi + path + data, reguOrderKey,
//		 signature, moment, data);
//		 System.out.println(answer);
		
		// ************TRADE ORDER WORKS, FORM SYNTHAX, OBVIUOSLY NO DATA
//		 String path = "/api/v1/order?symbol=XBTUSD&side=Buy&price=8800&orderQty=1.0&ordType=Limit";
//		 String data =
//				 "";
////		 long moment = System.currentTimeMillis() + 10000000;
//		long moment = 1525989849;
//		 String reguOrderKey = connr.getKeys().get("orderKey").getApiKey();
//		 System.out.println("Key\t" + reguOrderKey);
//		 String secretOrderKey =
//				 connr.getKeys().get("orderKey").getApiSecretKey();
//		 String messageBody = connr.createMessageBody("POST", path + data,
//				 data, moment);
//		 String signature = connr.generateSignature(secretOrderKey,
//				 messageBody);
//		 String answer = connr.post(connr.restApi + path + data, reguOrderKey,
//				 signature, moment, data);
//		 System.out.println(answer);

		// ************TRADE ORDER JSON, NOT WORKING, QUERY IS NOT PARSED
//		JsonObject json = new JsonObject();
//		json.addProperty("symbol", "XBTUSD");
//		json.addProperty("Side", "Buy");
//		json.addProperty("price", 9000);
//		json.addProperty("orderQty", 1.0);
//		json.addProperty("ordType", "Limit");
//		System.out.println("data\t" + json.toString());
//
//		String path = "/api/v1/order";
//		String data = json.toString();
////		long moment = System.currentTimeMillis() + 10000000;
//		long moment = 1525989849;
//		String reguOrderKey = connr.getKeys().get("orderKey").getApiKey();
//		 System.out.println("Key\t" + reguOrderKey);
//		String secretOrderKey = connr.getKeys().get("orderKey").getApiSecretKey();
//		String messageBody = connr.createMessageBody("POST", path, data, moment);
//		String signature = connr.generateSignature(secretOrderKey, messageBody);
//		String answer = connr.post(connr.restApi + path, reguOrderKey, signature, moment, json.toString());
//		System.out.println(answer);
		
		
//***THE SHORTEST json
		String path = "/api/v1/order";
		JsonObject json = new JsonObject();
		json.addProperty("symbol", "XBTUSD");
		json.addProperty("Side", "Buy");
		json.addProperty("price", 9000);
		json.addProperty("orderQty", 1.0);
		json.addProperty("ordType", "Limit");
		String data = json.toString();
//		String data = json.toString();
		long moment = 1525989849;
		String reguOrderKey ="PLc0jF_9Jh2-gYU6ye-6BS4q";
		System.out.println("Key\t" + reguOrderKey);
		String secretOrderKey = connr.getKeys().get("orderKey").getApiSecretKey();
		String messageBody = connr.createMessageBody("POST", path, data, moment);
		String signature = connr.generateSignature(secretOrderKey, messageBody);
		String answer = connr.post(connr.restApi + path, reguOrderKey, signature, moment, data);
		System.out.println(answer);
		
		
//		***DATA=""
//		String path = "/api/v1/order";
//		String data = "";
//		long moment = 1525989849;
//		String reguOrderKey ="PLc0jF_9Jh2-gYU6ye-6BS4q";
//		System.out.println("Key\t" + reguOrderKey);
//		String secretOrderKey = connr.getKeys().get("orderKey").getApiSecretKey();
//		String messageBody = connr.createMessageBody("POST", path, data, moment);
//		String signature = connr.generateSignature(secretOrderKey, messageBody);
//		String answer = connr.post(connr.restApi + path, reguOrderKey, signature, moment, data);
//		System.out.println(answer);
		

		

		// ************GET*************************
		// String path =
		// "/api/v1/instrument?filter=%7B%22symbol%22%3A+%22XBTM15%22%7D";
		// String data = "";
		// long moment = System.currentTimeMillis() + 10000000;
		// String messageBody = connr.createMessageBody("GET", path + data,
		// data, moment);
		// String reguKey = connr.getKeys().get("regularKey").getApiKey();
		// String secretKey =
		// connr.getKeys().get("regularKey").getApiSecretKey();
		// String signature = connr.generateSignature(secretKey, messageBody);
		// String answer = connr.get(connr.restApi + path, reguKey, signature,
		// moment);
		// System.out.println(answer);

	}

}