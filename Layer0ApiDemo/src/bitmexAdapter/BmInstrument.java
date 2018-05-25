package bitmexAdapter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import velox.api.layer1.common.Log;
import velox.api.layer1.layers.utils.OrderBook;

/*BmInstrument is the short for Bitmex Instrument
Cannot be named simply "Instrument" 
because the Bookmap Layer0Api has a class named "Instrument"*/
public class BmInstrument {
	private String symbol;
	private double tickSize;
	private long multiplier;

	private boolean isSubscribed = false;
	private boolean isFirstSnapshotParsed = false;
	private OrderBook orderBook = new OrderBook();
	private BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
	private HashMap<Long, Integer> pricesMap = new HashMap<>();
	private Position validPosition = new Position(0L, "", "", 0L, 0L, 0L, 0D);
	
	private int executionsVolume = 0;
	private int sellOrdersCount = 0;
	private int buyOrdersCount = 0;
	
	

	//	This is a temporary solution
	// Generally there must be one queue for any so called dataUnit, no matter which, 
	//either order update data or trade or position or whatever
	private BlockingQueue<BmOrder> executionQueue = new LinkedBlockingQueue<>();
	private BlockingQueue<Position> positionQueue = new LinkedBlockingQueue<>();


	

	public BmInstrument(String symbol, double tickSize) {
		super();
		this.symbol = symbol;
		this.tickSize = tickSize;
	}

	public BmInstrument() {
		super();
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
		return "{\"op\":\"subscribe\", \"args\":[\"orderBookL2:" + this.symbol + "\",\"trade:" + this.symbol + "\"]}";
	}

	public String getUnSubscribeReq() {
		return "{\"op\":\"unsubscribe\", \"args\":[\"orderBookL2:" + this.symbol + "\",\"trade:" + this.symbol + "\"]}";
	}

	public String getSymbol() {
		return symbol;
	}

	public double getTickSize() {
		return tickSize;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public void setTickSize(double tickSize) {
		this.tickSize = tickSize;
	}

	public OrderBook getOrderBook() {
			return orderBook;
	}

	public boolean isSubscribed() {
		return isSubscribed;
	}

	public void setSubscribed(boolean isSubscribed) {
		this.isSubscribed = isSubscribed;
	}
	
	public HashMap<Long, Integer> getPricesMap() {
		return pricesMap;
	}

	public Integer getPriceFromMap(long id) {
		return pricesMap.get(id);
	}
	
	public boolean isFirstSnapshotParsed() {
		return isFirstSnapshotParsed;
	}

	public void setFirstSnapshotParsed(boolean isFirstSnapshotParsed) {
		this.isFirstSnapshotParsed = isFirstSnapshotParsed;
	}
	
	public BlockingQueue<BmOrder> getExecutionQueue() {
		return executionQueue;
	}

	public void setExecutionQueue(BlockingQueue<BmOrder> executionQueue) {
		this.executionQueue = executionQueue;
	}

	public BlockingQueue<Position> getPositionQueue() {
		return positionQueue;
	}

	public void setPositionQueue(BlockingQueue<Position> positionQueue) {
		this.positionQueue = positionQueue;
	}

	public Position getValidPosition() {
		return validPosition;
	}

	public void setValidPosition(Position validPosition) {
		this.validPosition = validPosition;
	}

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
	
	
	
}
