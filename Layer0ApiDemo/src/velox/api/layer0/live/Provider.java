package velox.api.layer0.live;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import velox.api.layer1.Layer1ApiAdminListener;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.ExecutionInfo;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.InstrumentInfoCrypto;
import velox.api.layer1.data.Layer1ApiProviderSupportedFeatures;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.LoginFailedReason;
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
import velox.api.layer1.data.SystemTextMessageType;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.data.UserPasswordDemoLoginData;

import bitmexAdapter.BitmexConnector;
import bitmexAdapter.DataUnit;
import bitmexAdapter.Message;
import bitmexAdapter.Position;
import bitmexAdapter.TradeConnector;
import bitmexAdapter.BmInstrument;
import bitmexAdapter.BmOrder;

public class Provider extends ExternalLiveBaseProvider {

	public BitmexConnector connector = new BitmexConnector();
	public TradeConnector connr = new TradeConnector();
	private String tempClientId;
	private HashMap<String, OrderInfoBuilder> workingOrders = new HashMap<>();

	protected class Instrument {

		protected final String alias;
		protected final double pips;

		public Instrument(String alias, double pips) {
			this.alias = alias;
			this.pips = pips;
		}

//		public void generateData(String symbol) {
//			BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);
//
//			if (!bmInstrument.isFirstSnapshotParsed()) {
//				return;
//			}
//			BlockingQueue<Message> messages = bmInstrument.getQueue();
//
//			if (!messages.isEmpty()) {
//				Message message = messages.poll();
//
//				// if (message == null || message.getAction() == null ||
//				// message.getData() == null) {
//				// Log.info("***********NULL POINTER AT MESSAGE " + message);
//				// }
//
//				List<DataUnit> units = message.data;
//
//				if (message.table.equals("orderBookL2")) {
//					for (DataUnit unit : units) {
//						for (Layer1ApiDataListener listener : dataListeners) {
//							listener.onDepth(symbol, unit.isBid(), unit.getIntPrice(), (int) unit.getSize());
//						}
//					}
//				} else {
//					for (DataUnit unit : units) {
//						for (Layer1ApiDataListener listener : dataListeners) {
//							final boolean isOtc = false;
//							listener.onTrade(symbol, unit.getIntPrice(), (int) unit.getSize(),
//									new TradeInfo(isOtc, unit.isBid()));
//						}
//					}
//				}
//			}
//
//		}
	}

	protected HashMap<String, Instrument> instruments = new HashMap<>();

