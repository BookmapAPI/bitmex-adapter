package com.bookmap.plugins.layer0.bitmex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.bookmap.plugins.layer0.bitmex.adapter.BmConnector;
import com.bookmap.plugins.layer0.bitmex.adapter.BmInstrument;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.bookmap.plugins.layer0.bitmex.adapter.JsonParser;
import com.bookmap.plugins.layer0.bitmex.adapter.ResponseByRest;
import com.bookmap.plugins.layer0.bitmex.adapter.TradeConnector;
import com.bookmap.plugins.layer0.bitmex.adapter.UnitData;
import com.bookmap.plugins.layer0.bitmex.adapter.UnitExecution;
import com.bookmap.plugins.layer0.bitmex.adapter.UnitMargin;
import com.bookmap.plugins.layer0.bitmex.adapter.UnitOrder;
import com.bookmap.plugins.layer0.bitmex.adapter.UnitPosition;
import com.bookmap.plugins.layer0.bitmex.adapter.UnitWallet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import velox.api.layer0.annotations.Layer0LiveModule;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.Layer1ApiAdminListener;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.BalanceInfo;
import velox.api.layer1.data.BmSimpleHistoricalDataInfo;
import velox.api.layer1.data.DisconnectionReason;
import velox.api.layer1.data.ExecutionInfo;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.Layer1ApiProviderSupportedFeatures;
import velox.api.layer1.data.Layer1ApiProviderSupportedFeaturesBuilder;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.LoginFailedReason;
import velox.api.layer1.data.OcoOrderSendParameters;
import velox.api.layer1.data.OrderCancelParameters;
import velox.api.layer1.data.OrderDuration;
import velox.api.layer1.data.OrderInfoBuilder;
import velox.api.layer1.data.OrderMoveParameters;
import velox.api.layer1.data.OrderResizeParameters;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderStatus;
import velox.api.layer1.data.OrderType;
import velox.api.layer1.data.OrderUpdateParameters;
import velox.api.layer1.data.SimpleOrderSendParameters;
import velox.api.layer1.data.StatusInfo;
import velox.api.layer1.data.SubscribeInfo;
import velox.api.layer1.data.SystemTextMessageType;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.data.UserPasswordDemoLoginData;
import velox.api.layer1.layers.utils.OrderBook;

@Layer1ApiVersion(Layer1ApiVersionValue.VERSION1)
@Layer0LiveModule(shortName = "MEX", fullName = "BitMEX")
public class Provider extends ExternalLiveBaseProvider {

	private BmConnector connector;
	private TradeConnector tradeConnector;
	private String tempClientId;
	private HashMap<String, OrderInfoBuilder> workingOrders = new HashMap<>();

	private List<OrderInfoBuilder> pendingOrders = new ArrayList<>();
	private long orderCount = 0;
	private long orderOcoCount = 0;
	private boolean isCredentialsEmpty = false;

	/*
	 * for ocoOrders Map <clOrdLinkID, List <realIds>> Map<realid,
	 * clOrderLinkID>
	 */
	private Map<String, List<String>> LinkIdToRealIdsMap = new HashMap<>();
	private Map<String, String> RealToLinkIdMap = new HashMap<>();
	private Set<String> bracketParents = new HashSet<>();

	// <id, trailingStep>
	private Map<String, Double> trailingStops = new HashMap<>();
	private List<String> batchCancels = new LinkedList<>();
	private Map<String, BalanceInfo.BalanceInCurrency> balanceMap = new HashMap<>();

	private CopyOnWriteArrayList<SubscribeInfo> knownInstruments = new CopyOnWriteArrayList<>();

	protected class Instrument {
		protected final String alias;
		protected final double pips;

		public Instrument(String alias, double pips) {
			this.alias = alias;
			this.pips = pips;
		}
	}

	protected HashMap<String, Instrument> instruments = new HashMap<>();

	// This thread will perform data generation.
	private Thread providerThread = null;
	private Thread connectorThread = null;

	public boolean isCredentialsEmpty() {
		return isCredentialsEmpty;
	}

	public HashMap<String, OrderInfoBuilder> getWorkingOrders() {
		return workingOrders;
	}

	public BmConnector getConnector() {
		return connector;
	}

	public List<SubscribeInfo> getKnownInstruments() {
		return knownInstruments;
	}

	public void setKnownInstruments(CopyOnWriteArrayList<SubscribeInfo> knownInstruments) {
		this.knownInstruments = knownInstruments;
	}

	/**
	 * <p>
	 * Generates alias from symbol, exchange and type of the instrument. Alias
	 * is a unique identifier for the instrument, but it's also used in many
	 * places in UI, so it should also be easily readable.
	 * </p>
	 * <p>
	 * Note, that you don't have to use all 3 fields. You can just ignore some
	 * of those, for example use symbol only.
	 * </p>
	 */
	private static String createAlias(String symbol, String exchange, String type) {
		return symbol;
	}

	public static String testReponseForError(String str) throws JsonSyntaxException {
		ResponseByRest answ = (ResponseByRest) JsonParser.gson.fromJson(str, ResponseByRest.class);

		if (answ.getError() != null) {
			return answ.getError().getMessage();
		}
		return null;
	}

