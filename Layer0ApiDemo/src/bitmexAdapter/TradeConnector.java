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
import java.util.List;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import bitmexAdapter.TradeConnector.GeneralType;
import bitmexAdapter.TradeConnector.Method;
import quickfix.RuntimeError;
import velox.api.layer0.live.Provider;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.OrderInfoBuilder;
import velox.api.layer1.data.OrderMoveParameters;
import velox.api.layer1.data.OrderType;
import velox.api.layer1.data.SimpleOrderSendParameters;
import velox.api.layer1.data.SystemTextMessageType;

public class TradeConnector {

	private static final Gson gson = new GsonBuilder().create();

	public enum GeneralType {
		order, orderBulk, orderAll, instrument, execution, position;
	}

	private static final long requestTimeToLive = 86400000;
	private String orderApiKey;
	private String orderApiSecret;
	public Provider prov;

	private EnumMap<GeneralType, String> subPaths = new EnumMap<GeneralType, String>(GeneralType.class);
	{
		subPaths.put(GeneralType.order, "/api/v1/order");
		subPaths.put(GeneralType.orderBulk, "/api/v1/order/bulk");
		subPaths.put(GeneralType.orderAll, "/api/v1/order/all"); // for
																	// canceling
																	// orders
																	// only
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

	public String getOrderApiKey() {
		Log.info("TR CONN  - APIKEY REQUESTED");
		return orderApiKey;
	}

	public String getOrderApiSecret() {
		Log.info("TR CONN  - APISECRET REQUESTED");
		return orderApiSecret;
	}

	public void setOrderApiKey(String orderApiKey) {
		this.orderApiKey = orderApiKey;
	}

	public void setOrderApiSecret(String orderApiSecret) {
		this.orderApiSecret = orderApiSecret;
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

	public static String generateSignature(String apiSecret, String messageBody) {
		try {
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
			sha256_HMAC.init(secretKey);
			byte[] hash = sha256_HMAC.doFinal(messageBody.getBytes());
			String check = Hex.encodeHexString(hash);
			// System.out.println("signature\t" + check);
			return check;
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
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

	// public String post(String address, String key, String signature, long
	// moment, String data) {
	// System.out.println("url\t" + address);
	// String response = null;
	//
	// try {
	// URL url = new URL(address);
	// HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
	// conn.setDoOutput(true);
	// conn.setRequestMethod("POST");
	// conn.setRequestProperty("Content-Type", "application/json");
	// conn.setRequestProperty("Accept", "application/json");
	// conn.setRequestProperty("api-expires", Long.toString(moment));
	// conn.setRequestProperty("api-key", key);
	// conn.setRequestProperty("api-signature", signature);
	// conn.setRequestProperty("Content-Length",
	// Integer.toString(data.getBytes("UTF-8").length));
	// System.out.println(Integer.toString(data.getBytes("UTF-8").length));
	//
	// OutputStream os = conn.getOutputStream();
	// OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
	// osw.write(data);
	// osw.flush();
	// osw.close();
	//
	// // BufferedWriter out =
	// // new BufferedWriter(new
	// // OutputStreamWriter(conn.getOutputStream()));
	// // out.write(json.toString());
	// // out.close();
	//
	// if (conn.getResponseCode() == 200) {
	// BufferedReader br = new BufferedReader(new
	// InputStreamReader((conn.getInputStream())));
	// StringBuilder sb = new StringBuilder("");
	// String output = null;
	//
	// while ((output = br.readLine()) != null) {
	// sb.append(output);
	// }
	// conn.disconnect();
	// response = sb.toString();
	// } else {
	// BufferedReader br = new BufferedReader(new
	// InputStreamReader((conn.getErrorStream())));
	// StringBuilder sb = new StringBuilder("");
	// String output = null;
	//
	// while ((output = br.readLine()) != null) {
	// sb.append(output);
	// }
	//
	// System.out.println(sb.toString());
	// }
	// } catch (UnknownHostException | NoRouteToHostException e) {
	// // Log.info("NO RESPONSE FROM SERVER");
	// } catch (java.net.SocketException e) {
	// // Log.info("NETWORK IS UNREACHABLE");
	// } catch (IOException e) {
	// // Log.debug("BUFFER READING ERROR");
	// e.printStackTrace();
	// }
	// return response;
	// }

	public static String isolateSymbol(String alias) {
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

	public static JsonObject createSendData(SimpleOrderSendParameters params, OrderType orderType, String tempOrderId,
			String clOrdLinkID, String contingencyType, BmInstrument instr) {
		String symbol = isolateSymbol(params.alias);
		String side = params.isBuy ? "Buy" : "Sell";
		Log.info("****SIDE = " + side);
		// double price = params.limitPrice;
		double orderQty = params.size;

		JsonObject json = new JsonObject();
		json.addProperty("symbol", symbol);
		json.addProperty("side", side);
		// json.addProperty("simpleOrderQty", orderQty);
		json.addProperty("orderQty", orderQty);
		json.addProperty("orderQty", orderQty);
		json.addProperty("clOrdID", tempOrderId);
		if (clOrdLinkID != null) {
			json.addProperty("clOrdLinkID", clOrdLinkID);
		}
		if (contingencyType != null) {
			json.addProperty("contingencyType", contingencyType);
		}

		/*
		 * https://www.bitmex.com/api/explorer/#!/Order/Order_new Send a
		 * simpleOrderQty instead of an orderQty to create an order denominated
		 * in the underlying currency. This is useful for opening up a position
		 * with 1 XBT of exposure without having to calculate how many contracts
		 * it is.
		 */

		if (orderType == OrderType.LMT) {
			json.addProperty("ordType", "Limit");
			json.addProperty("price", params.limitPrice);
		} else if (orderType == OrderType.STP) {// StopMarket
			json.addProperty("ordType", "Stop");
			json.addProperty("stopPx", params.stopPrice);
			json.addProperty("execInst", "LastPrice");// used by stops to
														// determine triggering
														// price

			if (params.trailingStep > 0) {
				Log.info("TR CONN (createSendData) : STP trailing step == " + params.trailingStep);
				json.addProperty("pegPriceType", "TrailingStopPeg");

				double pegOffset;
				if (params.isBuy) {
					pegOffset = params.stopPrice
							- (double) instr.getOrderBook().getBestAskPriceOrNone() * instr.getTickSize();
				} else {
					pegOffset = params.stopPrice
							- (double) instr.getOrderBook().getBestBidPriceOrNone() * instr.getTickSize();
				}
				json.addProperty("pegOffsetValue", pegOffset);
				// json.addProperty("pegOffsetValue", ticksize);
			}

		} else if (orderType == OrderType.STP_LMT) {
			Log.info("TR CONN (createSendData) : STP_LMT trailing step == " + params.trailingStep);
			json.addProperty("ordType", "StopLimit");
			json.addProperty("stopPx", params.stopPrice);
			json.addProperty("price", params.limitPrice);
			json.addProperty("execInst", "LastPrice");// used by stops to
														// determine triggering
														// price

			if (params.trailingStep > 0) {
				Log.info("TR CONN (createSendData) : STP trailing step == " + params.trailingStep);
				json.addProperty("pegPriceType", "TrailingStopPeg");

				double pegOffset;
				if (params.isBuy) {
					pegOffset = params.stopPrice - instr.getOrderBook().getBestAskPriceOrNone();
				} else {
					pegOffset = -params.stopPrice + instr.getOrderBook().getBestBidPriceOrNone();
				}
				json.addProperty("pegOffsetValue", pegOffset);
				// json.addProperty("pegOffsetValue", ticksize);
			}

		}

		return json;
		// String data = json.toString();
		// return data;
	}



//	public void processNewOrderBulk(String data) {
//		String res = require(GeneralType.orderBulk, Method.POST, data);
//		Log.info(res);
//	}

	public BmOrder cancelOrder(String orderId) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		String data = json.toString();

		String res = require(GeneralType.order, Method.DELETE, data);
		Log.info(res);
		// BmOrder[] cancelledOrders = JsonParser.getArrayFromJson(res,
		// BmOrder[].class);
		// return cancelledOrders[0];

		return null; // ?????????????
	}

	public void cancelOrder(List<String> orderIds) {

		StringBuilder sb = new StringBuilder("");
		sb.append("orderID=");
		for (String orderId : orderIds) {
			sb.append(orderId).append(",");
		}
		sb.setLength(sb.length() - 1);

		String data1 = sb.toString();

		Log.info("TR CONN - CANCEL ALL " + data1);
		require(GeneralType.order, Method.DELETE, data1, true);

	}

	public void resizeOrder(String orderId, long orderQty) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		json.addProperty("orderQty", orderQty);
		// json.addProperty("simpleOrderQty", orderQty);
		String data = json.toString();

		require(GeneralType.order, Method.PUT, data);

	}

	public void resizeOrder(List<String> orderIds, long orderQty) {

		JsonArray array = new JsonArray();
		for (String orderId : orderIds) {
			JsonObject json = new JsonObject();
			json.addProperty("orderID", orderId);
			json.addProperty("orderQty", orderQty);
			// String data = json.toString();

			array.add(json);
		}

		String data = array.toString();
		String data1 = "orders=" + data;

		Log.info("TR CONN - RESIZE BULK " + data1);

		require(GeneralType.orderBulk, Method.PUT, data1);

	}

	public BmOrder resizePartiallyFilledOrder(String orderId, long orderQty) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		json.addProperty("leavesQty", orderQty);
		// json.addProperty("simpleOrderQty", orderQty);
		String data = json.toString();

		String res = require(GeneralType.order, Method.PUT, data);
		Log.info(res);
		return (BmOrder) gson.fromJson(res, BmOrder.class);

	}

	public void resizePartiallyFilledOrder(List<String> orderIds, long orderQty) {

		JsonArray array = new JsonArray();
		for (String orderId : orderIds) {
			JsonObject json = new JsonObject();
			json.addProperty("orderID", orderId);
			json.addProperty("leavesQty", orderQty);
			// String data = json.toString();

			array.add(json);
		}

		String data = array.toString();
		String data1 = "orders=" + data;

		Log.info("TR CONN - RESIZE BULK " + data1);
		require(GeneralType.orderBulk, Method.PUT, data1);

	}

	public JsonObject moveOrderJson(OrderMoveParameters params, boolean isStopTriggered) {
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
		return json;
	}

	public JsonObject moveTrailingStepJson(String id, Double newOffset) {
		JsonObject json = new JsonObject();
		json.addProperty("orderID", id);
		json.addProperty("pegOffsetValue", newOffset);
		return json;
	}

	public void moveOrder(String data) {

		String res = require(GeneralType.order, Method.PUT, data);
		Log.info(res);
		// return (BmOrder) gson.fromJson(res, BmOrder.class);
	}

	public void moveOrderBulk(String data) {

		String res = require(GeneralType.orderBulk, Method.PUT, data);
		Log.info(res);
		// return (BmOrder) gson.fromJson(res, BmOrder.class);

	}

	public String require(GeneralType genType, Method method, String data) {
		return require(genType, method, data, false);
	}

	public String require(GeneralType genType, Method method, String data, boolean isOrderListBeingCanceled) {
		String subPath = subPaths.get(genType);
		Log.info("TRCONN PATH: " + prov.connector.restApi);
		Log.info("TRCONN subPath: " + subPath);
		String path = prov.connector.restApi + subPath;
		long moment = getMoment();

		// String response = null;

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

			Log.info("TRCONN SIGNATURE: " + signature);
			Log.info("TRCONN MOMENT: " + moment);
			Log.info("TRCONN API KEY: " + orderApiKey);

			conn.setRequestMethod(methods.get(method));

			String contentType = genType.equals(GeneralType.orderBulk) || isOrderListBeingCanceled
					? "application/x-www-form-urlencoded" : "application/json";

			conn.setRequestProperty("Content-Type", contentType);
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("api-expires", Long.toString(moment));
			conn.setRequestProperty("api-key", orderApiKey);// **************************
			conn.setRequestProperty("api-signature", signature);
			conn.setRequestProperty("Content-Length", Integer.toString(data.getBytes("UTF-8").length));
			Log.info("TRCONN CONT LEN: " + Integer.toString(data.getBytes("UTF-8").length));

			OutputStream os = conn.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			osw.write(data);
			osw.flush();
			osw.close();

			Log.info("TR CONN : require : " + path + "\t" + data);

			if (conn.getResponseCode() != 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}

				Log.info("TR CONN * REQUIRE ASNWER " + sb.toString());

				String resp = Provider.testReponseForError(sb.toString());
				return resp;
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			// Log.info("NO RESPONSE FROM SERVER");
		} catch (java.net.SocketException e) {
			// Log.info("NETWORK IS UNREACHABLE");
		} catch (IOException e) {
			// Log.debug("BUFFER READING ERROR");
			e.printStackTrace();
		}
		return null;
	}

}
