package com.bookmap.plugins.layer0.bitmex;

import com.bookmap.plugins.layer0.bitmex.adapter.*;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.bookmap.plugins.layer0.bitmex.messages.ProviderTargetedLeverageMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import velox.api.layer0.annotations.Layer0CredentialsFieldsManager;
import velox.api.layer0.annotations.Layer0LiveModule;
import velox.api.layer0.credentialscomponents.CredentialsSerializationField;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.Layer1ApiAdminListener;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.*;
import velox.api.layer1.layers.utils.OrderBook;
import velox.api.layer1.messages.Layer1ApiIsRealTradingMessage;
import velox.api.layer1.messages.UserProviderTargetedMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Layer1ApiVersion(Layer1ApiVersionValue.VERSION1)
@Layer0LiveModule(shortName = "MEX", fullName = "BitMEX")
@Layer0CredentialsFieldsManager(BitmexFieldManager.class)
public class Provider extends ExternalLiveBaseProvider {

	private BmConnector connector;
	private TradeConnector tradeConnector;
	private HttpClientHolder httpClientHolder;
	private Object orderIdsMapsLock = new Object();
	private HashMap<String, OrderInfoBuilder> workingOrders = new HashMap<>();
	private BidiMap<String, String> clientIdsToOrderIds = new DualHashBidiMap<>(); 

	private List<OrderInfoBuilder> pendingOrdersBuilders = new ArrayList<>();
	private long orderOcoCount;
	private BitmexUserPasswordDemoLoginData bitmexLoginData;
	// <id, trailingStep>
	private Map<String, Double> trailingStops = new HashMap<>();
	private List<String> batchCancels = new LinkedList<>();
	private Map<String, BalanceInfo.BalanceInCurrency> balanceMap = new HashMap<>();
	private Map<String, Integer> leverages = new ConcurrentHashMap<>();
	public Map<String, Integer> maxLeverages = new HashMap<>();

	private CopyOnWriteArrayList<SubscribeInfo> knownInstruments = new CopyOnWriteArrayList<>();
	public final PanelServerHelper panelHelper = new PanelServerHelper();
	private Gson gson = new Gson();
	private boolean isDemo;
	private volatile boolean isLoginSuccessful = true;
	private String authFailedReason;

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
	private Thread providerThread;
	private Thread connectorThread;
	
	public boolean isTradingEnabled() {
		return bitmexLoginData.isTradingEnabled;
	}

    public String getAlias(String clientId) {
        String alias;

        synchronized (orderIdsMapsLock) {
            alias = workingOrders.get(clientId).getInstrumentAlias();
        }
        return alias;
	}

	public BmConnector getConnector() {
		return connector;
	}

	public List<SubscribeInfo> getKnownInstruments() {
		return knownInstruments;
	}

	ExecutorService orderExecutor = Executors.newSingleThreadExecutor();

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

	@Override
	public void subscribe(SubscribeInfo subscribeInfo) {
		final String symbol = subscribeInfo.symbol;
		final String exchange = subscribeInfo.exchange;
		final String type = subscribeInfo.type;

		Log.info("Provider subscribe");
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
							Log.error("", e);
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
					    Log.error("", e);
					}
					BmInstrument instr = activeBmInstruments.get(symbol);
					double pips = ((SubscribeInfoCrypto)subscribeInfo).pips;
					instr.setActiveTickSize(pips);

					Instrument newInstrument = new Instrument(alias, pips);
					instruments.put(alias, newInstrument);
					InstrumentInfo instrumentInfo = new InstrumentInfo(symbol, exchange, type, newInstrument.pips,
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
        Instrument instrument = null;
        synchronized (instruments) {
            instrument = instruments.get(alias);
        }
        /**
         * This is a workaround rather than a permanent solution.
         * 
         * Sometimes when switching between two workspaces (for example HitBTC, HitBTC demo),
         * Bookmap calls formatPrice method without previous calls to login and subscribe methods.
         * At this moment, info for pair isn't fetched yet and crash occurs NullPointerException.
         * So we just return constant value to avoid crashes.
         */
        double pips = instrument == null ? 100 : instrument.pips;
        return formatPriceDefault(pips, price);
	}

	@Override
	public void sendOrder(OrderSendParameters orderSendParameters) {
		orderExecutor.execute(() -> {
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

		Pair<Boolean, String> response = tradeConnector.require(genType, Method.POST, data);
		passCancelMessageIfNeededAndClearPendingList(response);
		Log.info("Provider sendOrder: response = " + response);
		});
	}

