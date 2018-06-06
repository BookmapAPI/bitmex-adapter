package bitmexAdapter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Hex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import bitmexAdapter.TradeConnector.GeneralType;
import bitmexAdapter.TradeConnector.Method;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.OrderInfoBuilder;
import velox.api.layer1.data.OrderMoveParameters;
import velox.api.layer1.data.OrderType;
import velox.api.layer1.data.SimpleOrderSendParameters;

public class TradeConnector {

	private static final Gson gson = new GsonBuilder().create();

	// public enum orderParams {
	// symbol, side, simpleOrderQty, orderQty, price, displayQty, stopPx,
	// clOrdID, clOrdLinkID, pegOffsetValue, pegPriceType, ordType, timeInForce,
	// execInst, contingencyType, text;
	// }
	//
	// // Defaults to 'Limit' when price is specified.
	// // Defaults to 'Stop' when stopPx is specified.
	// // Defaults to 'StopLimit' when price and stopPx are specified.
	// public enum paramOrdType {
	// Market, Limit, Stop, StopLimit, MarketIfTouched, LimitIfTouched,
	// MarketWithLeftOverAsLimit, Pegged;
	// }
	//
	// public enum paramSide {
	// Buy, Sell;
	// }
	//
	// public enum paramPegPriceType {
	// LastPeg, MidPricePeg, MarketPeg, PrimaryPeg, TrailingStopPeg;
	// }

	private static final long requestTimeToLive = 10000;
	public final String orderApiKey = "PLc0jF_9Jh2-gYU6ye-6BS4q";
	public final String orderApiSecret = "xyMWpfSlONCWCwrntm0GotQN42ia291Vv2aWANlp-f0Kb5-I";

	// final String apiKey = "9lB3AlKaGCM3_ea1Y6-U6Hcd";
	// final String apiSecret =
	// "TagdIwfBWp8YuMbZfocdaVtEO9qoIZla37BCsp3fWAhimLoq";
	// String restApi = "https://www.bitmex.com";
	// public final String restApi = "https://testnet.bitmex.com";
	public final String restApi = "https://testnet.bitmex.com";

	private Map<String, TradeConnector.Key> keys = new HashMap<String, TradeConnector.Key>();

	public enum GeneralType {
		order, instrument, execution, position;
	}

	private EnumMap<GeneralType, String> subPaths = new EnumMap<GeneralType, String>(GeneralType.class);
	{
		subPaths.put(GeneralType.order, "/api/v1/order");
		subPaths.put(GeneralType.instrument, "/api/v1/instrument");
		subPaths.put(GeneralType.execution, "/api/v1/execution");
		subPaths.put(GeneralType.position, "/api/v1/position");
	}

	public enum Method {
		GET, PUT, POST, DELETE;
	}

	private EnumMap<Method, String> methods = new EnumMap<Method, String>(Method.class);
	{
		methods.put(Method.GET, "GET");
		methods.put(Method.POST, "POST");
		methods.put(Method.PUT, "PUT");
		methods.put(Method.DELETE, "DELETE");
	}

	public class Key {
		private final String apiKey;
		private final String apiSecretKey;

		public Key(String apiKey, String apiSecretKey) {
			super();
			this.apiKey = apiKey;
			this.apiSecretKey = apiSecretKey;
		}

		public String getApiKey() {
			return apiKey;
		}

		public String getApiSecretKey() {
			return apiSecretKey;
		}
	}

	public void setKeys(Map<String, TradeConnector.Key> keys) {
		this.keys = keys;
	}

	public Map<String, TradeConnector.Key> getKeys() {
		return keys;
	}

	public static long getMoment() {
		return System.currentTimeMillis() + requestTimeToLive;
	}

