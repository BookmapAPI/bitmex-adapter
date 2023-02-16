package com.bookmap.plugins.layer0.bitmex.adapter;

import com.bookmap.plugins.layer0.bitmex.Provider;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Topic;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.SystemTextMessageType;
import velox.api.layer1.layers.utils.OrderByOrderBook;
import velox.api.layer1.providers.helper.RawDataHelper;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.*;

public class JsonParser {

	public static <T> ArrayList<T> getGenericFromMessage(String input, Class<T> cls) {
		Type type = new TypeToken<MessageGeneric<T>>() {
		}.getType();
		MessageGeneric<T> msg0 = gson.fromJson(input, type);
		ArrayList<T> dataUnits = msg0.getData();
		return dataUnits;
	}

	public static final Gson gson = new GsonBuilder().create();

    private Provider provider;
	private Map<String, BmInstrument> activeInstrumentsMap = new HashMap<>();
	private Set<String> nonInstrumentPartialsParsed = new HashSet<>();
	
	public JsonParser() {
        super();
    }

    public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public void setActiveInstrumentsMap(Map<String, BmInstrument> activeInstrumentsMap) {
		this.activeInstrumentsMap = activeInstrumentsMap;
	}

    public <T> T[] getArrayFromJson(String input, Class<T[]> cls) {
        try {
            return (T[]) new Gson().fromJson(input, cls);
        } catch (JsonSyntaxException e) {
            // An empty array will be returned if unable to parse.
            // If an input contains a warning message from BitMEX
            // (which happens to be pretty often) the message will
            // be shown to a user.
            Log.info("Cannot parse: ", input);
            Log.info("", e);
            
            StringBuilder sb = new StringBuilder();
            sb.append("Unable to parse the input")
            .append(System.lineSeparator());
            
            if (input.contains("<html><body><h1>")) {
                sb.append(input);
            }
            String message = sb.toString();
            provider.adminListeners.forEach(l -> l.onSystemTextMessage(message, SystemTextMessageType.UNCLASSIFIED));
            
            Class<?> componentType = cls.getComponentType();
            @SuppressWarnings("unchecked")
            T[] t = (T[]) Array.newInstance(componentType, 0);
            return t;
        }
    }

	public void parse(String str) {
		try {
		    if (RawDataHelper.isRawDataRecordingEnabled()) {
		        RawDataHelper.sendRawData(str, provider.adminListeners);
		    }
			// first let's find out what kind of object we have here
			ResponseByWebSocket responseWs = (ResponseByWebSocket) gson.fromJson(str, ResponseByWebSocket.class);
			if (responseWs.getTable() == null) {
				if (responseWs.getInfo() != null) {
					return;
				}

				if (responseWs.getStatus() != null && responseWs.getStatus() != 200) {
				    Log.info("JsonParser parser: websocket response status = " + responseWs.getError());
					String errorMessage = responseWs.getError();
					
					if (errorMessage.toUpperCase().contains("Signature not valid".toUpperCase()) ||
					        errorMessage.toUpperCase().contains("Invalid API Key".toUpperCase()) ||
                            errorMessage.toUpperCase().contains("Account does not exist".toUpperCase())) {
                        provider.setLoginSuccessful(false);
                        provider.setAuthFailedReason(errorMessage);
                        provider.getConnector().getWebSocketAuthLatch().countDown();
                    } else {
                        final String messageToShow = errorMessage == null ? str : errorMessage;
                      
                        provider.adminListeners.forEach(l -> l.onSystemTextMessage(messageToShow,
                                SystemTextMessageType.UNCLASSIFIED));
                    }
					return;
				}

				if (responseWs.getSuccess() == true && responseWs.getRequest().getOp().equals("authKey")) {
				    provider.setLoginSuccessful(true);
					provider.getConnector().getWebSocketAuthLatch().countDown();
				}

				if (responseWs.getSuccess() == true && responseWs.getRequest().getOp().equals("unsubscribe")) {
					String symbol = responseWs.getUnsubscribeSymbol();
					if (symbol != null) {
					    Log.info(
								"JsonParser parser: getting unsbscribed from orderBookL2, symbol = " + symbol);
						BmInstrument instr = activeInstrumentsMap.get(symbol);
						instr.clearOrderBook();
					}
				}

				if (responseWs.getSuccess() == null && responseWs.getError() == null && responseWs.getTable() == null
						&& responseWs.getInfo() == null) {
				    Log.info("JsonParser parser: parser fails to parse " + str);
					throw new RuntimeException();
				}

				if (responseWs.getSuccess() != null || responseWs.getInfo() != null) {
				    Log.info("JsonParser parser: service message " + str);
					return;
				}

				if (responseWs.getError() != null) {
				    Log.info("JsonParser parser: error message " + str);
					BmErrorMessage error = new Gson().fromJson(str, BmErrorMessage.class);
					Log.info(error.getMessage());
					return;
				}
				return;
			}

			// 'No object', 'success' and 'error' are excluded already excluded
			// so this must be a 'message' object (that contains 'data', an array
			// of objects)
			Message msg = (Message) gson.fromJson(str, Message.class);

			// skip a messages if it contains empty data
			if (msg.getData() == null) {
			    Log.info("JsonParser parser: data == null =>" + str);
			}

			if (ConnectorUtils.stringToTopic.keySet().contains(msg.getTable())) {
				Topic Topic = ConnectorUtils.stringToTopic.get(msg.getTable());
				preprocessMessage(str, Topic);
			}
		} catch (Exception e) {
		    Log.error("Exception thrown from parser. String is: " + str, e);
			throw new RuntimeException(e);
		}
	}

