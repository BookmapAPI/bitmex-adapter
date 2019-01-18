package com.bookmap.plugins.layer0.bitmex.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import com.bookmap.plugins.layer0.bitmex.Provider;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import velox.api.layer1.common.Log;
import velox.api.layer1.data.OrderMoveParameters;
import velox.api.layer1.data.OrderType;
import velox.api.layer1.data.SimpleOrderSendParameters;
import velox.api.layer1.layers.utils.OrderBook;

public class TradeConnector {

	private String orderApiKey;
	private String orderApiSecret;
	private Provider provider;

	public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public String getOrderApiKey() {
		return orderApiKey;
	}

	public String getOrderApiSecret() {
		return orderApiSecret;
	}

	public void setOrderApiKey(String orderApiKey) {
		this.orderApiKey = orderApiKey;
	}

	public void setOrderApiSecret(String orderApiSecret) {
		this.orderApiSecret = orderApiSecret;
	}

	public String makeRestGetQuery(String address) {
		String addr = address;
		long moment = ConnectorUtils.getMomentAndTimeToLive();

		Log.info("[bitmex] TradeConnector makeRestGetQuery(xx) moment = " + moment);

		String messageBody = ConnectorUtils.createMessageBody("GET", addr, "",
				moment);
		String signature = ConnectorUtils.generateSignature(orderApiSecret, messageBody);
		String response = null;

		try {
			URL url = new URL(provider.getConnector().getRestApi() + addr);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			// conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("api-expires", Long.toString(moment));
			conn.setRequestProperty("api-key", orderApiKey);
			conn.setRequestProperty("api-signature", signature);

			if (conn.getResponseCode() == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				// conn.disconnect();
				String rateLimitIfExists = ConnectorUtils.processRateLimitHeaders(conn.getHeaderFields());
				if (rateLimitIfExists != null) {
					provider.pushRateLimitWarning(rateLimitIfExists);
				}
				response = sb.toString();
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				Log.info("[bitmex] TradeConnector makeRestGetQery err: " + sb.toString());
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			Log.info("[bitmex] TradeConnector makeRestGetQuery: no response from server");
		} catch (java.net.SocketException e) {
			Log.info("[bitmex] TradeConnector makeRestGetQuery: network is unreachable");
		} catch (IOException e) {
			Log.info("[bitmex] TradeConnector makeRestGetQuery: buffer reading error");
			e.printStackTrace();
		}
		return response;
	}

	private double getPegOffset(String symbol, double stopPrice) {
		BmInstrument instr = provider.getConnector().getActiveInstrumentsMap().get(symbol);
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
		double orderQty = params.size;

		JsonObject json = new JsonObject();
		json.addProperty("symbol", symbol);
		json.addProperty("side", side);
		json.addProperty("orderQty", orderQty);
		json.addProperty("clOrdID", tempOrderId);
//		json.addProperty("clOrdLinkID", clOrdLinkID);
//		json.addProperty("contingencyType", contingencyType);

		/*
		 * https://www.bitmex.com/api/explorer/#!/Order/Order_new Send a
		 * simpleOrderQty instead of an orderQty to create an order denominated
		 * in the underlying currency. This is useful for opening up a position
		 * with 1 XBT of exposure without having to calculate how many contracts
		 * it is.
		 */
		if (orderType == OrderType.MKT) {
			json.addProperty("ordType", "Market");
		} else if (orderType == OrderType.LMT) {
			json.addProperty("ordType", "Limit");
			json.addProperty("price", params.limitPrice);
		} else {// has to do with STP
			json.addProperty("stopPx", params.stopPrice);

			if (orderType == OrderType.STP) {// StopMarket
				json.addProperty("ordType", "Stop");
			} else if (orderType == OrderType.STP_LMT) {
				Log.info("[bitmex] TradeConnector createSendData: STP_LMT trailing step == " + params.trailingStep);
				json.addProperty("ordType", "StopLimit");
				json.addProperty("price", params.limitPrice);
			}

			// used by stops to determine triggering price
			json.addProperty("execInst", "LastPrice");
			if (params.trailingStep > 0) {
				Log.info("[bitmex] TradeConnector createSendData: STP trailing step == " + params.trailingStep);
				json.addProperty("pegPriceType", "TrailingStopPeg");
				json.addProperty("pegOffsetValue", getPegOffset(symbol, params.stopPrice));
			}
		}
		return json;
	}

	public UnitOrder cancelOrder(String orderId) {
		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		String data = json.toString();
		String res = require(GeneralType.ORDER, Method.DELETE, data);
		Log.info("[bitmex] TradeConnector cancelOrder: " + res);
		return null;
	}

	public void cancelOrder(List<String> orderIds) {
		StringBuilder sb = new StringBuilder("");
		sb.append("orderID=");
		for (String orderId : orderIds) {
			sb.append(orderId).append(",");
		}
		sb.setLength(sb.length() - 1);
		String data1 = sb.toString();
		Log.info("[bitmex] TradeConnector cancelOrder (bulk): " + data1);
		require(GeneralType.ORDER, Method.DELETE, data1, true);
	}

	public String resizeOrder(String orderId, long orderQty) {
		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		json.addProperty("leavesQty", orderQty);
		String data = json.toString();
		return data;
	}

	public String resizeOrder(List<String> orderIds, long orderQty) {
		JsonArray array = new JsonArray();
		for (String orderId : orderIds) {
			JsonObject json = new JsonObject();
			json.addProperty("orderID", orderId);
			json.addProperty("leavesQty", orderQty);
			array.add(json);
		}
		String data = "orders=" + array.toString();
		Log.info("[bitmex] TradeConnector resizeOrder (bulk): " + data);
		return data;
	}

	public JsonObject moveOrderJson(OrderMoveParameters params, boolean isStopTriggered) {
		OrderType orderType = OrderType.getTypeFromPrices(params.stopPrice, params.limitPrice);
		JsonObject json = new JsonObject();
		json.addProperty("orderID", params.orderId);
		if (orderType == OrderType.LMT) {
			json.addProperty("price", params.limitPrice);
		} else if (orderType == OrderType.STP) {// StopMarket
			json.addProperty("stopPx", params.stopPrice);
		} else if (orderType == OrderType.STP_LMT) {
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
		String symbol = ConnectorUtils
				.isolateSymbol(provider.getWorkingOrders().get(params.orderId).getInstrumentAlias());
		json.addProperty("pegOffsetValue", getPegOffset(symbol, params.stopPrice));
		return json;
	}

	public String require(GeneralType genType, Method method, String data) {
		return require(genType, method, data, false);
	}

	public String require(GeneralType genType, Method method, String data, boolean isOrderListBeingCanceled) {
		String subPath = ConnectorUtils.subPaths.get(genType);
		String path = provider.getConnector().getRestApi() + subPath;
		long moment = ConnectorUtils.getMomentAndTimeToLive();
		
		Log.info("[bitmex] TradeConnector makeRestGetQuery(xx) moment = " + moment);

		Log.info("[bitmex] TradeConnector require:  sending data => " + data);

		try {
			URL url = new URL(path);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

			if (!method.equals(Method.GET)) {
				// if (method.equals(Method.POST) || method.equals(Method.PUT)){
				conn.setDoOutput(true);
			}
			// arguable at the moment
			conn.setDoOutput(true);

			String messageBody = ConnectorUtils.createMessageBody(ConnectorUtils.methods.get(method), subPath, data,
					moment);
			String signature = ConnectorUtils.generateSignature(orderApiSecret, messageBody);
			conn.setRequestMethod(ConnectorUtils.methods.get(method));
			String contentType = genType.equals(GeneralType.ORDERBULK) || isOrderListBeingCanceled
					? "application/x-www-form-urlencoded" : "application/json";

			conn.setRequestProperty("Content-Type", contentType);
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("api-expires", Long.toString(moment));
			conn.setRequestProperty("api-key", orderApiKey);
			conn.setRequestProperty("api-signature", signature);
			conn.setRequestProperty("Content-Length", Integer.toString(data.getBytes("UTF-8").length));

			OutputStream os = conn.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			osw.write(data);
			osw.flush();
			osw.close();

			String rateLimitIfExists = ConnectorUtils.processRateLimitHeaders(conn.getHeaderFields());
			if (rateLimitIfExists != null) {
				provider.pushRateLimitWarning(rateLimitIfExists);
			}

			if (conn.getResponseCode() != 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				Log.info("[bitmex] TradeConnector require:  response =>" + sb.toString());
				String resp = Provider.testReponseForError(sb.toString());
				return resp;
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			Log.info("[bitmex] TradeConnector require: no response from server");
		} catch (java.net.SocketException e) {
			Log.info("[bitmex] TradeConnector require: network is unreachable");
		} catch (IOException e) {
			Log.info("[bitmex] TradeConnector require: buffer reading error");
			e.printStackTrace();
		}
		return null;
	}

}