	// This thread will perform data generation.
	private Thread connectionThread = null;

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
	public void subscribe(String symbol, String exchange, String type) {

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
				HashMap<String, BmInstrument> activeBmInstruments = this.connector.getActiveInstrumentsMap();
				Set<String> set = new HashSet<>();

				synchronized (activeBmInstruments) {
					if (activeBmInstruments.isEmpty()) {
						try {
							activeBmInstruments.wait();// waiting for the
														// instruments map to be
														// filled...
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					for (String key : activeBmInstruments.keySet()) {
						set.add(key);// copying map's keyset to a new set
					}
				}

				if (set.contains(symbol)) {
					// try {
					// this.connector.getWebSocketStartingLatch().await();
					// } catch (InterruptedException e) {
					// e.printStackTrace();
					// }
					BmInstrument instr = activeBmInstruments.get(symbol);
					double pips = instr.getTickSize();

					final Instrument newInstrument = new Instrument(alias, pips);
					instruments.put(alias, newInstrument);
					final InstrumentInfo instrumentInfo = new InstrumentInfo(symbol, exchange, type, newInstrument.pips,
							1, "", false);

					instrumentListeners.forEach(l -> l.onInstrumentAdded(alias, instrumentInfo));
					this.connector.subscribe(instr);
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
		BmInstrument instr = this.connector.getActiveInstrumentsMap().get(alias);
		this.connector.unSubscribe(instr);

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
		Log.info("*******sendOrder*******");
		SimpleOrderSendParameters simpleParameters = (SimpleOrderSendParameters) orderSendParameters;
		// Detecting order type
		OrderType orderType = OrderType.getTypeFromPrices(simpleParameters.stopPrice, simpleParameters.limitPrice);
		Log.info("***orderType = " + orderType.toString());

		final OrderInfoBuilder builder = new OrderInfoBuilder(simpleParameters.alias, 
				"temp" + System.currentTimeMillis(), 
				simpleParameters.isBuy,
				orderType, simpleParameters.clientId, 
				simpleParameters.doNotIncrease);

		// You need to set these fields, otherwise Bookmap might not handle
		// order correctly
		builder.setStopPrice(simpleParameters.stopPrice).setLimitPrice(simpleParameters.limitPrice)
				.setUnfilled(simpleParameters.size).setDuration(OrderDuration.GTC)
				.setStatus(OrderStatus.PENDING_SUBMIT);

		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		// Marking all fields as unchanged, since they were just reported and
		// fields will be marked as changed automatically when modified.
		builder.markAllUnchanged();

		if (orderType == OrderType.STP || orderType == OrderType.LMT || orderType == OrderType.STP_LMT
				|| orderType == OrderType.MKT) {
			// ****************** TO BITMEX
			String tempClientId = simpleParameters.clientId;
			Log.info("CLIENT ID " + tempClientId);
			Log.info("***Order gets sent to BitMex");
			BmOrder ord = connr.processNewOrder(simpleParameters, orderType);
			if (ord == null) {
				rejectOrder(builder);
			} else {
				String bmId = ord.getOrderID();
				Log.info("BM_ID " + bmId);
				// ****************** TO BITMEX ENDS
				BmInstrument instr = connector.getActiveInstrumentsMap().get(ord.getSymbol());
				if (simpleParameters.isBuy) {
					instr.setBuyOrdersCount(instr.getBuyOrdersCount() + simpleParameters.size);
				} else {
					instr.setSellOrdersCount(instr.getSellOrdersCount() + simpleParameters.size);
				}

				builder.setOrderId(bmId);
				builder.setStatus(OrderStatus.WORKING);
				tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
				builder.markAllUnchanged();

				synchronized (workingOrders) {
					workingOrders.put(builder.getOrderId(), builder);
				}
			}

		} else {
			rejectOrder(builder);
		}

	}

	private void rejectOrder(OrderInfoBuilder builder) {
		Log.info("***Order gets REJECTED");

		// Necessary fields are already populated, so just change status to
		// rejected and send
		builder.setStatus(OrderStatus.REJECTED);
		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		builder.markAllUnchanged();

		// Provider can complain to user here explaining what was done wrong
		adminListeners.forEach(l -> l.onSystemTextMessage("The order was rejected",
				// adminListeners.forEach(l -> l.onSystemTextMessage("This
				// provider only supports limit orders",
				SystemTextMessageType.ORDER_FAILURE));
	}
	@Override
	public void updateOrder(OrderUpdateParameters orderUpdateParameters) {

		Log.info("*******updateOrder*******");

		// OrderMoveToMarketParameters will not be sent as we did not declare
		// support for it, 3 other requests remain

		synchronized (workingOrders) {
			// instanceof is not recommended here because subclass, if it
			// appears,
			// will anyway mean an action that existing code can not process as
			// expected
			if (orderUpdateParameters.getClass() == OrderCancelParameters.class) {

				Log.info("***order with provided ID gets CANCELLED");

				// Cancel order with provided ID
				OrderCancelParameters orderCancelParameters = (OrderCancelParameters) orderUpdateParameters;
				connr.cancelOrder(orderCancelParameters.orderId);

				OrderInfoBuilder builder = workingOrders.get(orderCancelParameters.orderId);

				String symbol = connr.isolateSymbol(builder.getInstrumentAlias());
				BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
				if (builder.isBuy()) {
					instr.setBuyOrdersCount(instr.getBuyOrdersCount() - builder.getUnfilled());
				} else {
					instr.setSellOrdersCount(instr.getSellOrdersCount() - builder.getUnfilled());
				}

				OrderInfoBuilder order = workingOrders.remove(orderCancelParameters.orderId);
				order.setStatus(OrderStatus.CANCELLED);
				tradingListeners.forEach(l -> l.onOrderUpdated(order.build()));

			} else if (orderUpdateParameters.getClass() == OrderResizeParameters.class) {

				Log.info("***order with provided ID gets RESIZED");

				// Resize order with provided ID
				OrderResizeParameters orderResizeParameters = (OrderResizeParameters) orderUpdateParameters;
				Log.info("RESIZE ORDER BY " + orderResizeParameters.size);

				int newSize = orderResizeParameters.size;
				BmOrder ord = connr.resizeOrder(orderResizeParameters.orderId, newSize);

				OrderInfoBuilder builder = workingOrders.get(orderResizeParameters.orderId);

				if (builder == null) {
					Log.info("ORDER IS NULL");
				}
				int oldSize = builder.getUnfilled();

				String symbol = connr.isolateSymbol(ord.getSymbol());
				BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
				if (builder.isBuy()) {
					instr.setBuyOrdersCount(instr.getBuyOrdersCount() + newSize - oldSize);
				} else {
					instr.setSellOrdersCount(instr.getSellOrdersCount() + newSize - oldSize);
				}

				// if (workingOrders.get(orderResizeParameters.orderId).isBuy())
				// {
				// buyOrdersCount += orderResizeParameters.size -
				// builder.getUnfilled();
				// } else {
				// sellOrdersCount += orderResizeParameters.size -
				// builder.getUnfilled();
				// }

				builder.setUnfilled(orderResizeParameters.size);
				tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));

			} else if (orderUpdateParameters.getClass() == OrderMoveParameters.class) {

				Log.info("***Change stop/limit prices of an order with provided ID");

				// Change stop/limit prices of an order with provided ID
				OrderMoveParameters orderMoveParameters = (OrderMoveParameters) orderUpdateParameters;

				connr.moveOrder(orderMoveParameters.orderId, orderMoveParameters.limitPrice);
				OrderInfoBuilder order = workingOrders.get(orderMoveParameters.orderId);
				// No need to update stop price as this demo only supports limit
				// orders

				if (order == null) {
					Log.info("ORDER IS NULL");
				}

				order.setLimitPrice(orderMoveParameters.limitPrice);
				tradingListeners.forEach(l -> l.onOrderUpdated(order.build()));

				// New price might trigger execution
				// simulateOrders();

			} else {
				throw new UnsupportedOperationException("Unsupported order type");
			}

		}
	}

	@Override
	public void login(LoginData loginData) {
		UserPasswordDemoLoginData userPasswordDemoLoginData = (UserPasswordDemoLoginData) loginData;
		// If connection process takes a while then it's better to do it in
		// separate thread
		connectionThread = new Thread(() -> handleLogin(userPasswordDemoLoginData));
		connectionThread.setName("-> INSTRUMENT");
		connectionThread.start();
	}

	private void handleLogin(UserPasswordDemoLoginData userPasswordDemoLoginData) {
		// With real connection provider would attempt establishing connection
		// here.

		// there is no need in password check for demo purposes
		// boolean isValid = "pass".equals(userPasswordDemoLoginData.password)
		// && "user".equals(userPasswordDemoLoginData.user) &&
		// userPasswordDemoLoginData.isDemo == true;

		// if (isValid) {
		// Report succesful login
		adminListeners.forEach(Layer1ApiAdminListener::onLoginSuccessful);

		// CONNECTOR
		// this.connector = new BitmexConnector();
		this.connector.prov = this;
		this.connector.setTrConn(connr);
		Thread thread = new Thread(this.connector);
		thread.setName("->BitmexAdapter: connector");
		thread.start();

		// Generate some events each second
		// while (!Thread.interrupted()) {

		// Generate some data changes
		// simulate();
		// }
		// } else {
		// // Report failed login
		// adminListeners.forEach(l ->
		// l.onLoginFailed(LoginFailedReason.WRONG_CREDENTIALS,
		// "This provider only acepts following credentials:\n" + "username:
		// user\n" + "password: pass\n"
		// + "is demo: checked"));
		// }
	}

//	protected void simulate() {
//		// Generating some data for each of the instruments
//		synchronized (instruments) {
//			// instruments.values().forEach(Instrument::generateData);
//			for (Instrument instrument : instruments.values()) {
//				instrument.generateData(instrument.alias);
//			}
//		}
//	}

	public void listenOrderOrTrade(Message message) {
		// Log.info("LISTENER USED");

		if (message == null || message.getAction() == null || message.getData() == null) {
			Log.info("***********NULL POINTER AT MESSAGE " + message);
		}

		String symbol = message.data.get(0).getSymbol();

		BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);

		if (!bmInstrument.isFirstSnapshotParsed()) {
			return;
		}

		List<DataUnit> units = message.data;

		if (message.table.equals("orderBookL2")) {
			for (DataUnit unit : units) {
				for (Layer1ApiDataListener listener : dataListeners) {
					listener.onDepth(symbol, unit.isBid(), unit.getIntPrice(), (int) unit.getSize());
				}
			}
		} else {
			for (DataUnit unit : units) {
				for (Layer1ApiDataListener listener : dataListeners) {
					final boolean isOtc = false;
					listener.onTrade(symbol, unit.getIntPrice(), (int) unit.getSize(),
							new TradeInfo(isOtc, unit.isBid()));
				}
			}
		}

	}

