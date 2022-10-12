package com.bookmap.plugins.layer0.bitmex.adapter;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.bookmap.plugins.layer0.bitmex.Provider;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import velox.api.layer1.common.Log;
import velox.api.layer1.data.OrderDuration;
import velox.api.layer1.data.OrderMoveParameters;
import velox.api.layer1.data.OrderType;
import velox.api.layer1.data.SimpleOrderSendParameters;
import velox.api.layer1.layers.utils.OrderBook;

public class TradeConnector {

    HttpClientHolder clientHolder;
	private String orderApiKey;
	private String orderApiSecret;
	private Provider provider;

    public TradeConnector(HttpClientHolder clientHolder) {
        super();
        this.clientHolder = clientHolder;
    }

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

	private double getPegOffset(String symbol, double stopPrice) {
		BmInstrument instr = provider.getConnector().getActiveInstrumentsMap().get(symbol);
		OrderBook orderBook = instr.getOrderBook().getOrderBook();
		double pegOffset;
		pegOffset = stopPrice
				- (double) orderBook.getBestAskPriceOrNone() * instr.getActiveTickSize();
		return pegOffset;
	}

	public JsonObject createSendData(SimpleOrderSendParameters params, OrderType orderType, String clOrdId,
			String clOrdLinkID, String contingencyType) {
		String symbol = ConnectorUtils.isolateSymbol(params.alias);
		String side = params.isBuy ? "Buy" : "Sell";
		double orderQty = params.size;

		JsonObject json = new JsonObject();
		json.addProperty("symbol", symbol);
		json.addProperty("side", side);
		json.addProperty("orderQty", orderQty);
		json.addProperty("clOrdID", clOrdId);
		json.addProperty("text", "bookmap");
		
		JsonArray execInst = new JsonArray();
		
		OrderDuration duration = params.duration;
		
		if (ConnectorUtils.bitmexOrderDurations.contains(duration)) {
			if (duration.equals(OrderDuration.GTC_PO)) {
				json.addProperty("timeInForce", ConnectorUtils.bitmexOrderDurationsValues.get(OrderDuration.GTC));
				execInst.add(ConnectorUtils.GtcPoExecutionalInstruction);
			} else {
				json.addProperty("timeInForce", ConnectorUtils.bitmexOrderDurationsValues.get(duration));
			}
		}
				
		/*
		 * These lines were commented out when BitMEX announced
		 * contingent orders deprecation
		 * https://blog.bitmex.com/api_announcement/deprecation-of-contingent-orders/
		 * 
		 * json.addProperty("clOrdLinkID", clOrdLinkID);
		 * json.addProperty("contingencyType", contingencyType);
		 */
		
		/**
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
				Log.info("TradeConnector createSendData: STP_LMT trailing step == " + params.trailingStep);
				json.addProperty("ordType", "StopLimit");
				json.addProperty("price", params.limitPrice);
			}

			// used by stops to determine triggering price
			execInst.add("LastPrice");
			if (params.trailingStep > 0) {
			    Log.info("TradeConnector createSendData: STP trailing step == " + params.trailingStep);
				json.addProperty("pegPriceType", "TrailingStopPeg");
				json.addProperty("pegOffsetValue", getPegOffset(symbol, params.stopPrice));
			}
		}
		
		if (execInst.size() > 0){
			json.add("execInst", execInst);
		}
		return json;
	}

	public void cancelOrder(String orderId) {
		JsonObject json = new JsonObject();
		json.addProperty("orderID", orderId);
		String data = json.toString();
		Pair<Boolean, String> response = require(GeneralType.ORDER, Method.DELETE, data);
		Log.info("TradeConnector cancelOrder: " + response);
	}

	public void cancelOrder(List<String> orderIds) {
		StringBuilder sb = new StringBuilder("");
		sb.append("orderID=");
		for (String orderId : orderIds) {
			sb.append(orderId).append(",");
		}
		sb.setLength(sb.length() - 1);
		String data1 = sb.toString();
		Log.info("TradeConnector cancelOrder (bulk): " + data1);
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
		Log.info("TradeConnector resizeOrder (bulk): " + data);
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
		String clientId = provider.getClientId(params.orderId);
		String symbol = ConnectorUtils
				.isolateSymbol(provider.getAlias(clientId));
		json.addProperty("pegOffsetValue", getPegOffset(symbol, params.stopPrice));
		return json;
	}

	public Pair<Boolean, String> require(GeneralType genType, Method method, String data) {
	    return clientHolder.makeRequest(genType, method, data);
	}

	public Pair<Boolean, String> require(GeneralType genType, Method method, String data, boolean isOrderListBeingCanceled) {
	    return clientHolder.makeRequest(genType, method, data, isOrderListBeingCanceled);
	}
	
	public Pair<Boolean, String> require(GeneralType genType, Method method, String data, boolean isOrderListBeingCanceled, String requestParameters) {
	    return clientHolder.makeRequest(genType, method, data, isOrderListBeingCanceled, requestParameters);
	}

}