	@Override
	public void subscribe(SubscribeInfo subscribeInfo) {
		final String symbol = subscribeInfo.symbol;
		final String exchange = subscribeInfo.exchange;
		final String type = subscribeInfo.type;

		Log.info("[bitmex] Provider subscribe");
		String alias = createAlias(symbol, exchange, type);
		// Since instruments also will be accessed from the data generation
		// thread, synchronization is required
		//
		// No need to worry about calling listener from synchronized block,
		// since those will be processed asynchronously
		synchronized (instruments) {

			if (instruments.containsKey(alias)) {
				instrumentListeners.forEach(l -> l.onInstrumentAlreadySubscribed(symbol, exchange, type));
			} else {
				// We are performing subscription synchronously for simplicity,
				// but if subscription process takes long it's better to do it
				// asynchronously (e.g use Executor)

				// This is delivered after REST query response
				// connector.getWebSocketStartingLatch();//why?
				HashMap<String, BmInstrument> activeBmInstruments = connector.getActiveInstrumentsMap();
				Set<String> set = new HashSet<>();

				synchronized (activeBmInstruments) {
					if (activeBmInstruments.isEmpty()) {
						try {
							// waiting for the instruments map to be filled...
							activeBmInstruments.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					for (String key : activeBmInstruments.keySet()) {
						set.add(key);// copying map's keyset to a new set
					}
				}

				if (set.contains(symbol)) {
					try {
						connector.getWebSocketStartingLatch().await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					BmInstrument instr = activeBmInstruments.get(symbol);
					double pips = instr.getTickSize();

					final Instrument newInstrument = new Instrument(alias, pips);
					instruments.put(alias, newInstrument);
					final InstrumentInfo instrumentInfo = new InstrumentInfo(symbol, exchange, type, newInstrument.pips,
							1, "", false);

					instrumentListeners.forEach(l -> l.onInstrumentAdded(alias, instrumentInfo));
					connector.subscribe(instr);
				} else {
					instrumentListeners.forEach(l -> l.onInstrumentNotFound(symbol, exchange, type));
				}
			}
		}
	}

	@Override
	public void unsubscribe(String alias) {

		synchronized (instruments) {
			if (instruments.remove(alias) != null) {
				instrumentListeners.forEach(l -> l.onInstrumentRemoved(alias));
			}
		}
		BmInstrument instr = connector.getActiveInstrumentsMap().get(alias);
		connector.unSubscribe(instr);
	}

	@Override
	public String formatPrice(String alias, double price) {
		// Use default Bookmap price formatting logic for simplicity.
		// Values returned by this method will be used on price axis and in few
		// other places.
		double pips;
		synchronized (instruments) {
			pips = instruments.get(alias).pips;
		}
		return formatPriceDefault(pips, price);
	}

	@Override
	public void sendOrder(OrderSendParameters orderSendParameters) {
		String data;
		GeneralType genType;

		if (orderSendParameters.getClass() == OcoOrderSendParameters.class) {// OCO
			OcoOrderSendParameters ocoParams = (OcoOrderSendParameters) orderSendParameters;
			data = createOcoOrdersStringData(ocoParams.orders);
			genType = GeneralType.ORDERBULK;
		} else {
			SimpleOrderSendParameters simpleParams = (SimpleOrderSendParameters) orderSendParameters;

			if (isBracketOrder(simpleParams)) {// Bracket
				SimpleOrderSendParameters stopLoss = createStopLossFromParameters(simpleParams);
				SimpleOrderSendParameters takeProfit = createTakeProfitFromParameters(simpleParams);
				data = createBracketOrderStringData(simpleParams, stopLoss, takeProfit);
				genType = GeneralType.ORDERBULK;
			} else {// Single order otherwise
				JsonObject json = prepareSimpleOrder(simpleParams, null, null);
				data = json.toString();
				genType = GeneralType.ORDER;
			}
		}

		String response = tradeConnector.require(genType, Method.POST, data);
		passCancelMessageIfNeededAndClearPendingList(response);
		Log.info("[bitmex] Provider sendOrder: response = " + response);
	}

	private void passCancelMessageIfNeededAndClearPendingList(String response) {
		synchronized (pendingOrders) {
			if (response != null) {// if bitmex responds with an error
				for (OrderInfoBuilder builder : pendingOrders) {
					rejectOrder(builder, response);
				}
			}
			// should be cleared anyway
			pendingOrders.clear();
		}
	}

	private boolean isBracketOrder(SimpleOrderSendParameters simpleParams) {
		/*
		 * These lines were commented out when BitMEX announced contingent
		 * orders deprecation
		 * https://blog.bitmex.com/api_announcement/deprecation-of-contingent-
		 * orders/
		 * 
		 * return simpleParams.takeProfitOffset != 0 && simpleParams.stopLossOffset != 0;
		 */
		return false;
	}

	private SimpleOrderSendParameters createStopLossFromParameters(SimpleOrderSendParameters simpleParams) {
		String symbol = ConnectorUtils.isolateSymbol(simpleParams.alias);
		BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);
		double ticksize = bmInstrument.getTickSize();
		int offsetMultiplier = simpleParams.isBuy ? 1 : -1;

		double limitPriceChecked = checkLImitPriceForBracket(simpleParams, bmInstrument);

		SimpleOrderSendParameters stopLoss = new SimpleOrderSendParameters(
				simpleParams.alias,
				!simpleParams.isBuy, // !
				simpleParams.size,
				simpleParams.duration,
				Double.NaN, // limitPrice
				limitPriceChecked - offsetMultiplier * simpleParams.stopLossOffset * ticksize, // stopPrice
				simpleParams.sizeMultiplier);
		return stopLoss;
	}

	private SimpleOrderSendParameters createTakeProfitFromParameters(SimpleOrderSendParameters simpleParams) {
		String symbol = ConnectorUtils.isolateSymbol(simpleParams.alias);
		BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);
		double ticksize = bmInstrument.getTickSize();
		int offsetMultiplier = simpleParams.isBuy ? 1 : -1;
		double limitPriceChecked = checkLImitPriceForBracket(simpleParams, bmInstrument);

		SimpleOrderSendParameters takeProfit = new SimpleOrderSendParameters(
				simpleParams.alias,
				!simpleParams.isBuy, // !
				simpleParams.size,
				simpleParams.duration,
				limitPriceChecked + offsetMultiplier * simpleParams.takeProfitOffset * ticksize, // limitPrice
				Double.NaN, // stopPrice
				simpleParams.sizeMultiplier);
		return takeProfit;
	}

	private double checkLImitPriceForBracket(SimpleOrderSendParameters simpleParams, BmInstrument bmInstrument) {
		double limitPriceChecked = simpleParams.limitPrice;
		if (Double.isNaN(simpleParams.limitPrice)) {
			OrderBook orderBook = bmInstrument.getOrderBook();
			limitPriceChecked = simpleParams.isBuy ? orderBook.getBestAskPriceOrNone() * bmInstrument.getTickSize()
					: orderBook.getBestBidPriceOrNone() * bmInstrument.getTickSize();
		}
		return limitPriceChecked;
	}

	private String createOcoOrdersStringData(List<SimpleOrderSendParameters> ordersList) {
		String contingencyType = "OneCancelsTheOther";
		String clOrdLinkID = System.currentTimeMillis() + "-LINKED-" + orderOcoCount++;

		JsonArray array = new JsonArray();
		for (SimpleOrderSendParameters simpleParams : ordersList) {
			JsonObject json = prepareSimpleOrder(simpleParams, clOrdLinkID, contingencyType);
			array.add(json);
		}
		String data = "orders=" + array.toString();
		return data;
	}

	private String createBracketOrderStringData(SimpleOrderSendParameters simpleParams,
			SimpleOrderSendParameters stopLoss,
			SimpleOrderSendParameters takeProfit) {
		String clOrdLinkID = System.currentTimeMillis() + "-LINKED-" + orderOcoCount++;

		JsonArray array = new JsonArray();
		array.add(prepareSimpleOrder(simpleParams, clOrdLinkID, "OneTriggersTheOther"));
		array.add(prepareSimpleOrder(stopLoss, clOrdLinkID, "OneCancelsTheOther"));
		array.add(prepareSimpleOrder(takeProfit, clOrdLinkID, "OneCancelsTheOther"));
		String data = "orders=" + array.toString();
		return data;
	}

	private JsonObject prepareSimpleOrder(SimpleOrderSendParameters simpleParameters, String clOrdLinkID,
			String contingencyType) {
		// Detecting order type
		OrderType orderType = OrderType.getTypeFromPrices(simpleParameters.stopPrice, simpleParameters.limitPrice);
		Log.info("[bitmex] Provider prepareSimpleOrder: orderType = " + orderType.toString());
		String tempOrderId = System.currentTimeMillis() + "-temp-" + orderCount++;
		final OrderInfoBuilder builder = new OrderInfoBuilder(simpleParameters.alias, tempOrderId,
				simpleParameters.isBuy, orderType, simpleParameters.clientId, simpleParameters.doNotIncrease);

		// You need to set these fields, otherwise Bookmap might not handle
		// order correctly
		builder.setStopPrice(simpleParameters.stopPrice)
				.setLimitPrice(simpleParameters.limitPrice)
				.setUnfilled(simpleParameters.size)
				.setDuration(simpleParameters.duration)
				.setStatus(OrderStatus.PENDING_SUBMIT);

		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		// Marking all fields as unchanged, since they were just reported and
		// fields will be marked as changed automatically when modified.
		builder.markAllUnchanged();

		/*
		 * pending orders are added to the list to cancel them later if BitMEX
		 * reports an error trying placing orders
		 */
		synchronized (pendingOrders) {
			pendingOrders.add(builder);
		}

		Log.info("[bitmex] Provider prepareSimpleOrder: getting sent to bitmex");
		synchronized (workingOrders) {
			workingOrders.put(builder.getOrderId(), builder);
		}

		JsonObject json = tradeConnector.createSendData(simpleParameters, orderType, tempOrderId, clOrdLinkID,
				contingencyType);
		return json;
	}

	public void rejectOrder(OrderInfoBuilder builder, String reas) {
		String reason = "The order was rejected: \n" + reas;
		Log.info("[bitmex] Provider rejectOrder");
		/*
		 * Necessary fields are already populated, so just change status to
		 * rejected and send
		 */
		builder.setStatus(OrderStatus.REJECTED);
		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		builder.markAllUnchanged();

		// Provider can complain to user here explaining what was done wrong
		adminListeners.forEach(l -> l.onSystemTextMessage(reason,
				SystemTextMessageType.ORDER_FAILURE));
	}

	@Override
	public void updateOrder(OrderUpdateParameters orderUpdateParameters) {
		if (orderUpdateParameters.getClass() == OrderCancelParameters.class) {
			OrderCancelParameters orderCancelParameters = (OrderCancelParameters) orderUpdateParameters;
			Log.info("[bitmex] Provider updateOrder: (cancel) id=" + orderCancelParameters.orderId);
			passCancelParameters(orderCancelParameters);
		} else if (orderUpdateParameters.getClass() == OrderResizeParameters.class) {
			Log.info("[bitmex] Provider updateOrder: (resize)");
			OrderResizeParameters orderResizeParameters = (OrderResizeParameters) orderUpdateParameters;
			passResizeParameters(orderResizeParameters);
		} else if (orderUpdateParameters.getClass() == OrderMoveParameters.class) {
			Log.info("[bitmex] Provider updateOrder: (move)");
			OrderMoveParameters orderMoveParameters = (OrderMoveParameters) orderUpdateParameters;

			boolean isBracketParent;
			synchronized (bracketParents) {
				isBracketParent = bracketParents.contains(orderMoveParameters.orderId);
			}

			boolean isTrailingStop;
			synchronized (trailingStops) {
				isTrailingStop = trailingStops.containsKey(orderMoveParameters.orderId);
			}

			if (isBracketParent) {
				passBracketMoveParameters(orderMoveParameters);
			} else if (isTrailingStop) {
				// trailing stop
				JsonObject json = tradeConnector.moveTrailingStepJson(orderMoveParameters);
				tradeConnector.require(GeneralType.ORDER, Method.PUT, json.toString());
			} else {// single order
				boolean isStopTriggered;
				synchronized (workingOrders) {
					isStopTriggered = workingOrders.get(orderMoveParameters.orderId).isStopTriggered();
				}
				JsonObject json = tradeConnector.moveOrderJson(orderMoveParameters, isStopTriggered);
				tradeConnector.require(GeneralType.ORDER, Method.PUT, json.toString());
			}
		} else {
			throw new UnsupportedOperationException("Unsupported order type");
		}

	}

	private void passCancelParameters(OrderCancelParameters orderCancelParameters) {
		if (orderCancelParameters.batchEnd == true) {
			/*
			 * This is the end of the batch or a single cancel. But if this
			 * order is an OCO or Bracket component we need to cancel the whole
			 * OCO or Bracket
			 */
			if (batchCancels.size() == 0) {
				/*
				 * the batch list is empty so this is a single order if an order
				 * is a part of OCO or Bracket we have to cancel all orders with
				 * the same linkedId
				 */
				boolean isLinkedOrder;
				synchronized (RealToLinkIdMap) {
					isLinkedOrder = RealToLinkIdMap.containsKey(orderCancelParameters.orderId);
				}

				if (isLinkedOrder) {
					String clOrdLinkID;
					synchronized (RealToLinkIdMap) {
						clOrdLinkID = RealToLinkIdMap.get(orderCancelParameters.orderId);
					}
					List<String> bunchOfOrdersToCancel;
					synchronized (LinkIdToRealIdsMap) {
						bunchOfOrdersToCancel = LinkIdToRealIdsMap.get(clOrdLinkID);
					}
					tradeConnector.cancelOrder(bunchOfOrdersToCancel);
					Log.info("[bitmex] Provider passCancelParameters: (batch cancel component)");
				} else {
					// finally, true single order
					tradeConnector.cancelOrder(orderCancelParameters.orderId);
					Log.info("[bitmex] Provider passCancelParameters: (single cancel)");
				}
			} else {
				/*
				 * This is the batch end. We add cancel to the list then perform
				 * canceling then clear the list
				 */
				batchCancels.add(orderCancelParameters.orderId);
				tradeConnector.cancelOrder(batchCancels);
				batchCancels.clear();
				Log.info("[bitmex] Provider passCancelParameters: (batch cancel performed)");

			}
		} else {/*
				 * this is not the end of batch so just add it to the list
				 */
			batchCancels.add(orderCancelParameters.orderId);
		}
	}

	private void passResizeParameters(OrderResizeParameters orderResizeParameters) {
		int newSize = orderResizeParameters.size;
		OrderInfoBuilder builder;
		synchronized (workingOrders) {
			builder = workingOrders.get(orderResizeParameters.orderId);
		}
		List<String> pendingIds = new ArrayList<>();
		String data;
		GeneralType type;

		boolean isLinkedOrder;
		synchronized (RealToLinkIdMap) {
			isLinkedOrder = RealToLinkIdMap.containsKey(builder.getOrderId());
		}

		if (!isLinkedOrder) {
			// single order
			pendingIds.add(builder.getOrderId());
			type = GeneralType.ORDER;
			data = tradeConnector.resizeOrder(builder.getOrderId(), newSize);
		} else { // ***** OCO
			List<String> otherIds = getOtherLinkedOrdersId(builder.getOrderId());
			pendingIds.addAll(otherIds);
			type = GeneralType.ORDERBULK;
			data = tradeConnector.resizeOrder(otherIds, newSize);
		}
		setPendingStatus(pendingIds, OrderStatus.PENDING_MODIFY);
		String response = tradeConnector.require(type, Method.PUT, data);
		passCancelMessageIfNeededAndClearPendingListForResize(pendingIds, response);
		Log.info("[bitmex] Provider passResizeParameters: server response" + response);
	}

	private void setPendingStatus(List<String> pendingIds, OrderStatus status) {
		for (String id : pendingIds) {
			OrderInfoBuilder builder;
			synchronized (workingOrders) {
				builder = workingOrders.get(id);
			}
			builder.setStatus(status);
			tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
			builder.markAllUnchanged();
		}
	}

	// temporary solution
	private void passCancelMessageIfNeededAndClearPendingListForResize(List<String> pendingIds, String response) {
		if (response != null) {// if bitmex responds with an error
			adminListeners.forEach(l -> l.onSystemTextMessage(response,
					SystemTextMessageType.ORDER_FAILURE));

			for (String id : pendingIds) {
				OrderInfoBuilder builder;
				synchronized (workingOrders) {
					builder = workingOrders.get(id);
				}
				builder.setStatus(OrderStatus.WORKING);
				tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
				builder.markAllUnchanged();
			}
		}
		// should be cleared anyway
		pendingIds.clear();
	}

	private List<String> getBracketChildren(String parentId) {
		List<String> brackets;
		String clOrdLinkId;

		synchronized (RealToLinkIdMap) {
			clOrdLinkId = RealToLinkIdMap.get(parentId);
		}

		synchronized (LinkIdToRealIdsMap) {
			brackets = LinkIdToRealIdsMap.get(clOrdLinkId);
		}

		List<String> children = new ArrayList<>();
		for (String id : brackets) {
			if (!id.equals(parentId)) {
				children.add(id);
			}
		}

		if (children.size() != 2) {
			throw new RuntimeException("Bracket children count != 2");
		}
		return children;
	}

	private void passBracketMoveParameters(OrderMoveParameters orderMoveParameters) {
		List<String> children = getBracketChildren(orderMoveParameters.orderId);
		double difference = getDifference(orderMoveParameters);
		OrderMoveParameters moveParamsOne = getIndividualMoveParameters(children.get(0), difference);
		OrderMoveParameters moveParamsTwo = getIndividualMoveParameters(children.get(1), difference);

		JsonArray array = new JsonArray();
		boolean isParentStopTriggered;
		boolean isChildZeroStopTriggered;
		boolean isChildOneStopTriggered;
		synchronized (workingOrders) {
			isParentStopTriggered = workingOrders.get(orderMoveParameters.orderId).isStopTriggered();
			isChildZeroStopTriggered = workingOrders.get(children.get(0)).isStopTriggered();
			isChildOneStopTriggered = workingOrders.get(children.get(1)).isStopTriggered();
		}
		array.add(tradeConnector.moveOrderJson(orderMoveParameters, isParentStopTriggered));
		array.add(tradeConnector.moveOrderJson(moveParamsOne, isChildZeroStopTriggered));
		array.add(tradeConnector.moveOrderJson(moveParamsTwo, isChildOneStopTriggered));
		String data = "orders=" + array.toString();
		tradeConnector.require(GeneralType.ORDERBULK, Method.PUT, data);
	}

	private double getDifference(OrderMoveParameters orderMoveParameters) {
		double difference = 0.0;
		synchronized (workingOrders) {
			if (!Double.isNaN(orderMoveParameters.limitPrice)) {
				difference += orderMoveParameters.limitPrice
						- workingOrders.get(orderMoveParameters.orderId).getLimitPrice();
			}
			if (!Double.isNaN(orderMoveParameters.stopPrice)) {
				difference += orderMoveParameters.stopPrice
						- workingOrders.get(orderMoveParameters.orderId).getStopPrice();
			}
		}
		return difference;
	}

	private OrderMoveParameters getIndividualMoveParameters(String id, double finiteDifference) {
		double stopPrice;
		double limitPrice;
		synchronized (workingOrders) {
			stopPrice = workingOrders.get(id).getStopPrice();
			limitPrice = workingOrders.get(id).getLimitPrice();
		}

		OrderMoveParameters moveParams = new OrderMoveParameters(id,
				stopPrice + finiteDifference, limitPrice + finiteDifference);
		return moveParams;
	}

	private List<String> getOtherLinkedOrdersId(String realId) {
		String ocoId;
		synchronized (RealToLinkIdMap) {
			ocoId = RealToLinkIdMap.get(realId);
		}

		List<String> otherIds;
		synchronized (LinkIdToRealIdsMap) {
			otherIds = LinkIdToRealIdsMap.get(ocoId);
		}
		return otherIds;
	}

	@Override
	public void login(LoginData loginData) {
		UserPasswordDemoLoginData userPasswordDemoLoginData = (UserPasswordDemoLoginData) loginData;
		// If connection process takes a while then it's better to do it in
		// separate thread
		providerThread = new Thread(() -> handleLogin(userPasswordDemoLoginData));
		providerThread.setName("-> INSTRUMENT");
		providerThread.start();
	}

	private void handleLogin(UserPasswordDemoLoginData userPasswordDemoLoginData) {
		Log.info("[bitmex] Provider handleLogin");
		// With real connection provider would attempt establishing connection
		// here.

		// there is no need in password check for demo purposes
		boolean isValid = !userPasswordDemoLoginData.password.equals("")
				&& !userPasswordDemoLoginData.user.equals("") == true;

		isCredentialsEmpty = userPasswordDemoLoginData.password.equals("")
				&& userPasswordDemoLoginData.user.equals("") == true;

		boolean isOneCredentialEmpty = !isCredentialsEmpty && !isValid;

		if (isValid || isCredentialsEmpty) {

			Log.info("[bitmex] Provider handleLogin: credentials valid or empty");

			connector = new BmConnector();
			tradeConnector = new TradeConnector();
			tradeConnector.setProvider(this);
			tradeConnector.setOrderApiKey(userPasswordDemoLoginData.user);
			tradeConnector.setOrderApiSecret(userPasswordDemoLoginData.password);
			// if (isValid) {
			// Report succesful login
			adminListeners.forEach(Layer1ApiAdminListener::onLoginSuccessful);

			if (userPasswordDemoLoginData.isDemo == true) {
				adminListeners.forEach(l -> l.onSystemTextMessage(ConnectorUtils.testnet_Note,
						SystemTextMessageType.UNCLASSIFIED));
				connector.setWssUrl(ConnectorUtils.testnet_Wss);
				connector.setRestApi(ConnectorUtils.testnet_restApi);
				connector.setRestActiveInstrUrl(ConnectorUtils.testnet_restActiveInstrUrl);
			} else {
				connector.setWssUrl(ConnectorUtils.bitmex_Wss);
				connector.setRestApi(ConnectorUtils.bitmex_restApi);
				connector.setRestActiveInstrUrl(ConnectorUtils.bitmex_restActiveInstrUrl);
			}
			// CONNECTOR
			// this.connector = new BmConnector();

			connector.setProvider(this);
			connector.setTradeConnector(tradeConnector);
			connectorThread = new Thread(connector);
			connectorThread.setName("->com.bookmap.plugins.layer0.bitmex.adapter: connector");
			connectorThread.start();
		} else if (isOneCredentialEmpty) {
			Log.info("[bitmex] Provider handleLogin: empty credentials");
			// Report failed login
			adminListeners.forEach(l -> l.onLoginFailed(LoginFailedReason.WRONG_CREDENTIALS,
					"Either login or password is empty"));
		}

	}

	public void reportWrongCredentials(String reason) {
		adminListeners.forEach(l -> l.onLoginFailed(LoginFailedReason.WRONG_CREDENTIALS,
				reason));
		close();
	}

	public void listenForOrderBookL2(UnitData unit) {
		for (Layer1ApiDataListener listener : dataListeners) {
			listener.onDepth(unit.getSymbol(), unit.isBid(), unit.getIntPrice(), (int) unit.getSize());
		}
	}

	public void listenForTrade(UnitData unit) {
		for (Layer1ApiDataListener listener : dataListeners) {
			final boolean isOtc = false;
			listener.onTrade(unit.getSymbol(), unit.getIntPrice(), (int) unit.getSize(),
					new TradeInfo(isOtc, unit.isBid()));
		}
	}

	public void listenForExecution(UnitExecution exec) {
		OrderInfoBuilder builder = workingOrders.get(exec.getOrderID());

		if (builder == null) {
			Log.info("[bitmex] Provider listenForExecution: builder is null");
		}

		if (exec.getExecType().equals("New")) {
			Log.info("[bitmex] Provider listenForExecution: new");
			String tempOrderId = exec.getClOrdID();

			synchronized (workingOrders) {
				builder = workingOrders.get(tempOrderId);
			}

			if (builder == null) {
				createBookmapOrder((UnitOrder) exec);
				synchronized (workingOrders) {
					builder = workingOrders.get(exec.getOrderID());
				}
			}

			// there will be either new id if the order is accepted
			// or the order will be rejected so no need to keep it in the map
			synchronized (workingOrders) {
				workingOrders.remove(tempOrderId);
			}

			if (exec.getPegPriceType().equals("TrailingStopPeg")) {
				synchronized (trailingStops) {
					trailingStops.put(exec.getOrderID(), exec.getPegOffsetValue());
				}
			}

			builder.setOrderId(exec.getOrderID());
			builder.setStatus(OrderStatus.WORKING);

			if (exec.getTriggered().equals("NotTriggered")) {
				// 'NotTriggered' really means 'notTriggeredBracketChild'.
				builder.setStatus(OrderStatus.SUSPENDED);
			}

			checkIfLinkedAndAddToMaps(exec);

		} else if (exec.getExecType().equals("Replaced")
				|| exec.getExecType().equals("Restated")) {
			Log.info("[bitmex] Provider listenForExecution: " + exec.getExecType());
			builder.setUnfilled((int) exec.getLeavesQty());
			builder.setLimitPrice(exec.getPrice());
			builder.setStopPrice(exec.getStopPx());
			
			if(builder.getStatus().equals(OrderStatus.PENDING_MODIFY)){
				builder.setStatus(OrderStatus.WORKING);
			}

		} else if (exec.getExecType().equals("Trade")) {
			Log.info("[bitmex] Provider listenForExecution: trade");
			ExecutionInfo executionInfo = new ExecutionInfo(exec.getOrderID(), (int) exec.getLastQty(),
					exec.getLastPx(),
					exec.getExecID(), System.currentTimeMillis());
			tradingListeners.forEach(l -> l.onOrderExecuted(executionInfo));

			// updating filled orders volume
			String symbol = exec.getSymbol();
			BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
			// instr.setExecutionsVolume(instr.getExecutionsVolume() + (int)
			// exec.getCumQty());
			instr.setExecutionsVolume(instr.getExecutionsVolume() + (int) exec.getLastQty());

			// Changing the order itself
			builder.setAverageFillPrice(exec.getAvgPx());
			builder.setUnfilled((int) exec.getLeavesQty());
			builder.setFilled((int) exec.getCumQty());

			if (exec.getOrdStatus().equals("Filled")) {
				builder.setStatus(OrderStatus.FILLED);
			}
		} else if (exec.getExecType().equals("Canceled")) {
			Log.info("[bitmex] Provider listenForExecution: canceled");
			builder.setStatus(OrderStatus.CANCELLED);
		} else if (exec.getExecType().equals("TriggeredOrActivatedBySystem")) {
			if (exec.getTriggered().equals("StopOrderTriggered")) {
				Log.info("[bitmex] Provider listenForExecution: StopOrderTriggered");
				builder.setStopTriggered(true);
			} else if (exec.getTriggered().equals("Triggered")) {
				Log.info("[bitmex] Provider listenForExecution: TriggeredOrActivatedBySystem + Triggered");
				builder.setStatus(OrderStatus.WORKING);
			}
		} else if (exec.getExecType().equals("Rejected")) {
			Log.info("[bitmex] Provider listenForExecution: Rejected");
			if (builder == null) {
				synchronized (workingOrders) {
					builder = workingOrders.get(exec.getClOrdID());
				}
			}
			String reason = "The order was rejected: \n" +
					exec.getOrdRejReason();
			builder.setStatus(OrderStatus.REJECTED);
			// Provider can complain to user here explaining what was done wrong
			adminListeners.forEach(l -> l.onSystemTextMessage(reason,
					SystemTextMessageType.ORDER_FAILURE));
		} else if (exec.getExecType().equals("TriggeredOrActivatedBySystem")) {

			if (exec.getTriggered().equals("StopOrderTriggered")) {
				Log.info("[bitmex] Provider listenForExecution: TriggeredOrActivatedBySystem + StopOrderTriggered");
				builder.setStopTriggered(true);
			} else if (exec.getTriggered().equals("Triggered")) {
				Log.info("[bitmex] Provider listenForExecution:  TriggeredOrActivatedBySystem + Triggered");
				builder.setStatus(OrderStatus.WORKING);
			}
		}

		exec.setExecTransactTime(ConnectorUtils.transactTimeToLong(exec.getTransactTime()));
		builder.setModificationUtcTime(exec.getExecTransactTime());
		OrderInfoBuilder finalBuilder = builder;
		tradingListeners.forEach(l -> l.onOrderUpdated(finalBuilder.build()));
		builder.markAllUnchanged();

		synchronized (workingOrders) {
			// we no longer need filled or canceled orders in the working orders
			// map
			if (exec.getExecType().equals("Filled")
					|| exec.getExecType().equals("Canceled")
					|| exec.getExecType().equals("Rejected")) {
				workingOrders.remove(exec.getOrderID());
			} else {// but we need to keep the changes if something has changed
				workingOrders.put(finalBuilder.getOrderId(), builder);
			}
		}
	}

	public void listenForPosition(UnitPosition pos) {
		String symbol = pos.getSymbol();
		BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
		UnitPosition validPosition = instr.getValidPosition();

		updateValidPosition(validPosition, pos);

		StatusInfo info = new StatusInfo(validPosition.getSymbol(),
				(double) validPosition.getUnrealisedPnl() / (double) instr.getMultiplier(),
				(double) validPosition.getRealisedPnl() / (double) instr.getMultiplier(),
				"",
				(int) pos.getCurrentQty(),
				validPosition.getAvgEntryPrice(), instr.getExecutionsVolume(),
				validPosition.getOpenOrderBuyQty().intValue(), validPosition.getOpenOrderSellQty().intValue());

		tradingListeners.forEach(l -> l.onStatus(info));
	}

	public void listenForWallet(UnitWallet wallet) {
		BalanceInfo.BalanceInCurrency currentBic = balanceMap.get(wallet.getCurrency());
		String currency = wallet.getCurrency();
		if (currentBic == null) {// no current balance balance
			currentBic = new BalanceInfo.BalanceInCurrency(0.0, 0.0, 0.0, 0.0, 0.0,
					currency, null);
		}

		long tempMultiplier = 100000000;// temp
		// PNLs and NetLiquidityValue are taken from UnitMargin topic
		// Double netLiquidityValue = 0.0;// to be calculated
		
		Double rateToBase = null;

		currentBic = new BalanceInfo.BalanceInCurrency(
				wallet.getAmount() == null ? currentBic.balance : (double) wallet.getAmount() / tempMultiplier,
				currentBic.realizedPnl,
				currentBic.unrealizedPnl,
				wallet.getPrevAmount() == null ? currentBic.previousDayBalance
						: (double) wallet.getPrevAmount() / tempMultiplier,
				// netLiquidityValue == null ? currentBic.netLiquidityValue :
				// netLiquidityValue,
				currentBic.netLiquidityValue,
				currency,
				rateToBase == null ? currentBic.rateToBase : rateToBase);

		balanceMap.put(currency, currentBic);
		BalanceInfo info = new BalanceInfo(new ArrayList<BalanceInfo.BalanceInCurrency>(balanceMap.values()));
		tradingListeners.forEach(l -> l.onBalance(info));
	}

	public void listenForMargin(UnitMargin margin) {
		long tempMultiplier = 100000000;// temp
		String currency = margin.getCurrency();
		BalanceInfo.BalanceInCurrency currentBic = balanceMap.get(margin.getCurrency());
		if (currentBic == null) {// no current balance balance
			currentBic = new BalanceInfo.BalanceInCurrency(0.0, 0.0, 0.0, 0.0, 0.0, currency, null);
		}
		currentBic = new BalanceInfo.BalanceInCurrency(
				currentBic.balance,
				margin.getRealisedPnl() == null ? currentBic.realizedPnl
						: (double) margin.getRealisedPnl() / tempMultiplier,
				margin.getUnrealisedPnl() == null ? currentBic.unrealizedPnl
						: (double) margin.getUnrealisedPnl() / tempMultiplier,
				currentBic.previousDayBalance,
				margin.getAvailableMargin() == null ? currentBic.netLiquidityValue
						: (double) margin.getAvailableMargin() / tempMultiplier,
				currency,
				currentBic.rateToBase);

		balanceMap.put(currency, currentBic);
		BalanceInfo info = new BalanceInfo(new ArrayList<BalanceInfo.BalanceInCurrency>(balanceMap.values()));
		tradingListeners.forEach(l -> l.onBalance(info));
	}

	public void pushRateLimitWarning(String ratio) {
		String reason = "Only " + ratio
				+ "% of your rate limit is left. Please slow down for a while to stay within your rate limit";
		adminListeners.forEach(l -> l.onSystemTextMessage(reason,
				SystemTextMessageType.ORDER_FAILURE));
	}

	public void reportLostConnection() {
		adminListeners.forEach(l -> l.onConnectionLost(DisconnectionReason.NO_INTERNET, "Connection lost"));
	}

	public void reportRestoredCoonection() {
		adminListeners.forEach(l -> l.onConnectionRestored());
	}

	public void updateExecutionsHistory(UnitExecution[] execs) {

		for (int i = execs.length - 1; i >= 0; i--) {
			UnitExecution exec = execs[i];
			exec.setExecTransactTime(ConnectorUtils.transactTimeToLong(exec.getTransactTime()));

			final OrderInfoBuilder builder = new OrderInfoBuilder(
					exec.getSymbol(), exec.getOrderID(),
					exec.getSide().equals("Buy"),
					OrderType.getTypeFromPrices(exec.getStopPx(), exec.getPrice()),
					exec.getClientId(),
					false);

			OrderStatus status = exec.getOrdStatus().equals("Filled") ? OrderStatus.FILLED : OrderStatus.CANCELLED;
			long unfilled = exec.getLeavesQty() == 0 ? exec.getOrderQty() - exec.getCumQty() : exec.getLeavesQty();

			builder.setStopPrice(exec.getStopPx())
					.setLimitPrice(exec.getPrice())
					.setUnfilled((int) unfilled)
					.setFilled((int) exec.getCumQty())
					.setDuration(OrderDuration.GTC)
					.setStatus(status)
					.setAverageFillPrice(exec.getAvgPx())
					.setModificationUtcTime(exec.getExecTransactTime());

			tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
			if (status.equals(OrderStatus.FILLED)) {
				ExecutionInfo executionInfo = new ExecutionInfo(exec.getOrderID(), (int) exec.getCumQty(),
						exec.getAvgPx(),
						exec.getExecID(), exec.getExecTransactTime());
				tradingListeners.forEach(l -> l.onOrderExecuted(executionInfo));
			}
		}
	}

	private void updateValidPosition(UnitPosition validPosition, UnitPosition pos) {
		if (validPosition.getAccount().equals(0L)) {
			if (pos.getAccount() != null) {
				validPosition.setAccount(pos.getAccount());
			}
		}
		if (validPosition.getSymbol().equals("") && pos.getSymbol() != null) {
			validPosition.setSymbol(pos.getSymbol());
		}
		if (validPosition.getCurrency().equals("") && pos.getCurrency() != null) {
			validPosition.setCurrency(pos.getCurrency());
		}
		if (pos.getMarkValue() != null) {
			validPosition.setMarkValue(pos.getMarkValue());
		}
		if (pos.getRealisedPnl() != null) {
			validPosition.setRealisedPnl(pos.getRealisedPnl());
		}

		if (pos.getUnrealisedPnl() != null) {
			validPosition.setUnrealisedPnl(pos.getUnrealisedPnl());
		}
		if (pos.getAvgEntryPrice() != null) {
			validPosition.setAvgEntryPrice(pos.getAvgEntryPrice());
		}
		if (pos.getOpenOrderBuyQty() != null) {
			validPosition.setOpenOrderBuyQty(pos.getOpenOrderBuyQty());
			Log.info("[bitmex] Provider updateValidPosition:  add Buys=" + validPosition.getOpenOrderBuyQty());
		}
		if (pos.getOpenOrderSellQty() != null) {
			validPosition.setOpenOrderSellQty(pos.getOpenOrderSellQty());
			Log.info("[bitmex] Provider updateValidPosition:  add Sells=" + validPosition.getOpenOrderSellQty());
		}
	}

	/**
	 * must always be invokes before invoking updateCurrentPosition because it
	 * needs not updated valid position
	 */

	public void createBookmapOrder(UnitOrder order) {
		Log.info("[bitmex] Provider createBookmapOrder:  order created id=" + order.getOrderID());
		boolean isBuy = order.getSide().equals("Buy") ? true : false;
		OrderType type = OrderType.getTypeFromPrices(order.getStopPx(), order.getPrice());
		Log.info("[bitmex] Provider createBookmapOrder:  order created Type=" + type.toString());
		String clientId = tempClientId;
		boolean doNotIncrease = false;// this field is being left true so far

		checkIfLinkedAndAddToMaps(order);

		final OrderInfoBuilder builder = new OrderInfoBuilder(order.getSymbol(), order.getOrderID(), isBuy, type,
				clientId, doNotIncrease);
		builder.setStopPrice(order.getStopPx()).setLimitPrice(order.getPrice()).setUnfilled((int) order.getLeavesQty())
				.setFilled((int) order.getCumQty()).setDuration(OrderDuration.GTC)
				.setStatus(OrderStatus.WORKING);
		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		builder.markAllUnchanged();

		synchronized (workingOrders) {
			workingOrders.put(order.getOrderID(), builder);
			Log.info("BM ORDER PUT");
		}
	}

	private void checkIfLinkedAndAddToMaps(UnitOrder order) {
		// if order is linked
		if (!order.getClOrdLinkID().equals("")) {
			// add to LinkIdToRealIdsMap
			List<String> tempList;

			synchronized (LinkIdToRealIdsMap) {
				if (!LinkIdToRealIdsMap.containsKey(order.getClOrdLinkID())) {
					LinkIdToRealIdsMap.put(order.getClOrdLinkID(), new LinkedList<String>());
				}
				tempList = LinkIdToRealIdsMap.get(order.getClOrdLinkID());
			}

			if (!order.getContingencyType().equals("OneTriggersTheOther")) {
				tempList.add(0, order.getOrderID());
			} else {
				// add to Bracket parents
				synchronized (bracketParents) {
					bracketParents.add(order.getOrderID());
				}
				tempList.add(order.getOrderID());
			}
			// add to RealToLinkIdMap
			synchronized (RealToLinkIdMap) {
				RealToLinkIdMap.put(order.getOrderID(), order.getClOrdLinkID());
			}
		}
	}

	@Override
	public Layer1ApiProviderSupportedFeatures getSupportedFeatures() {
		// Expanding parent supported features, reporting basic trading support
		Layer1ApiProviderSupportedFeaturesBuilder a = super.getSupportedFeatures().toBuilder();

		if (!isCredentialsEmpty) {
			a.setTrading(true);
		}

		/*
		 * OCO and brackets are set to false because BitMEX announced contingent
		 * orders deprecation
		 * https://blog.bitmex.com/api_announcement/deprecation-of-contingent-
		 * orders/
		 */

		a.setOco(false)
				.setBrackets(false)
				.setSupportedOrderDurations(Arrays.asList(ConnectorUtils.bitmexOrderDurations.stream()
						.toArray(size -> new OrderDuration[size])))
				// At the moment of writing this method it was not possible to
				// report limit orders support, but no stop orders support
				// If you actually need it, you can report stop orders support
				// but reject stop orders when those are sent.
				.setSupportedStopOrders(Arrays.asList(new OrderType[] { OrderType.LMT, OrderType.MKT }))
				.setBalanceSupported(true)
				.setTrailingStopsAsIndependentOrders(true)
				.setExchangeUsedForSubscription(false)
				.setTypeUsedForSubscription(false)
				.setHistoricalDataInfo(new BmSimpleHistoricalDataInfo(
						"http://bitmex.historicaldata.bookmap.com:38080/historical-data-server-1.0/"))
				.setKnownInstruments(knownInstruments);

		return a.build();
	}

	@Override
	public String getSource() {
		// String identifying where data came from.
		// For example you can use that later in your indicator.
		return "realtime demo";
	}

	@Override
	public void close() {
		// Stop events generation
		Log.info("[bitmex] Provider close(): ");
		connector.closeSocket();
		connector.setInterruptionNeeded(true);
		providerThread.interrupt();
	}

}