	public static String hash256(String data) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(data.getBytes());
		return bytesToHex(md.digest());
	}

	public static String bytesToHex(byte[] bytes) {
		StringBuffer result = new StringBuffer();
		for (byte byt : bytes)
			result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
		return result.toString();
	}

	public static String createMessageBody(String method, String path, String data, long moment) {
		String messageBody = method + path + Long.toString(moment) + data;
		// System.out.println("messageBody\t" + messageBody);
		return messageBody;
	}

	public static String generateSignature(String apiSecret, String messageBody)
			throws NoSuchAlgorithmException, InvalidKeyException {
		Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
		sha256_HMAC.init(secretKey);
		byte[] hash = sha256_HMAC.doFinal(messageBody.getBytes());
		String check = Hex.encodeHexString(hash);
		// System.out.println("signature\t" + check);
		return check;
	}

	public String get(String address, String key, String signature, long moment, String data) {
		String response = null;

		try {
			URL url = new URL(address);
			Log.info("url\t" + address);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			// conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("api-expires", Long.toString(moment));
			conn.setRequestProperty("api-key", key);
			conn.setRequestProperty("api-signature", signature);

			// *********
			// conn.setRequestProperty("Content-Length",
			// Integer.toString(data.getBytes("UTF-8").length));
			// System.out.println(Integer.toString(data.getBytes("UTF-8").length));
			// OutputStream os = conn.getOutputStream();
			// OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			// osw.write(data);
			// osw.flush();
			// osw.close();

			if (conn.getResponseCode() == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				conn.disconnect();
				response = sb.toString();
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}

				System.out.println(sb.toString());
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			// Log.info("NO RESPONSE FROM SERVER");
		} catch (java.net.SocketException e) {
			// Log.info("NETWORK IS UNREACHABLE");
		} catch (IOException e) {
			// Log.debug("BUFFER READING ERROR");
			e.printStackTrace();
		}
		return response;
	}

	public String post(String address, String key, String signature, long moment, String data) {
		System.out.println("url\t" + address);
		String response = null;

		try {
			URL url = new URL(address);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("api-expires", Long.toString(moment));
			conn.setRequestProperty("api-key", key);
			conn.setRequestProperty("api-signature", signature);
			conn.setRequestProperty("Content-Length", Integer.toString(data.getBytes("UTF-8").length));
			System.out.println(Integer.toString(data.getBytes("UTF-8").length));

			OutputStream os = conn.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			osw.write(data);
			osw.flush();
			osw.close();

			// BufferedWriter out =
			// new BufferedWriter(new
			// OutputStreamWriter(conn.getOutputStream()));
			// out.write(json.toString());
			// out.close();

			if (conn.getResponseCode() == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				conn.disconnect();
				response = sb.toString();
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}

				System.out.println(sb.toString());
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			// Log.info("NO RESPONSE FROM SERVER");
		} catch (java.net.SocketException e) {
			// Log.info("NETWORK IS UNREACHABLE");
		} catch (IOException e) {
			// Log.debug("BUFFER READING ERROR");
			e.printStackTrace();
		}
		return response;
	}

	public String isolateSymbol(String alias) {
		// Log.info(alias);
		char[] symbData = alias.toCharArray();
		StringBuilder sb = new StringBuilder();
		sb.append("");

		for (int i = 0; i < symbData.length
				&& (symbData[i] >= 'A' && symbData[i] <= 'Z' || symbData[i] >= '0' && symbData[i] <= '9');) {
			sb.append(symbData[i++]);
		}
		// for (int i = 0; i < symbData.length; i++){
		// if (symbData[i] >= 'A' && symbData[i] <= 'Z'){
		// sb.append(symbData[i]);
		// } else {
		// break;
		// }
		// }
		return sb.toString();
	}

	public void processNewOrder(SimpleOrderSendParameters params, OrderType orderType, String tempOrderId) {

		String symbol = isolateSymbol(params.alias);
		String side = params.isBuy ? "Buy" : "Sell";
		Log.info("****SIDE = " + side);
		double price = params.limitPrice;
		double orderQty = params.size;

		JsonObject json = new JsonObject();
		json.addProperty("symbol", symbol);
		json.addProperty("side", side);
		// json.addProperty("simpleOrderQty", orderQty);
		json.addProperty("orderQty", orderQty);
		json.addProperty("orderQty", orderQty);
		json.addProperty("clOrdID", tempOrderId);

		/*
		 * https://www.bitmex.com/api/explorer/#!/Order/Order_new Send a
		 * simpleOrderQty instead of an orderQty to create an order denominated
		 * in the underlying currency. This is useful for opening up a position
		 * with 1 XBT of exposure without having to calculate how many contracts
		 * it is.
		 */

		if (orderType == OrderType.LMT) {
			json.addProperty("ordType", "Limit");
			json.addProperty("price", price);
		} else if (orderType == OrderType.STP) {// StopMarket
			json.addProperty("ordType", "Stop");
			json.addProperty("stopPx", params.stopPrice);
		} else if (orderType == OrderType.STP_LMT) {
			json.addProperty("ordType", "StopLimit");
			json.addProperty("stopPx", params.stopPrice);
			json.addProperty("price", price);
		}

		String data = json.toString();
		Log.info("ORDER TYPE " + orderType.toString());
		Log.info("NEW ORDER TR " + data);

		try {
			String res = require(GeneralType.order, Method.POST, data);
			// String st = require(GeneralType.execution, Method.GET, "");
			Log.info(res);
			// Log.info(st);

			// *********************************************
			// Log.info("GET FROM HERE ON");
			long moment = getMoment();
			String adr = "/api/v1/execution";
			String addr;
			String data0;
			String st0;
			String sign;

			// String data0 = "?ordStatus=New";
			String data1 = "";
			// String addr = "/api/v1/execution?ordStatus=New";
			// String sign = generateSignature(orderApiSecret,
			// createMessageBody("GET", addr, data1, moment));
			// Log.info("GET LAUNCHES");
			// String st0 = get("https://testnet.bitmex.com" + addr,
			// orderApiKey,
			// sign,
			// moment,
			// data0);
			// Log.info(st0);
			//
			//
			//
			// data0 = "?{\"ordStatus\":\"New\"}";
			// addr = "/api/v1/execution?{\"ordStatus\":\"New\"}";
			// sign = generateSignature(orderApiSecret, createMessageBody("GET",
			// addr, data1, moment));
			// st0 = get("https://testnet.bitmex.com" + addr,
			// orderApiKey,
			// sign,
			// moment,
			// data0);
			// Log.info(st0);

			// WORKING
			data0 = "?filter=%7B%22open%22:true%7D";
			addr = "/api/v1/order?filter=%7B%22open%22:true%7D";
			sign = generateSignature(orderApiSecret, createMessageBody("GET", addr, data1, moment));
			st0 = get("https://testnet.bitmex.com" + addr, orderApiKey, sign, moment, data0);
			Log.info("TR_CONNR NEW ORDER EXEC " + st0);
			//

			// return (BmOrder) gson.fromJson(res, BmOrder.class);
			// return (BmOrder) gson.fromJson(res, BmOrder.class);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		// return null;
	}

	public BmOrder cancelOrder(String orderId) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		String data = json.toString();

		try {
			String res = require(GeneralType.order, Method.DELETE, data);
			Log.info(res);
			BmOrder[] cancelledOrders = JsonParser.getArrayFromJson(res, BmOrder[].class);
			return cancelledOrders[0];
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	public BmOrder resizeOrder(String orderId, long orderQty) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		json.addProperty("orderQty", orderQty);
		// json.addProperty("simpleOrderQty", orderQty);
		String data = json.toString();

		try {
			String res = require(GeneralType.order, Method.PUT, data);
			Log.info(res);
			return (BmOrder) gson.fromJson(res, BmOrder.class);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	public BmOrder resizePartiallyFilledOrder(String orderId, long orderQty) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		json.addProperty("leavesQty", orderQty);
		// json.addProperty("simpleOrderQty", orderQty);
		String data = json.toString();

		try {
			String res = require(GeneralType.order, Method.PUT, data);
			Log.info(res);
			return (BmOrder) gson.fromJson(res, BmOrder.class);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void moveOrder(OrderMoveParameters params, boolean isStopTriggered) {
		// public void moveOrder(String orderId, OrderMoveParameters params) {
		OrderType orderType = OrderType.getTypeFromPrices(params.stopPrice, params.limitPrice);
		JsonObject json = new JsonObject();
		json.addProperty("orderID", params.orderId);
		if (orderType == OrderType.LMT) {
			// json.addProperty("ordType", "Limit");
			json.addProperty("price", params.limitPrice);
		} else if (orderType == OrderType.STP) {// StopMarket
			// json.addProperty("ordType", "Stop");
			json.addProperty("stopPx", params.stopPrice);
		} else if (orderType == OrderType.STP_LMT) {
			// json.addProperty("ordType", "StopLimit");
			if (!isStopTriggered) {
				json.addProperty("stopPx", params.stopPrice);
			}
			json.addProperty("price", params.limitPrice);
		}

		// JsonObject json = new JsonObject();
		// json.addProperty("orderID", orderId);
		// json.addProperty("price", price);
		String data = json.toString();

		try {
			String res = require(GeneralType.order, Method.PUT, data);
			Log.info(res);
			// return (BmOrder) gson.fromJson(res, BmOrder.class);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		// return null;
	}

	public String require(GeneralType genType, Method method, String data)
			throws InvalidKeyException, NoSuchAlgorithmException {
		// System.out.println("url\t" + address);
		String subPath = subPaths.get(genType);
		String path = this.restApi + subPath;
		long moment = getMoment();

		String response = null;

		try {
			URL url = new URL(path);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

			if (!method.equals(Method.GET)) {
				// if (method.equals(Method.POST) || method.equals(Method.PUT)){
				conn.setDoOutput(true);
			}

			// !!!!!!!!!1 КОСТЫЛЬ
			conn.setDoOutput(true);

			String messageBody = createMessageBody(methods.get(method), subPath, data, moment);
			String signature = generateSignature(orderApiSecret, messageBody);
			// String messageBody = this.createMessageBody(methods.get(method),
			// subPath, data, moment);
			// String signature = this.generateSignature(orderApiSecret,
			// messageBody);

			conn.setRequestMethod(methods.get(method));
			Log.info(methods.get(method));
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("api-expires", Long.toString(moment));
			conn.setRequestProperty("api-key", orderApiKey);// **************************
			conn.setRequestProperty("api-signature", signature);
			conn.setRequestProperty("Content-Length", Integer.toString(data.getBytes("UTF-8").length));
			System.out.println(Integer.toString(data.getBytes("UTF-8").length));

			OutputStream os = conn.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			osw.write(data);
			osw.flush();
			osw.close();

			// BufferedWriter out =
			// new BufferedWriter(new
			// OutputStreamWriter(conn.getOutputStream()));
			// out.write(json.toString());
			// out.close();

			if (conn.getResponseCode() == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				conn.disconnect();
				response = sb.toString();
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}

				System.out.println(sb.toString());
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			// Log.info("NO RESPONSE FROM SERVER");
		} catch (java.net.SocketException e) {
			// Log.info("NETWORK IS UNREACHABLE");
		} catch (IOException e) {
			// Log.debug("BUFFER READING ERROR");
			e.printStackTrace();
		}
		return response;
	}

	public String require0(GeneralType genType, Method method, String data)
			throws InvalidKeyException, NoSuchAlgorithmException {
		// System.out.println("url\t" + address);
		String subPath = subPaths.get(genType);
		String path = this.restApi + subPath;
		long moment = getMoment();

		String response = null;

		try {
			URL url = new URL(path);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

			if (!method.equals(Method.GET)) {
				// if (method.equals(Method.POST) || method.equals(Method.PUT)){
				conn.setDoOutput(true);
			}

			// !!!!!!!!!1 КОСТЫЛЬ
			conn.setDoOutput(true);

			String messageBody = createMessageBody(methods.get(method), subPath, data, moment);
			String signature = generateSignature(orderApiSecret, messageBody);
			// String messageBody = this.createMessageBody(methods.get(method),
			// subPath, data, moment);
			// String signature = this.generateSignature(orderApiSecret,
			// messageBody);

			conn.setRequestMethod(methods.get(method));
			Log.info(methods.get(method));
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("api-expires", Long.toString(moment));
			conn.setRequestProperty("api-key", orderApiKey);// **************************
			conn.setRequestProperty("api-signature", signature);
			conn.setRequestProperty("Content-Length", Integer.toString(data.getBytes("UTF-8").length));
			System.out.println(Integer.toString(data.getBytes("UTF-8").length));

			OutputStream os = conn.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			osw.write(data);
			osw.flush();
			osw.close();

			// BufferedWriter out =
			// new BufferedWriter(new
			// OutputStreamWriter(conn.getOutputStream()));
			// out.write(json.toString());
			// out.close();

			if (conn.getResponseCode() == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				conn.disconnect();
				response = sb.toString();
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}

				// System.out.println(sb.toString());
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			// Log.info("NO RESPONSE FROM SERVER");
		} catch (java.net.SocketException e) {
			// Log.info("NETWORK IS UNREACHABLE");
		} catch (IOException e) {
			// Log.debug("BUFFER READING ERROR");
			e.printStackTrace();
		}
		return response;
	}

}