	private void passCancelMessageIfNeededAndClearPendingList(Pair<Boolean, String> response) {
		synchronized (pendingOrdersBuilders) {
			if (!response.getLeft()) {// if bitmex responds with an error
				for (OrderInfoBuilder builder : pendingOrdersBuilders) {
					rejectOrder(builder, response.getRight());
				}
			}
			// should be cleared anyway
			pendingOrdersBuilders.clear();
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
		double tickSize = bmInstrument.getActiveTickSize();
		int offsetMultiplier = simpleParams.isBuy ? 1 : -1;

		double limitPriceChecked = checkLImitPriceForBracket(simpleParams, bmInstrument);

		@SuppressWarnings("deprecation")
        SimpleOrderSendParameters stopLoss = new SimpleOrderSendParameters(
				simpleParams.alias,
				!simpleParams.isBuy, // !
				simpleParams.size,
				simpleParams.duration,
				Double.NaN, // limitPrice
				limitPriceChecked - offsetMultiplier * simpleParams.stopLossOffset * tickSize, // stopPrice
				simpleParams.sizeMultiplier);
		return stopLoss;
	}

	private SimpleOrderSendParameters createTakeProfitFromParameters(SimpleOrderSendParameters simpleParams) {
		String symbol = ConnectorUtils.isolateSymbol(simpleParams.alias);
		BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);
		double tickSize = bmInstrument.getActiveTickSize();
		int offsetMultiplier = simpleParams.isBuy ? 1 : -1;
		double limitPriceChecked = checkLImitPriceForBracket(simpleParams, bmInstrument);

		@SuppressWarnings("deprecation")
        SimpleOrderSendParameters takeProfit = new SimpleOrderSendParameters(
				simpleParams.alias,
				!simpleParams.isBuy, // !
				simpleParams.size,
				simpleParams.duration,
				limitPriceChecked + offsetMultiplier * simpleParams.takeProfitOffset * tickSize, // limitPrice
				Double.NaN, // stopPrice
				simpleParams.sizeMultiplier);
		return takeProfit;
	}

	private double checkLImitPriceForBracket(SimpleOrderSendParameters simpleParams, BmInstrument bmInstrument) {
		double limitPriceChecked = simpleParams.limitPrice;
		if (Double.isNaN(simpleParams.limitPrice)) {
			OrderBook orderBook = bmInstrument.getOrderBook().getOrderBook();
			limitPriceChecked = simpleParams.isBuy ? orderBook.getBestAskPriceOrNone() * bmInstrument.getActiveTickSize()
					: orderBook.getBestBidPriceOrNone() * bmInstrument.getActiveTickSize();
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
		Log.info("Provider prepareSimpleOrder: orderType = " + orderType.toString());
		final OrderInfoBuilder builder = new OrderInfoBuilder(simpleParameters.alias, simpleParameters.clientId,
				simpleParameters.isBuy, orderType, simpleParameters.clientId, simpleParameters.doNotIncrease);

		// You need to set these fields, otherwise Bookmap might not handle
		// order correctly
		builder.setStopPrice(simpleParameters.stopPrice)
				.setLimitPrice(simpleParameters.limitPrice)
				.setUnfilled(simpleParameters.size)
				.setDuration(simpleParameters.duration)
				.setStatus(OrderStatus.PENDING_SUBMIT);
		builder.build();
		// Marking all fields as unchanged, since they were just reported and
		// fields will be marked as changed automatically when modified.
		builder.markAllUnchanged();

		/*
		 * pending orders are added to the list to cancel them later if BitMEX
		 * reports an error trying placing orders
		 */
		synchronized (pendingOrdersBuilders) {
			pendingOrdersBuilders.add(builder);
		}

		Log.info("Provider prepareSimpleOrder: getting sent to bitmex, clientId " + simpleParameters.clientId);
		synchronized (orderIdsMapsLock) {
			workingOrders.put(simpleParameters.clientId, builder);
		}

		JsonObject json = tradeConnector.createSendData(simpleParameters, orderType, simpleParameters.clientId, clOrdLinkID,
				contingencyType);
		return json;
	}

	public void rejectOrder(OrderInfoBuilder builder, String reason) {
		String purifiedReason = "The order has been rejected: \n" + getPurifiedErrorMessage(reason);
		Log.info("Provider rejectOrder");
		/*
		 * Necessary fields are already populated, so just change status to
		 * rejected and send
		 */
        
		/*
         * A pending submit order has no orderId as it has not been accepted by the exchange.
         * Report a builder with null orderId is a bad idea so we need to assign a 
         * temporary unique orderId. To avoid generating a unique id we can use a clientId here.
         */		
		if (StringUtils.isEmpty(builder.getOrderId())) {
		    builder.setOrderId(builder.getClientId());
		}
        builder.setStatus(OrderStatus.PENDING_SUBMIT);
        tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));

        builder.setStatus(OrderStatus.REJECTED);
		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		builder.markAllUnchanged();
		
