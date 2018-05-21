package velox.api.layer0.live;

import java.io.BufferedReader;
import java.io.FileReader;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.devexperts.connector.Connector;

import bitmexAdapter.BmInstrument;
import bitmexAdapter.BmOrder;
import bitmexAdapter.DataUnit;
import bitmexAdapter.JsonParser;
import bitmexAdapter.Message;
import bitmexAdapter.MessageExecution;
import bitmexAdapter.Position;
import bitmexAdapter.TradeConnector;
import bitmexAdapter.TradeConnector.GeneralType;
import bitmexAdapter.TradeConnector.Method;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.ExecutionInfo;
import velox.api.layer1.data.Layer1ApiProviderSupportedFeatures;
import velox.api.layer1.data.OrderCancelParameters;
import velox.api.layer1.data.OrderDuration;
import velox.api.layer1.data.OrderInfoBuilder;
import velox.api.layer1.data.OrderInfoUpdate;
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
import velox.api.layer1.layers.utils.OrderBook;

/**
 * <p>
 * This provider generates data according to same rules as parent provider, but
 * also has some trading capabilities.
 * </p>
 *
 * <p>
 * It does not aim to be realistic, so it's somewhat simplified.
 * </p>
 */
public class DemoExternalRealtimeTradingProvider_2 extends DemoExternalRealtimeProviderTake_2 {

	AtomicInteger orderIdGenerator = new AtomicInteger();
	AtomicInteger executionIdGenerator = new AtomicInteger();

	String tempClientId;

