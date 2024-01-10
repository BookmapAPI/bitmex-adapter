package com.bookmap.plugins.layer0.bitmex.adapter;

import java.util.HashMap;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.WebSocketOperation;

import velox.api.layer1.common.Log;
import velox.api.layer1.layers.utils.OrderBook;
import velox.api.layer1.layers.utils.OrderByOrderBook;

/*BmInstrument is the short for BitMEX Instrument
Cannot be named simply "Instrument" 
because the Bookmap Layer0Api has a class named "Instrument"*/
public class BmInstrument {
	private String symbol;
	//Bitmex representation of type
	private String typ;
	/**
	 * The native min tick size of an instrument
	 */
	private double tickSize;
	private long multiplier;
	private long underlyingToSettleMultiplier;
	private String settlCurrency;
	private boolean isSubscribed;
	private double initMargin;

	private OrderByOrderBook orderBook = new OrderByOrderBook();
	private BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
	private HashMap<Long, Double> pricesMap = new HashMap<>();
	private UnitPosition validPosition = new UnitPosition(0L, "", "", 0L, 0L, 0L, 0D);
	private boolean orderBookSnapshotParsed = false;
	private double lastBuy = Double.NaN;
	private double lastSell = Double.NaN;
	private int executionsVolume = 0;
	private int sellOrdersCount = 0;
	private int buyOrdersCount = 0;
	private double lotSize;
	private long underlyingToPositionMultiplier;
	private double sizeMultiplier = Double.NaN;


	/**
	 * The tick size a user has been subscribed to.
	 */
	private double activeTickSize;
	private double activeSizeMultiplier;

    {//temp workaround
        this.validPosition.setOpenOrderBuyQty(0);
        this.validPosition.setOpenOrderSellQty(0);
    }

	private transient Timer snapshotTimer = null;

	public BmInstrument(String symbol, double tickSize) {
		super();
		this.symbol = symbol;
		this.tickSize = tickSize;
	}

	public BmInstrument() {
		super();
	}


	public Timer getSnapshotTimer() {
//		return null;
		return snapshotTimer;
	}

	public void setSnapshotTimer(Timer snapshotTimer) {
		this.snapshotTimer = snapshotTimer;
	}

	public boolean isOrderBookSnapshotParsed() {
		return orderBookSnapshotParsed;
	}

	public void setOrderBookSnapshotParsed(boolean orderBookSnapshotParsed) {
        if (snapshotTimer != null) {
            snapshotTimer.cancel();
        }
		this.orderBookSnapshotParsed = orderBookSnapshotParsed;
	}

	public int getExecutionsVolume() {
		return executionsVolume;
	}

	public void setExecutionsVolume(int executionsVolume) {
		this.executionsVolume = executionsVolume;
	}

	public long getMultiplier() {
		return multiplier;
	}

	public void setMultiplier(long multiplier) {
		this.multiplier = multiplier;
	}

	public BlockingQueue<Message> getQueue() {
		return queue;
	}

	public String getSubscribeReq() {
		WsData wsData = new WsData(this.symbol, WebSocketOperation.SUBSCRIBE,
				(Object[]) ConnectorUtils.getNonAuthenticatedTopicsList());
		String res = JsonParser.gson.toJson(wsData);
		return res;
	}

	public String getUnSubscribeReq() {
		WsData wsData = new WsData(this.symbol, WebSocketOperation.UNSUBSCRIBE,
				(Object[]) ConnectorUtils.getNonAuthenticatedTopicsList());
		String res = JsonParser.gson.toJson(wsData);
		return res;
	}

	public String getSymbol() {
		return symbol;
	}

	/**
	 * @return the native min tick size of an instrument<br>
	 * use {@link #getActiveTickSize()} for the current tick size
	 */
	public double getTickSize() {
		return tickSize;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public void setTickSize(double tickSize) {
		this.tickSize = tickSize;
	}

	public OrderByOrderBook getOrderBook() {
		return orderBook;
	}

	public void resetOrderBook(){
		orderBook = new OrderByOrderBook();
		pricesMap = new HashMap<>();
	}

	public void clearOrderBook() {
		this.orderBook = new OrderByOrderBook();
	}

	public boolean isSubscribed() {
		return isSubscribed;
	}

	public void setSubscribed(boolean isSubscribed) {
		this.isSubscribed = isSubscribed;
	}

	public HashMap<Long, Double> getPricesMap() {
		return pricesMap;
	}

	public UnitPosition getValidPosition() {
		return validPosition;
	}

	// public void setValidPosition(UnitPosition validPosition) {
	// this.validPosition = validPosition;
	// }

	public int getSellOrdersCount() {
		return sellOrdersCount;
	}

	public void setSellOrdersCount(int sellOrdersCount) {
	    Log.info("setSellOrdersCount " + sellOrdersCount);
		this.sellOrdersCount = sellOrdersCount;
	}

	public int getBuyOrdersCount() {
		return buyOrdersCount;
	}

	public void setBuyOrdersCount(int buyOrdersCount) {
	    Log.info("setBuyOrdersCount " + buyOrdersCount);
		this.buyOrdersCount = buyOrdersCount;
	}

	public long getUnderlyingToSettleMultiplier() {
		return underlyingToSettleMultiplier;
	}

	public void setUnderlyingToSettleMultiplier(long underlyingToSettleMultiplier) {
		this.underlyingToSettleMultiplier = underlyingToSettleMultiplier;
	}

	public String getSettlCurrency() {
		return settlCurrency;
	}

	public void setSettlCurrency(String settlCurrency) {
		this.settlCurrency = settlCurrency;
	}

	public double getLastBuy() {
		return lastBuy;
	}

	public double getLastSell() {
		return lastSell;
	}

	public void setLastBuy(double lastBuy) {
		this.lastBuy = lastBuy;
	}

	public void setLastSell(double lastSell) {
		this.lastSell = lastSell;
	}

    public double getInitMargin() {
        return initMargin;
    }

    public void setInitMargin(double initMargin) {
        this.initMargin = initMargin;
    }

	public double getActiveTickSize() {
		return activeTickSize;
	}

	public double getActiveSizeMultiplier() {
		return activeSizeMultiplier;
	}

	public void setActiveTickSize(double activeTickSize) {
		this.activeTickSize = activeTickSize;
	}

	public void setActiveSizeMultiplier(double activeSizeMultiplier) {
		this.activeSizeMultiplier = activeSizeMultiplier;
	}

	public String getTyp() {
		return typ;
	}

	public void setTyp(String typ) {
		this.typ = typ;
	}


	public long getUnderlyingToPositionMultiplier() {
		return underlyingToPositionMultiplier;
	}

	public void setUnderlyingToPositionMultiplier(long underlyingToPositionMultiplier) {
		this.underlyingToPositionMultiplier = underlyingToPositionMultiplier;
	}

	public double getLotSize() {
		return lotSize;
	}

	public void setLotSize(double lotSize) {
		this.lotSize = lotSize;
	}

	public double getSizeMultiplier() {
		if (Double.isNaN(sizeMultiplier)){
			sizeMultiplier = underlyingToPositionMultiplier/lotSize;
		}
		return sizeMultiplier;
	}

	public boolean isSpot(){
		return typ.equals(Constants.typesToSpecifiers.get("SPOT"));
	}
}