		// Provider can complain to user here explaining what was done wrong
		adminListeners.forEach(l -> l.onSystemTextMessage(purifiedReason,
				SystemTextMessageType.ORDER_FAILURE));
		
		// The best option would be to remove it from workingOrders map.
		// But if bitmex decides to send a 'rejected' execution (which might happen someday)
		// the order will be missing and it will cause a crash.
		// So unfilled volume is set to 0 to keep the workingOrders volume updated.
		builder.setUnfilled(0);
	}

	@Override
    public void updateOrder(OrderUpdateParameters orderUpdateParameters) {
		orderExecutor.execute(() -> {
        try {
            if (orderUpdateParameters.getClass() == OrderCancelParameters.class) {
                OrderCancelParameters orderCancelParameters = (OrderCancelParameters) orderUpdateParameters;
                Log.info("Provider updateOrder: (cancel) id=" + orderCancelParameters.orderId);
                passCancelParameters(orderCancelParameters);
            } else if (orderUpdateParameters.getClass() == OrderResizeParameters.class) {
                Log.info("Provider updateOrder: (resize)");
                OrderResizeParameters orderResizeParameters = (OrderResizeParameters) orderUpdateParameters;
                passResizeParameters(orderResizeParameters);
            } else if (orderUpdateParameters.getClass() == OrderMoveParameters.class) {
                Log.info("Provider updateOrder: (move)");
                OrderMoveParameters orderMoveParameters = (OrderMoveParameters) orderUpdateParameters;

                boolean isTrailingStop;
                synchronized (trailingStops) {
                    isTrailingStop = trailingStops.containsKey(orderMoveParameters.orderId);
                }

                if (isTrailingStop) {
                    // trailing stop
                    JsonObject json = tradeConnector.moveTrailingStepJson(orderMoveParameters);
                    tradeConnector.require(GeneralType.ORDER, Method.PUT, json.toString());
                } else {// single order
                    boolean isStopTriggered;
                    synchronized (orderIdsMapsLock) {
                        String clientId = getClientId(orderMoveParameters.orderId);
                        OrderInfoBuilder builder = workingOrders.get(clientId);
                        if (builder == null) {
                            Log.info("Provider checking isStopTriggered | bulder NULL for "
                                    + orderMoveParameters.orderId + ", not being sent to bitmex");
                        } else {
                            isStopTriggered = builder.isStopTriggered();
                            JsonObject json = tradeConnector.moveOrderJson(orderMoveParameters, isStopTriggered);
                            tradeConnector.require(GeneralType.ORDER, Method.PUT, json.toString());
                        }
                    }
                }
            } else {
                throw new UnsupportedOperationException("Unsupported order type");
            }
        } catch (NullPointerException e) {
            Log.error("", e);
            Log.info("Provider updateOrder: no found " + orderUpdateParameters.toString());
            if (workingOrders == null) {
                Log.info("Provider updateOrder: workingOrders == null");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("Provider updateOrder: workingOrders {");
                for (String id : workingOrders.keySet()) {
                    sb.append("[" + id + ", " + workingOrders.get(id) + "], ");
                }
                sb.append("]");
                Log.info(sb.toString());
            }
            throw new RuntimeException();
        }
		});
    }

	private void passCancelParameters(OrderCancelParameters orderCancelParameters) {
		if (orderCancelParameters.batchEnd == true) {
			/*
			 * This is the end of the batch or a single cancel. But if this
			 * order is an OCO or Bracket component we need to cancel the whole
			 * OCO or Bracket
			 */
			if (batchCancels.size() == 0) {
                tradeConnector.cancelOrder(orderCancelParameters.orderId);
                Log.info("Provider passCancelParameters: (single cancel)");
			} else {
				/*
				 * This is the batch end. We add cancel to the list then perform
				 * canceling then clear the list
				 */
				batchCancels.add(orderCancelParameters.orderId);
				tradeConnector.cancelOrder(batchCancels);
				batchCancels.clear();
				Log.info("Provider passCancelParameters: (batch cancel performed)");

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
		synchronized (orderIdsMapsLock) {
		    String clientId = getClientId(orderResizeParameters.orderId);
			builder = workingOrders.get(clientId);
		}
		List<String> pendingClientIds = new ArrayList<>();
		String data;
		GeneralType type;

        // single order
        pendingClientIds.add(builder.getClientId());
        type = GeneralType.ORDER;
        data = tradeConnector.resizeOrder(builder.getOrderId(), newSize);

		setPendingStatus(pendingClientIds, OrderStatus.PENDING_MODIFY);
		Pair<Boolean, String> response = tradeConnector.require(type, Method.PUT, data);
		passCancelMessageIfNeededAndClearPendingListForResize(pendingClientIds, response);
	}

	private void setPendingStatus(List<String> pendingClientIds, OrderStatus status) {
		for (String clientId : pendingClientIds) {
			OrderInfoBuilder builder;
			synchronized (orderIdsMapsLock) {
				builder = workingOrders.get(clientId);
			}
			builder.setStatus(status);
			tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
			builder.markAllUnchanged();
		}
	}

	// temporary solution
	private void passCancelMessageIfNeededAndClearPendingListForResize(List<String> pendingClientIds, Pair<Boolean, String> response) {
		if (!response.getLeft()) {// if bitmex responds with an error
		    String purifiedResponse = getPurifiedErrorMessage(response.getRight());
			adminListeners.forEach(l -> l.onSystemTextMessage(purifiedResponse,
					SystemTextMessageType.ORDER_FAILURE));

			for (String clientId : pendingClientIds) {
				OrderInfoBuilder builder;
				synchronized (orderIdsMapsLock) {
					builder = workingOrders.get(clientId);
				}
				builder.setStatus(OrderStatus.WORKING);
				tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
				builder.markAllUnchanged();
			}
		}
		// should be cleared anyway
		pendingClientIds.clear();
	}

	@Override
	public void login(LoginData loginData) {
        if (loginData instanceof ExtendedLoginData) {
            Map<String, CredentialsSerializationField> extendedData = ((ExtendedLoginData)loginData).extendedData;
            String key = extendedData.get(Constants.API_KEY_FIELD_NAME).getStringValue();
            String secret = extendedData.get(Constants.API_SECRET_FIELD_NAME).getStringValue();
            boolean isDemo = Boolean.valueOf(extendedData.get(Constants.IS_DEMO_CHECKBOX_NAME).getStringValue());
            boolean isTradingEnabled = Boolean.valueOf(extendedData.get(Constants.ENABLE_TRADING_CHECKBOX_NAME).getStringValue());
            bitmexLoginData = new BitmexUserPasswordDemoLoginData(key, secret, isDemo, isTradingEnabled);
        } else {
            boolean isTradingEnabled = StringUtils.isNoneBlank(
                    ((UserPasswordDemoLoginData) loginData).user,
                    ((UserPasswordDemoLoginData) loginData).password
                    );
            bitmexLoginData = new BitmexUserPasswordDemoLoginData((UserPasswordDemoLoginData) loginData, isTradingEnabled);
        }
		// If connection process takes a while then it's better to do it in
		// separate thread
		providerThread = new Thread(() -> handleLogin(bitmexLoginData));
		providerThread.setName("-> INSTRUMENT");
		providerThread.start();
	}

	private void handleLogin(BitmexUserPasswordDemoLoginData bitmexLoginData) {
        if (bitmexLoginData.isTradingEnabled
                && StringUtils.isAnyBlank(bitmexLoginData.user, bitmexLoginData.password)) {
            reportWrongCredentials("Either login or password is empty");
            return;
        }

        isLoginSuccessful = true;
        authFailedReason = "";
        Log.info("Provider handleLogin");
        // With real connection provider would attempt establishing connection
        // here.
        Log.info("Provider handleLogin: credentials valid or empty");
        httpClientHolder = new HttpClientHolder(bitmexLoginData.user, bitmexLoginData.password, this);
        connector = new BmConnector(httpClientHolder);
        tradeConnector = new TradeConnector(httpClientHolder);
        tradeConnector.setProvider(this);
        tradeConnector.setOrderApiKey(bitmexLoginData.user);
        tradeConnector.setOrderApiSecret(bitmexLoginData.password);
        panelHelper.setConnector(tradeConnector);
        panelHelper.setProvider(this);

        this.isDemo = bitmexLoginData.isDemo;
        if (isTradingEnabled()) {
            adminListeners.forEach(l -> l.onUserMessage(new Layer1ApiIsRealTradingMessage(!isDemo)));
        }

        if (isDemo) {
            connector.setWssUrl(Constants.testnet_Wss);
            connector.setRestApi(Constants.testnet_restApi);
        } else {
            connector.setWssUrl(Constants.bitmex_Wss);
            connector.setRestApi(Constants.bitmex_restApi);
        }
        connector.setProvider(this);
        connector.setTradeConnector(tradeConnector);
        connectorThread = new Thread(connector);
        connectorThread.setName("->com.bookmap.plugins.layer0.bitmex.adapter: connector");
        
        if (!connector.isConnectionEstablished()) {
            adminListeners.forEach(l -> l.onLoginFailed(LoginFailedReason.NO_INTERNET_CONNECTION, "no Internet connection"));
            close();
            return;
        }
        connectorThread.start();

        if (bitmexLoginData.isTradingEnabled) {
            try {
                connector.getWebSocketAuthLatch().await();
            } catch (InterruptedException e) {
                Log.info("", e);
            }
        }

        if (isLoginSuccessful) {
            adminListeners.forEach(Layer1ApiAdminListener::onLoginSuccessful);
            if (isDemo) {
                adminListeners.forEach(
                        l -> l.onSystemTextMessage(ConnectorUtils.testnet_Note, SystemTextMessageType.UNCLASSIFIED));
            }
        } else {
            reportWrongCredentials(authFailedReason);
        }
	}

	public void reportWrongCredentials(String reason) {
		adminListeners.forEach(l -> l.onLoginFailed(LoginFailedReason.WRONG_CREDENTIALS,
				reason));
		close();
	}

	public void listenForOrderBookL2(UnitData unit) {
		for (Layer1ApiDataListener listener : dataListeners) {
			listener.onDepth(unit.getSymbol(), unit.isBid(), unit.getIntPrice(), toIntOrMax(unit.getSize(), Constants.maxSize));
		}
	}

	public void listenForTrade(UnitData unit) {
		for (Layer1ApiDataListener listener : dataListeners) {
			final boolean isOtc = false;
			listener.onTrade(unit.getSymbol(), unit.getIntPrice(), toIntOrMax(unit.getSize(), Constants.maxSize),
					new TradeInfo(isOtc, unit.isBid()));
		}
	}

	public void listenForExecution(UnitExecution exec) {
	    // An order placed from terminal has no ClOrdId.
	    // Orders like these need to be distinguished.
	    // So their ClOrdId is set to their orderId.
        if (StringUtils.isBlank(exec.getClOrdID())) {
            exec.setClOrdID(exec.getOrderID());
        }
        
	    Log.info("listenForExecution " + exec.toString());
	    OrderInfoBuilder builder;
	    
        synchronized (orderIdsMapsLock) {
            clientIdsToOrderIds.put(exec.getClOrdID(), exec.getOrderID());
            builder = workingOrders.get(exec.getClOrdID());
        }
        
		

		if (exec.getText().contains("Canceled: Order had execInst of ParticipateDoNotInitiate") && exec.getExecType().equals("Canceled")) {
		    // a not-so-good workaround for a misplaced GTC_PO order
		    exec.setExecType("Rejected");
        }

		if (exec.getExecType().equals("New")) {
			synchronized (orderIdsMapsLock) {
				builder = workingOrders.get(exec.getClOrdID());
			}

			if (builder == null) {
				createBookmapOrder((UnitOrder) exec);
				synchronized (orderIdsMapsLock) {
					builder = workingOrders.get(exec.getClOrdID());
				}
			}
			
			if ("TrailingStopPeg".equals(exec.getPegPriceType())) {
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

			//adding untriggered orders to TCP
            if (builder.getType() == OrderType.STP || builder.getType() == OrderType.STP_LMT) {
                String symbol = exec.getSymbol();
                UnitPosition blankPosition = new UnitPosition();
                blankPosition.setSymbol(symbol);
                listenForPosition(blankPosition);
            }
		} else if (exec.getExecType().equals("Replaced")
				|| exec.getExecType().equals("Restated")) {
		    Log.info("Provider listenForExecution: " + exec.getExecType());
			builder.setUnfilled(toIntOrMax(exec.getLeavesQty(), Constants.maxSize));
			builder.setLimitPrice(exec.getPrice());
			builder.setStopPrice(exec.getStopPx());
			
			if(builder.getStatus().equals(OrderStatus.PENDING_MODIFY)){
				builder.setStatus(OrderStatus.WORKING);
			}

		} else if (exec.getExecType().equals("Trade")) {
		    Log.info("Provider listenForExecution: trade " + exec.getOrderID());
			ExecutionInfo executionInfo = new ExecutionInfo(exec.getOrderID(), toIntOrMax(exec.getLastQty(), Constants.maxSize),
					exec.getLastPx(),
					exec.getExecID(), System.currentTimeMillis());
			tradingListeners.forEach(l -> l.onOrderExecuted(executionInfo));

			// updating filled orders volume
			String symbol = exec.getSymbol();
			BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
			// instr.setExecutionsVolume(instr.getExecutionsVolume() + toIntOrMax(exec.getCumQty(), Constants.maxSize)
			instr.setExecutionsVolume(instr.getExecutionsVolume() + toIntOrMax(exec.getLastQty(), Constants.maxSize));

			// Changing the order itself
			builder.setAverageFillPrice(exec.getAvgPx());
			builder.setUnfilled(toIntOrMax(exec.getLeavesQty(), Constants.maxSize));
			builder.setFilled(toIntOrMax(exec.getCumQty(), Constants.maxSize));

			if (exec.getOrdStatus().equals("Filled")) {
			    Log.info("Provider listenForExecution: orderId filled " + exec.getOrderID()); 
				builder.setStatus(OrderStatus.FILLED);
			}
		} else if (exec.getExecType().equals("Canceled")) {
		    Log.info("Provider listenForExecution: canceled");
			builder.setStatus(OrderStatus.CANCELLED);
		} else if (exec.getExecType().equals("TriggeredOrActivatedBySystem")) {
			if (exec.getTriggered().equals("StopOrderTriggered")) {
			    Log.info("Provider listenForExecution: StopOrderTriggered");
				builder.setStopTriggered(true);
			} else if (exec.getTriggered().equals("Triggered")) {
			    Log.info("Provider listenForExecution: TriggeredOrActivatedBySystem + Triggered");
				builder.setStatus(OrderStatus.WORKING);
			}
		} else if (exec.getExecType().equals("Rejected")) {
		    Log.info("Provider listenForExecution: Rejected");
			if (builder == null) {
				synchronized (workingOrders) {
					builder = workingOrders.get(exec.getClOrdID());
				}
			}
			StringBuilder sb = new StringBuilder();
			sb.append("The order was rejected:");
			
			if (exec.getOrdRejReason() != null && !exec.getOrdRejReason().equals("")) {
			    sb.append("\n").append(exec.getOrdRejReason());
			}
			if (exec.getOrdRejReason() != null && !exec.getText().equals("")) {
			    sb.append("\n").append(exec.getText());
			}
			String reason = sb.toString();
			builder.setStatus(OrderStatus.REJECTED);
			// Provider can complain to user here explaining what was done wrong
			adminListeners.forEach(l -> l.onSystemTextMessage(reason,
					SystemTextMessageType.ORDER_FAILURE));
		}

		if (builder == null) {
		    //executed order number does not fit any builder. Possibly restated execution
		    Log.info("Skipped execution not matching any order: " + exec.toString());
		    return;
		}
		exec.setExecTransactTime(ConnectorUtils.transactTimeToLong(exec.getTransactTime()));
		builder.setModificationUtcTime(exec.getExecTransactTime());
		OrderInfoBuilder finalBuilder = builder;
		tradingListeners.forEach(l -> l.onOrderUpdated(finalBuilder.build()));
		builder.markAllUnchanged();

		synchronized (orderIdsMapsLock) {
			// we no longer need filled or canceled orders in the working orders
			// map
			if (exec.getExecType().equals("Filled")
					|| exec.getExecType().equals("Canceled")
					|| exec.getExecType().equals("Rejected")) {
				workingOrders.remove(exec.getClOrdID());
			}
		}
        updatePosition(exec.getSymbol());
	}

	public void listenForPosition(UnitPosition pos) {
		String symbol = pos.getSymbol();
		BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
		UnitPosition validPosition = instr.getValidPosition();

		updateValidPosition(validPosition, pos);

		if (pos.getLeverage() != null) {
		    updateLeverage(pos.getSymbol(), pos.getCommonLeverage());
		}
		reportPosition(instr, validPosition);
	}
	
    private void reportPosition(BmInstrument instr, UnitPosition validPosition) {

        long openBuys = workingOrders.values().stream()
                .filter(infoBuilder -> infoBuilder.isBuy())
                .flatMapToInt(infoBuilder -> IntStream.of(infoBuilder.getUnfilled()))
                .sum();
        long openSells = workingOrders.values().stream()
                .filter(infoBuilder -> !infoBuilder.isBuy())
                .flatMapToInt(infoBuilder -> IntStream.of(infoBuilder.getUnfilled()))
                .sum();
        
        StatusInfo info = new StatusInfo(validPosition.getSymbol(),
                (double) validPosition.getUnrealisedPnl() / (double) Math.abs(instr.getMultiplier()),
                (double) validPosition.getRealisedPnl() / (double) Math.abs(instr.getMultiplier()),
                "",
                toIntOrMax(validPosition.getCurrentQty(), Constants.maxSize),
                validPosition.getAvgEntryPrice(),
                instr.getExecutionsVolume(),
                toIntOrMax(openBuys, Constants.maxSize),
                toIntOrMax(openSells, Constants.maxSize));

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
					exec.getClOrdID(),
					false);

			OrderStatus status = exec.getOrdStatus().equals("Filled") ? OrderStatus.FILLED : OrderStatus.CANCELLED;
			long unfilled = exec.getLeavesQty() == 0 ? exec.getOrderQty() - exec.getCumQty() : exec.getLeavesQty();

			builder.setStopPrice(exec.getStopPx())
					.setLimitPrice(exec.getPrice())
					.setUnfilled(toIntOrMax(unfilled, Constants.maxSize))
					.setFilled(toIntOrMax(exec.getCumQty(), Constants.maxSize))
					.setDuration(OrderDuration.GTC)
					.setStatus(status)
					.setAverageFillPrice(exec.getAvgPx())
					.setModificationUtcTime(exec.getExecTransactTime());
			tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
			if (status.equals(OrderStatus.FILLED)) {
				ExecutionInfo executionInfo = new ExecutionInfo(exec.getOrderID(), toIntOrMax(exec.getCumQty(), Constants.maxSize),
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
		}
		if (pos.getOpenOrderSellQty() != null) {
			validPosition.setOpenOrderSellQty(pos.getOpenOrderSellQty());
		}
		validPosition.setCurrentQty(pos.getCurrentQty());
	}

	/**
	 * must always be invokes before invoking updateCurrentPosition because it
	 * needs not updated valid position
	 */

	public void createBookmapOrder(UnitOrder order) {
	    // TODO: erase later
        // This peace is duplicated from listenForExecution.
        // createBookmap order is called from JsonParser
        // dispatchRawUnits(ArrayList<T>, Class<?>)
        //
        // An order placed from terminal has no ClOrdId.
        // Orders like these need to be distinguished.
        // So their ClOrdId is set to their orderId.
        if (StringUtils.isBlank(order.getClOrdID())) {
            order.setClOrdID(order.getOrderID());
        }
		boolean isBuy = order.getSide().equals("Buy") ? true : false;
		String sType = order.getOrdType();
		
        synchronized (orderIdsMapsLock) {
            clientIdsToOrderIds.put(order.getClOrdID(), order.getOrderID());
        }

		if (sType.equals("symbol")) {
		    Log.info("Provider createBookmapOrder:  ordType is symbol; return");
			return;
		}
		if (sType.equals("MarketIfTouched")) {
			sType = "Stop";
			Log.info("Provider createBookmapOrder:  MarketIfTouched castes to Stop");
		}
		if (sType.equals("LimitIfTouched")) {
			sType = "StopLimit";
			Log.info("Provider createBookmapOrder:  LimitIfTouched castes to StopLimit");
		}

		String sTypeUpper = sType.toUpperCase();
		OrderType type = OrderType.valueOfLoose(sTypeUpper);
		Log.info("Provider createBookmapOrder:  order created Type=" + type.toString());
		boolean doNotIncrease = false;// this field is being left true so far

		final OrderInfoBuilder builder = new OrderInfoBuilder(order.getSymbol(), order.getOrderID(), isBuy, type,
		        order.getClOrdID(), doNotIncrease);
		
		builder.setStopPrice(order.getStopPx())
		.setLimitPrice(order.getPrice())
		.setUnfilled(toIntOrMax(order.getLeavesQty(), Constants.maxSize))		
		.setFilled(toIntOrMax(order.getCumQty(), Constants.maxSize))
		.setStatus(OrderStatus.WORKING);

        if (order.getTimeInForce() != null){
            builder.setDuration(ConnectorUtils.bitmexOrderDurationsValues
                    .inverseBidiMap()
                    .get(order.getTimeInForce()));
            
            if (order.getExecInst() != null && order.getExecInst().length() > 0) {
                String[] instr = order.getExecInst().split(",");
                Set<String> executionInstructions = new HashSet<>(Arrays.asList(instr));
                
                if (executionInstructions.contains(ConnectorUtils.GtcPoExecutionalInstruction)) {
                    builder.setDuration(OrderDuration.GTC_PO);
                }
            }
        }

		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		builder.markAllUnchanged();

		synchronized (orderIdsMapsLock) {
            workingOrders.put(order.getClOrdID(), builder);
        }
    }

	@Override
	public Layer1ApiProviderSupportedFeatures getSupportedFeatures() {
		// Expanding parent supported features, reporting basic trading support
		Layer1ApiProviderSupportedFeaturesBuilder a = super.getSupportedFeatures().toBuilder();
		a.setTrading(bitmexLoginData != null && bitmexLoginData.isTradingEnabled);

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
				        isDemo ? Constants.demoHistoricalServerUrl : Constants.realHistoricalServerUrl))
				.setKnownInstruments(knownInstruments)
				.setPipsFunction(subscribeInfo -> {
					if (connector.getActiveInstrumentsMap().get(subscribeInfo.symbol) == null) {
						return null;
					}
					double minSelectablePip = getMinSelectablePip(subscribeInfo);
					return new DefaultAndList<>(minSelectablePip, getSelectablePips(minSelectablePip));
				});

		return a.build();
	}

	private double getMinSelectablePip(SubscribeInfo subscribeInfo){
		return connector.getActiveInstrumentsMap().get(subscribeInfo.symbol).getTickSize();
	}

	private List<Double> getSelectablePips(double minSelectablePip) {
		int lastDigit = BigDecimal.valueOf(minSelectablePip).unscaledValue().intValue() % 10;
		List<Double> list = new ArrayList<>();
		double currentValue = minSelectablePip;

		for (int i = 0; minSelectablePip * 10_000 > currentValue; i++) {
			switch (lastDigit) {
				case 1:
					currentValue = minSelectablePip * Math.pow(10, i / 2) * (i % 2 == 0 ? 1 : 5);
					break;
				case 5:
					currentValue = minSelectablePip * Math.pow(10, i / 2) * (i % 2 == 0 ? 1 : 2);
					break;
				default:
					currentValue = minSelectablePip * Math.pow(10, i);
			}
			list.add(currentValue);
		}
		return list;
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
	    Log.info("Provider close(): ");
        if (connector != null) {
            connector.closeSocket();
            connector.setInterruptionNeeded(true);
        }
        if (httpClientHolder != null) {
            try {
                httpClientHolder.close();
            } catch (IOException e) {
                Log.error("", e);
            }
        }
		providerThread.interrupt();
	}
	
	public void updateLeverage (String symbol, double leverage) {
	    Log.info("updateLvrg " + symbol + " " + leverage);
	    Integer previousLeverage = leverages.get(symbol); 
	    if (previousLeverage == null || !previousLeverage.equals(leverage)) {
	        leverages.put(symbol, (int) Math.round(leverage));
	        Log.info("updateDLvrg " + symbol + " " + leverage);

	        //send message to panel here
	        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("symbol", symbol);
            map.put("leverage", leverage);
            map.put("maxLeverage", connector.getMaximumLeverage(symbol));
            gson.toJson(map);
            String message = gson.toJson(map);
            Log.info("bitmexSendMsg " + message);

            panelHelper.onUserMessage(message);
	    }
	}
	
	public Integer getLeverage (String symbol) {
	    return leverages.get(symbol);
	}

    @Override
    public Object sendUserMessage(Object data) {
        if (isTargetedAtBitmexAdapter(data)) {
            try {
                try (
                ClassLoaderObjectInputStream str = new ClassLoaderObjectInputStream(getClass().getClassLoader(), new ByteArrayInputStream(SerializationUtils.serialize((Serializable) data)))){
                    data = str.readObject();
                }
                ProviderTargetedLeverageMessage ptm = (ProviderTargetedLeverageMessage) data;
                String message = ptm.getMessage();
                panelHelper.acceptMessage(message);
            } catch (Exception e) {
                Log.info("", e);
            }
        }
        return super.sendUserMessage(data);
    }

    private boolean isTargetedAtBitmexAdapter(Object data) {
        if (data instanceof UserProviderTargetedMessage && data.getClass().getSimpleName().equals(ProviderTargetedLeverageMessage.class.getSimpleName())) {
            String programmaticName = ((UserProviderTargetedMessage) data).getProviderProgrammaticName();
            return programmaticName.equals(Constants.programmaticName);
        }
        return false;
    }
    
    public void onUserMessage(Object data) {
        adminListeners.forEach(l -> l.onUserMessage(data));
    }
    
    public String getOrderId(String clientId) {
        synchronized (orderIdsMapsLock) {
            return clientIdsToOrderIds.get(clientId);
        }
    }
    
    public String getClientId(String orderId) {
        synchronized (orderIdsMapsLock) {
            return clientIdsToOrderIds.inverseBidiMap().get(orderId);
        }
    }
    
    public String getPurifiedErrorMessage(String errorMessage) {
        String purifiedErrorMessage = errorMessage;
        if (errorMessage.contains("error")) {
            try {
                BmErrorMessage errorMessageObject = gson.fromJson(errorMessage, BmErrorMessage.class);
                String purifiedMessage = errorMessageObject.getMessage();
                if (!StringUtils.isEmpty(purifiedMessage)) {
                    purifiedErrorMessage = purifiedMessage;
                }
            } catch (Exception e) {
                Log.warn(errorMessage, e);
            }
        }
        return purifiedErrorMessage;
    }
    
    private void updatePosition (String symbol) {
        BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
        UnitPosition validPosition = instr.getValidPosition();
        updateValidPosition(validPosition, validPosition);
        reportPosition(instr, validPosition);
    }

    public boolean isLoginSuccessful() {
        return isLoginSuccessful;
    }

    public void setLoginSuccessful(boolean isLoginSuccessful) {
        this.isLoginSuccessful = isLoginSuccessful;
    }

    public void setAuthFailedReason(String lostConnectionReason) {
        this.authFailedReason = lostConnectionReason;
    }

    private int toIntOrMax(long value, int max) {
        return (int) Math.min(value, max);
    }
}