	{
		Log.info("****************GET SYSINFO");
		try {
			getSystemInfo();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private HashMap<String, OrderInfoBuilder> workingOrders = new HashMap<>();

	public static String getSystemInfo() throws Exception {
		String sysInfofileName = "sysinfo.txt";
		ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "dxdiag", "/t", sysInfofileName);
		Process p = pb.start();
		p.waitFor();

		StringBuilder builder = new StringBuilder();

		BufferedReader reader = new BufferedReader(new FileReader(sysInfofileName));
		String line;
		while ((line = reader.readLine()) != null) {
			// if(line.trim().startsWith("Card name:") ||
			// line.trim().startsWith("Current Mode:")){
			builder.append(line).append('\n');
			// }
		}
		reader.close();

		return builder.toString();
	}

	@Override
	public void sendOrder(OrderSendParameters orderSendParameters) {
		Log.info("*******sendOrder*******");

		// Since we did not report OCO/OSO/Brackets support, this method can
		// only receive simple orders
		SimpleOrderSendParameters simpleParameters = (SimpleOrderSendParameters) orderSendParameters;

		// Detecting order type
		OrderType orderType = OrderType.getTypeFromPrices(simpleParameters.stopPrice, simpleParameters.limitPrice);

		Log.info("***orderType = " + orderType.toString());

		// Even if order will be rejected provider should first acknowledge it
		// with PENDING_SUBMIT.
		// This allows Bookmap visualization to distinguish between orders that
		// were just sent and orders that were rejected earlier and now the last
		// state is reported
		// If your datasource does not provide some variation of PENDING_SUBMIT
		// status, you are advised to send a fake message with PENDING_SUBMIT
		// before reporting REJECT - this will make Bookmap consider all rejects
		// to be new ones instead of historical ones

		// ****************** TO BITMEX
		tempClientId = simpleParameters.clientId;
		Log.info("CLIENT ID " + tempClientId);
		Log.info("***Order gets sent to BitMex");
		BmOrder ord = connr.processNewOrder(simpleParameters);
		String bmId = ord.getOrderID();
		Log.info("BM_ID " + bmId);
		// ****************** TO BITMEX ENDS

		Log.info("***OrderInfoBuilder builder CREATED");
		final OrderInfoBuilder builder = new OrderInfoBuilder(simpleParameters.alias, bmId, simpleParameters.isBuy,
				orderType, simpleParameters.clientId, simpleParameters.doNotIncrease);

		// You need to set these fields, otherwise Bookmap might not handle
		// order correctly
		builder.setStopPrice(simpleParameters.stopPrice).setLimitPrice(simpleParameters.limitPrice)
				.setUnfilled(simpleParameters.size).setDuration(OrderDuration.GTC)
				.setStatus(OrderStatus.PENDING_SUBMIT);

		tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
		// Marking all fields as unchanged, since they were just reported and
		// fields will be marked as changed automatically when modified.
		builder.markAllUnchanged();

		// First, since we are not going to emulate stop or market orders in
		// this demo,
		// let's reject anything except for Limit orders.
		if (orderType != OrderType.LMT) {

			Log.info("***Order gets REJECTED");

			// Necessary fields are already populated, so just change status to
			// rejected and send
			builder.setStatus(OrderStatus.REJECTED);
			tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
			builder.markAllUnchanged();

			// Provider can complain to user here explaining what was done wrong
			adminListeners.forEach(l -> l.onSystemTextMessage("This provider only supports limit orders",
					SystemTextMessageType.ORDER_FAILURE));
		} else {

			// ****************** TO BITMEX
			// Log.info("***Order gets sent to BitMex");
			// BmOrder ord = connr.processNewOrder(simpleParameters);
			// String bmId = ord.getOrderID();
			// ****************** TO BITMEX ENDS

			// We are going to simulate this order, entering WORKING state
			builder.setStatus(OrderStatus.WORKING);

			tradingListeners.forEach(l -> l.onOrderUpdated(builder.build()));
			builder.markAllUnchanged();

			synchronized (workingOrders) {
				// workingOrders.put(builder.orderId, builder);
				workingOrders.put(builder.getOrderId(), builder);
			}

			// TEST
			// long moment = connr.getMoment();
			// String addr;
			// String data0;
			// String st0;
			// String sign;
			// String data1 = "";
			// data0 = "?filter=%7B%22open%22:true%7D";
			// addr = "/api/v1/order?filter=%7B%22open%22:true%7D";
			// try {
			// sign = connr.generateSignature(connr.orderApiSecret,
			// connr.createMessageBody("GET", addr, data1, moment));
			// st0 = connr.get("https://testnet.bitmex.com" + addr,
			// connr.orderApiKey,
			// sign,
			// moment,
			// data0);
			// Log.info("OLD ORDER RETURNED FROM SENDORDER METHOD" + st0);
			//
			// BmOrder[] orders = JsonParser.getArrayFromJson(st0,
			// BmOrder[].class);
			// for(BmOrder order : orders ){
			// order.setSnapshot(true);
			//// instr.getExecutionQueue().add(order);
			// this.createBookmapOrder(order);
			// }
			//
			// } catch (InvalidKeyException | NoSuchAlgorithmException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }
			//

		}

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
		int size = (int) order.getLeavesQty();

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

		synchronized (workingOrders) {
			// workingOrders.put(builder.orderId, builder);
			workingOrders.put(orderId, builder);
			Log.info("BM ORDER PUT");
		}
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

				OrderInfoBuilder order = workingOrders.remove(orderCancelParameters.orderId);
				order.setStatus(OrderStatus.CANCELLED);
				tradingListeners.forEach(l -> l.onOrderUpdated(order.build()));

			} else if (orderUpdateParameters.getClass() == OrderResizeParameters.class) {

				Log.info("***order with provided ID gets RESIZED");

				// Resize order with provided ID
				OrderResizeParameters orderResizeParameters = (OrderResizeParameters) orderUpdateParameters;

				BmOrder ord = connr.resizeOrder(orderResizeParameters.orderId, orderResizeParameters.size);

				OrderInfoBuilder order = workingOrders.get(orderResizeParameters.orderId);

				if (order == null) {
					Log.info("ORDER IS NULL");
				}

				order.setUnfilled(orderResizeParameters.size);
				tradingListeners.forEach(l -> l.onOrderUpdated(order.build()));

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
	protected void simulate() {
		// Log.info("***simulate() started");
		// Perform data changes simulation
		this.connector.provider = this;
		super.simulate();

		simulateOrders();
	}

	public void simulateOrders() {

		// Log.info("*******simulateOrders*******");

		// Simulate order executions
		synchronized (workingOrders) {
			synchronized (instruments) {
				// Purging orders that are no longer working - those do not have
				// to be simulated
				workingOrders.values().removeIf(o -> o.getStatus() != OrderStatus.WORKING);

				for (OrderInfoBuilder order : workingOrders.values()) {
					Instrument instrument = instruments.get(order.getInstrumentAlias());
					// Instrument instrument =
					// instruments.get(order.instrumentAlias);

					// Log.info("***instrument = " + instrument.toString());

					// Only simulating if user is subscribed to instrument -
					// this is because we do not generate data when there is no
					// subscription
					if (instrument != null) {
						// Determining on which price level order can be
						// executed. Note the multiplication by pips part -
						// that's because order price is a raw value and
						// instrument bid/ask are level numbers.

						String symbol = connr.isolateSymbol(instrument.alias);
						BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);

						BlockingQueue<BmOrder> messages = bmInstrument.getExecutionQueue();
						if (!messages.isEmpty()) {
							BmOrder orderExec = messages.poll();

							if (orderExec.getOrdStatus().equals("Filled")) {
								final long executionTime = System.currentTimeMillis();
								int filled = (int) Math.round(orderExec.getCumQty() / bmInstrument.getTickSize());
								ExecutionInfo executionInfo = new ExecutionInfo(orderExec.getOrderID(), filled,
										orderExec.getLastPx(), orderExec.getExecID(), executionTime);

								tradingListeners.forEach(l -> l.onOrderExecuted(executionInfo));

								// Changing the order itself
								order.setAverageFillPrice(orderExec.getLastPx());
								order.setUnfilled(0);
								order.setFilled(filled);
								order.setStatus(OrderStatus.FILLED);
								tradingListeners.forEach(l -> l.onOrderUpdated(order.build()));
								order.markAllUnchanged();
							}
						}
						
//						BlockingQueue<Position> messPos = bmInstrument.getPositionQueue();
//						if (!messPos.isEmpty()) {
//							Position pos = messPos.poll();
//							
//							Log.info("POSITION FOR STATUS INFO");
//							Log.info("UnrPnl" + "\t\t" + 
//									"RsdPnl" + "\t" +
//									"Cur" + "\t" +
//									"CntQ" + "\t" +
//									"AvEnPr" + "\t" +
//									"BQty" + "\t" +
//									"SQty" + "\t" +
//									"OpOrBQ" + "\t" +
//									"OpOrSQ");
//							Log.info((double) pos.getUnrealisedPnl() + "\t\t" + 
//									(double) pos.getRealisedPnl()+ "\t" +
//									pos.getCurrency()+ "\t" +
//									(int) pos.getCurrentQty()+ "\t" +
//									pos.getAvgEntryPrice()+ "\t" +
//									(int) pos.getExecBuyQty() + "\t" +
//									(int) pos.getExecSellQty() + "\t" +
//									(int) pos.getOpenOrderBuyQty()+ "\t" +
//									(int) pos.getOpenOrderSellQty());
//							
//							
//							StatusInfo info = new StatusInfo(pos.getSymbol(),
//									(double) pos.getUnrealisedPnl(),
//									(double) pos.getRealisedPnl(),
//									pos.getCurrency(),
//									(int) pos.getCurrentQty(), //This one is arguable
//									pos.getAvgEntryPrice(), //This one is arguable
//									(int) pos.getExecBuyQty() + (int) pos.getExecSellQty(), //This one is arguable
//									(int) pos.getOpenOrderBuyQty(),
////									10);
//							(int) pos.getOpenOrderSellQty());
//
//								tradingListeners.forEach(l -> l.onStatus(info));
//
//							
//						}
						
					}
				}
			}
		}
	}
}