	public void listenToExecution(BmOrder orderExec){
		String symbol = orderExec.getSymbol();
		BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
		
		if (orderExec.getOrdStatus().equals("Filled")) {
			final long executionTime = System.currentTimeMillis();
			int filled = (int) Math.round(orderExec.getCumQty() / instr.getTickSize());
			ExecutionInfo executionInfo = new ExecutionInfo(orderExec.getOrderID(), filled,
					orderExec.getLastPx(), orderExec.getExecID(), executionTime);

			tradingListeners.forEach(l -> l.onOrderExecuted(executionInfo));

			OrderInfoBuilder builder = workingOrders.get(orderExec.getOrderID());
			// updating filled orders volume
			instr.setExecutionsVolume(
					instr.getExecutionsVolume() + (int) orderExec.getSimpleCumQty());
			if (builder.isBuy()) {
				instr.setBuyOrdersCount(instr.getBuyOrdersCount() - builder.getUnfilled());
				// buyOrdersCount -= builder.getUnfilled();
			} else {
				instr.setSellOrdersCount(instr.getSellOrdersCount() - builder.getUnfilled());
				// sellOrdersCount -= builder.getUnfilled();
			}

			// Changing the order itself
			OrderInfoBuilder order = workingOrders.get(orderExec.getOrderID());
			order.setAverageFillPrice(orderExec.getLastPx());
			order.setUnfilled(0);
			order.setFilled(filled);
			order.setStatus(OrderStatus.FILLED);
			tradingListeners.forEach(l -> l.onOrderUpdated(order.build()));
			order.markAllUnchanged();
		}
	}
	