	/*
	 * setting missing values for dataunits' fields adding missing prices to
	 * <id,intPrice> map updating the orderBook This refers to order book
	 * updates only UnitTrade orders are processed in processTradeMsg method
	 */
	private void processOrderMessage(MessageGeneric<UnitData> msg) {
		for (UnitData unit : msg.getData()) {
			unit.setBid(unit.getSide().equals("Buy"));
			BmInstrument instr = activeInstrumentsMap.get(unit.getSymbol());
			HashMap<Long, Double> pricesMap = instr.getPricesMap();
			String action = msg.getAction();

			//update and delete units contain no price so we need to restore it
			switch (action) {
				case "delete":
				case "update":
					double price = pricesMap.get(unit.getId());
					unit.setPrice(price);
					break;
				default://partial or insert
					pricesMap.put(unit.getId(), unit.getPrice());
			}
			double activeTickSize = instr.getActiveTickSize();
			double defaultTickSize = instr.getTickSize();
			long id = (long) Math.round(unit.getPrice() / defaultTickSize);
			int intPrice = getIntPrice(unit.isBid(), unit.getPrice() / activeTickSize);
			OrderByOrderBook orderBook = instr.getOrderBook();
			long newSize;

			if (orderBook.hasOrder(id)) {
				if (action.equals("delete")) {
					newSize = orderBook.removeOrder(id);
				} else {
					OrderByOrderBook.OrderUpdateResult updateOrder = orderBook.updateOrder(id, intPrice, unit.getSize());
					newSize = updateOrder.toSize;
				}
			} else {
				newSize = orderBook.addOrder(id, unit.isBid(), intPrice, unit.getSize());
			}
			int absoluteSize = (int) Math.min(Integer.MAX_VALUE, newSize);
			unit.setSize(absoluteSize);
			unit.setIntPrice(intPrice);
		}
	}

	private int getIntPrice(boolean isBid, double price){
		double result = isBid
				? Math.floor(price)
				: Math.ceil(price);
		return Math.min(Integer.MAX_VALUE, (int)result);
	}

	private void processTradeUnit(UnitTrade unit) {
		BmInstrument instr = activeInstrumentsMap.get(unit.getSymbol());
		/*
		 * Please note #getIntPrice takes an inverted isBid value as an arg for trades.
		 * We assume a trade is performed by an aggressor so buys must be placed around
		 * the best ask line (rounding up) ad sells around the best bid line (rounding
		 * down) respectively.
		 */
		unit.setBid(unit.getSide().equals("Buy"));
		int intPrice = getIntPrice(!unit.isBid(), unit.getPrice()/instr.getActiveTickSize());
		unit.setIntPrice(intPrice);
	}

