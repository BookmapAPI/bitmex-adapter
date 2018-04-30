package bitmexAdapter;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import velox.api.layer1.layers.utils.OrderBook;

//BmInstrument is the short for Bitmex Instrument
//Cannot be named simply "Instrument" 
//because the Bookmap Layer0Api has a class named "Instrument"
public class BmInstrument {
	private String symbol;
	private double tickSize;
	private boolean isSubscribed = false;
	private boolean isFirstSnapshotParsed = false;
	private OrderBook orderBook = new OrderBook();
	private BlockingQueue<Msg> queue = new LinkedBlockingQueue<>();
	private HashMap<Long, Integer> pricesMap = new HashMap<>();

	public BmInstrument(String symbol, double tickSize) {
		super();
		this.symbol = symbol;
		this.tickSize = tickSize;
	}

	public BmInstrument() {
		super();
	}

	public BlockingQueue<Msg> getQueue() {
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
}
