package bitmexAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.codec.binary.Hex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import bitmexAdapter.ConnectorUtils.GeneralType;
import bitmexAdapter.ConnectorUtils.Method;
import velox.api.layer0.live.Provider;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.OrderMoveParameters;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderType;
import velox.api.layer1.data.SimpleOrderSendParameters;
import velox.api.layer1.layers.utils.OrderBook;

public class TradeConnector {

	private String orderApiKey;
	private String orderApiSecret;
	public Provider prov;

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


	private double getPegOffset(String symbol, double stopPrice) {
		BmInstrument instr = prov.connector.getActiveInstrumentsMap().get(symbol);
		OrderBook orderBook = instr.getOrderBook();
		double pegOffset;
		pegOffset = stopPrice
				- (double) orderBook.getBestAskPriceOrNone() * instr.getTickSize();
		return pegOffset;
	}

	public JsonObject createSendData(SimpleOrderSendParameters params, OrderType orderType, String tempOrderId,
			String clOrdLinkID, String contingencyType) {
		String symbol = ConnectorUtils.isolateSymbol(params.alias);
		String side = params.isBuy ? "Buy" : "Sell";
		Log.info("****SIDE = " + side);
		double orderQty = params.size;

		JsonObject json = new JsonObject();
		json.addProperty("symbol", symbol);
		json.addProperty("side", side);
		json.addProperty("orderQty", orderQty);
		json.addProperty("clOrdID", tempOrderId);
		// if (clOrdLinkID != null) {
		json.addProperty("clOrdLinkID", clOrdLinkID);
		// }
		// if (contingencyType != null) {
		json.addProperty("contingencyType", contingencyType);
		// }

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
			// used by stops to determine triggering price
			json.addProperty("execInst", "LastPrice");

			if (params.trailingStep > 0) {
				Log.info("TR CONN (createSendData) : STP trailing step == " + params.trailingStep);
				json.addProperty("pegPriceType", "TrailingStopPeg");
				json.addProperty("pegOffsetValue", getPegOffset(symbol, params.stopPrice));
			}

		} else if (orderType == OrderType.STP_LMT) {
			Log.info("TR CONN (createSendData) : STP_LMT trailing step == " + params.trailingStep);
			json.addProperty("ordType", "StopLimit");
			json.addProperty("stopPx", params.stopPrice);
			json.addProperty("price", params.limitPrice);
			// used by stops to determine triggering price
			json.addProperty("execInst", "LastPrice");
			if (params.trailingStep > 0) {
				Log.info("TR CONN (createSendData) : STP trailing step == " + params.trailingStep);
				json.addProperty("pegPriceType", "TrailingStopPeg");
				json.addProperty("pegOffsetValue", getPegOffset(symbol, params.stopPrice));
			}

		}
		return json;
		
	}
	

	public BmOrder cancelOrder(String orderId) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		String data = json.toString();

		String res = require(GeneralType.ORDER, Method.DELETE, data);
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
		require(GeneralType.ORDER, Method.DELETE, data1, true);

	}

	public void resizeOrder(String orderId, long orderQty) {

		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		// json.addProperty("orderQty", orderQty);
		json.addProperty("leavesQty", orderQty);
		// json.addProperty("simpleOrderQty", orderQty);
		String data = json.toString();

		require(GeneralType.ORDER, Method.PUT, data);
	}

	// public void resizePartiallyFilledOrder(String orderId, long orderQty) {
	//
	// JsonObject json = new JsonObject();
	// json.addProperty("orderID", orderId);
	// json.addProperty("leavesQty", orderQty);
	// // json.addProperty("simpleOrderQty", orderQty);
	// String data = json.toString();
	//
	// String res = require(GeneralType.ORDER, Method.PUT, data);
	// Log.info(res);
	// }

	public void resizeOrder(List<String> orderIds, long orderQty) {

		JsonArray array = new JsonArray();
		for (String orderId : orderIds) {
			JsonObject json = new JsonObject();
			json.addProperty("orderID", orderId);
			// json.addProperty("orderQty", orderQty);
			json.addProperty("orderQty", orderQty);
			// String data = json.toString();

			array.add(json);
		}

		String data = array.toString();
		String data1 = "orders=" + data;

		Log.info("TR CONN - RESIZE BULK " + data1);

		require(GeneralType.ORDERBULK, Method.PUT, data1);

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
		require(GeneralType.ORDERBULK, Method.PUT, data1);

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

	public JsonObject moveTrailingStepJson(OrderMoveParameters params) {
		JsonObject json = new JsonObject();
		json.addProperty("orderID", params.orderId);
		String symbol = ConnectorUtils.isolateSymbol(prov.workingOrders.get(params.orderId).getInstrumentAlias());
		json.addProperty("pegOffsetValue", getPegOffset(symbol, params.stopPrice));
		return json;
	}

	public String require(GeneralType genType, Method method, String data) {
		return require(genType, method, data, false);
	}

	public String require(GeneralType genType, Method method, String data, boolean isOrderListBeingCanceled) {
		String subPath = ConnectorUtils.subPaths.get(genType);
		// Log.info("TRCONN PATH: " + prov.connector.restApi);
		// Log.info("TRCONN subPath: " + subPath);
		String path = prov.connector.restApi + subPath;
		long moment = ConnectorUtils.getMomentAndTimeToLive();

		try {
			URL url = new URL(path);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

			if (!method.equals(Method.GET)) {
				// if (method.equals(Method.POST) || method.equals(Method.PUT)){
				conn.setDoOutput(true);
			}

			// !!!!!!!!!1 КОСТЫЛЬ
			conn.setDoOutput(true);

			String messageBody = ConnectorUtils.createMessageBody(ConnectorUtils.methods.get(method), subPath, data,
					moment);
			String signature = ConnectorUtils.generateSignature(orderApiSecret, messageBody);

			// Log.info("TRCONN SIGNATURE: " + signature);
			// Log.info("TRCONN MOMENT: " + moment);
			// Log.info("TRCONN API KEY: " + orderApiKey);

			conn.setRequestMethod(ConnectorUtils.methods.get(method));

			String contentType = genType.equals(GeneralType.ORDERBULK) || isOrderListBeingCanceled
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