	/**
	 * resets orderBooks (both for Bookmap and for BmInstrument) after
	 * disconnect and reconnect For better visualization purposes besAsk and
	 * bestBid will go last in this method and come first in
	 * putBestAskToTheHeadOfList method (see the description for
	 * putBestAskToTheHeadOfList method)
	 **/
	private void resetBookMapOrderBook(BmInstrument instr) {
		String symbol = instr.getSymbol();
		ArrayList<UnitData> units = new ArrayList<>();
		TreeMap<Integer, Long> askMap = instr.getOrderBook().getOrderBook().getAskMap();
		ArrayList<Integer> askList = new ArrayList<>(askMap.keySet());

		for (Integer intPrice : askList) {
			UnitData unit = new UnitData(symbol, 0, "Sell");
			unit.setBid(false);
			unit.setIntPrice(intPrice);
			units.add(unit);		}

		TreeMap<Integer, Long> bidMap = instr.getOrderBook().getOrderBook().getBidMap();
		ArrayList<Integer> bidList = new ArrayList<>(bidMap.keySet());
		for (Integer intPrice : bidList) {
			UnitData unit = new UnitData(symbol, 0, "Buy");
			unit.setBid(true);
			unit.setIntPrice(intPrice);
			units.add(unit);
		}
		MessageGeneric<UnitData> mess = new MessageGeneric<>("orderBookL2", "delete", UnitData.class, units);
		for (UnitData unit : mess.getData()) {
			provider.listenForOrderBookL2(unit);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void preprocessMessage(String str, Topic topic) {
		TopicContainer container = ConnectorUtils.containers.get(topic);
		Type type = container.unitType;
		MessageGeneric<T> msg0 = gson.fromJson(str, type);

		if (msg0.getAction().equals("partial")) {
			nonInstrumentPartialsParsed.add(container.name);
			Log.info("JsonParser preprocessMessage: partial acquired for  " + container.name);

			if (topic.equals(Topic.ORDERBOOKL2)) {
			    ArrayList<UnitData> data = ((MessageGeneric<UnitData>) msg0).getData();
			    if (data.isEmpty()) return;
			    String symbol = data.get(0).getSymbol();
				BmInstrument instr = activeInstrumentsMap.get(symbol);
				nonInstrumentPartialsParsed.add(container.name);
				instr.setOrderBookSnapshotParsed(true);
				Log.info("JsonParser preprocessMessage setOrderBookSnapshotParsed set true for "
						+ instr.getSymbol());
				performOrderBookL2SpecificOpSetOne((MessageGeneric<UnitData>) msg0);
			}
		}

		if (nonInstrumentPartialsParsed.contains(container.name)) {
			if (topic.equals(Topic.ORDER)) {
				performOrderSpecificOp();
				Log.info("JsonParser preprocessMessage: (order)" + str);
			}

			if (msg0.getData().isEmpty()) {
			    Log.info("JsonParser preprocessMessage: skips data == [] => " + str);
				return;
			}

			ArrayList<T> units = (ArrayList<T>) msg0.getData();

			if (topic.equals(Topic.ORDERBOOKL2) && !units.isEmpty()) {
				processOrderMessage((MessageGeneric<UnitData>) msg0);
			}

			if (!units.isEmpty()) {
				dispatchRawUnits(units, container.clazz);
			}

			if (topic.equals(Topic.EXECUTION)) {
			    Log.info("JsonParser parser: execution => " + str);
			}
		}
		return;
	}

	private void performOrderSpecificOp() {
		nonInstrumentPartialsParsed.remove("order");
		Log.info("JsonParser performOrderSpecificOp: 'order' removed from partialsParsed");
		// we need only the snapshot.
		// the rest of info comes from execution Topic.
		// it will be a good idea to get unsubscribed from orders
		// right at this point.
	}

	private void performOrderBookL2SpecificOpSetOne(MessageGeneric<UnitData> msg) {
		BmInstrument instr = activeInstrumentsMap.get(msg.getData().get(0).getSymbol());
		if (!instr.getOrderBook().getOrderBook().getAskMap().isEmpty()) {
			// orderbook is filled already (after reconnect).
			// reset the book after reconnect
			resetBookMapOrderBook(instr);
			instr.resetOrderBook();
		}
	}

	public <T> void dispatchRawUnits(ArrayList<T> units, Class<?> clazz) {
		for (T unit : units) {
			if (clazz == UnitWallet.class) {
				provider.listenForWallet((UnitWallet) unit);
			} else if (clazz == UnitExecution.class) {
                try {
                    provider.listenForExecution((UnitExecution) unit);
                } catch (Exception e) {
                    Log.info("Exception thrown in listenForExecution " + unit.toString() + "\n", e);
                    throw e;
                }
			} else if (clazz == UnitMargin.class) {
				provider.listenForMargin((UnitMargin) unit);
			} else if (clazz == UnitPosition.class) {
				provider.listenForPosition((UnitPosition) unit);
			} else if (clazz == UnitOrder.class) {
				UnitOrder ord = (UnitOrder) unit;
				Log.info("JsonParser dispatchRawUnits: orderId" + ord.getOrderID());
				provider.createBookmapOrder((UnitOrder) unit);
			} else if (clazz == UnitTrade.class) {
				// specific
				processTradeUnit((UnitTrade) unit);
				provider.listenForTrade((UnitTrade) unit);
			} else if (clazz == UnitData.class) {
				provider.listenForOrderBookL2((UnitData) unit);
			}
		}
	}

}