	public void listenToPosition(Position pos) {

		String symbol = pos.getSymbol();
		BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);

		Position validPosition = bmInstrument.getValidPosition();
		updateValidPosition(validPosition, pos);
		Log.info("NEW VAL" + validPosition.toString());

		BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);

		StatusInfo info = new StatusInfo(validPosition.getSymbol(),
				(double) validPosition.getUnrealisedPnl() / (double) bmInstrument.getMultiplier(),
				(double) validPosition.getRealisedPnl() / (double) bmInstrument.getMultiplier(),
				validPosition.getCurrency(),
				(int) Math.round((double) (validPosition.getMarkValue() - validPosition.getUnrealisedPnl())
						/ (double) bmInstrument.getMultiplier()),
				validPosition.getAvgEntryPrice(), 
				bmInstrument.getExecutionsVolume(),
				instr.getBuyOrdersCount(), instr.getSellOrdersCount());

		Log.info(info.toString());

		tradingListeners.forEach(l -> l.onStatus(info));

	}
	
	private void updateValidPosition(Position validPosition, Position pos) {
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
		Log.info("WTN MTH" + validPosition.toString());
	}
	
	public void createBookmapOrder(BmOrder order) {
		String symbol = order.getSymbol();
		String orderId = order.getOrderID();
		boolean isBuy = order.getSide().equals("Buy") ? true : false;
		OrderType type = order.getOrdType().equals("Limit") ? OrderType.LMT : OrderType.STP;
		// String clientId =order.getClientId();//this field is being left blank
		// so far
		String clientId = tempClientId;
		Log.info("To BM CLIENT ID " + tempClientId);

		boolean doNotIncrease = true;// this field is being left true so far

		double stopPrice = type.equals(OrderType.STP) ? order.getPrice() : Double.NaN;
		double limitPrice = type.equals(OrderType.LMT) ? order.getPrice() : Double.NaN;
		// int size = (int) order.getLeavesQty();
		int size = (int) order.getSimpleLeavesQty();
		Log.info("SIMPLE LEAVES QTY = " + size);

		final OrderInfoBuilder builder = new OrderInfoBuilder(symbol, orderId, isBuy, type, clientId, doNotIncrease);

		// You need to set these fields, otherwise Bookmap might not handle
		// order correctly
		builder.setStopPrice(stopPrice).setLimitPrice(limitPrice).setUnfilled(size).setDuration(OrderDuration.GTC)
				.setStatus(OrderStatus.PENDING_SUBMIT);
		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		builder.markAllUnchanged();

		builder.setStatus(OrderStatus.WORKING);
		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		builder.markAllUnchanged();

		BmInstrument instr = connector.getActiveInstrumentsMap().get(symbol);
		if (isBuy) {
			instr.setBuyOrdersCount(instr.getBuyOrdersCount() + size);
			// buyOrdersCount += simpleParameters.size;
		} else {
			instr.setSellOrdersCount(instr.getSellOrdersCount() + size);
			// sellOrdersCount += simpleParameters.size;
		}

		synchronized (workingOrders) {
			// workingOrders.put(builder.orderId, builder);
			workingOrders.put(orderId, builder);
			Log.info("BM ORDER PUT");
		}
	}

	@Override
	public Layer1ApiProviderSupportedFeatures getSupportedFeatures() {
		// Expanding parent supported features, reporting basic trading support
		return super.getSupportedFeatures().toBuilder().setTrading(true)
				.setSupportedOrderDurations(Arrays.asList(new OrderDuration[] { OrderDuration.GTC }))
				// At the moment of writing this method it was not possible to
				// report limit orders support, but no stop orders support
				// If you actually need it, you can report stop orders support
				// but reject stop orders when those are sent.
				.setSupportedStopOrders(Arrays.asList(new OrderType[] { OrderType.LMT, OrderType.STP })).build();
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
		connectionThread.interrupt();
	}
	
	

}
