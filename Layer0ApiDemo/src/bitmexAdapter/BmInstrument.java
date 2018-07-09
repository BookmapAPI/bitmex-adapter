package bitmexAdapter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
	private long underlyingToSettleMultiplier;
	private String settlCurrency;

	

	private boolean isSubscribed = false;
	private boolean isFirstSnapshotParsed = false;
	
	//this one is for 'orderBookL2 and for 'trade'
	private Map<String, Boolean> instrumentPartialsParsed = new HashMap<>();
	
	private OrderBook orderBook = new OrderBook();
	private BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
	private HashMap<Long, Integer> pricesMap = new HashMap<>();
	private UnitPosition validPosition = new UnitPosition(0L, "", "", 0L, 0L, 0L, 0D);
	
	private Set<String> partialsParsed = new HashSet<>();

	
	private double lastBuy = Double.NaN;
	private double lastSell = Double.NaN;
	
	private int executionsVolume = 0;
	private int sellOrdersCount = 0;
	private int buyOrdersCount = 0;
	
	

	//	This is a temporary solution
	// Generally there must be one queue for any so called dataUnit, no matter which, 
	//either order update data or trade or position or whatever
	private BlockingQueue<UnitOrder> executionQueue = new LinkedBlockingQueue<>();
	private BlockingQueue<UnitPosition> positionQueue = new LinkedBlockingQueue<>();


	

	public BmInstrument(String symbol, double tickSize) {
		super();
		this.symbol = symbol;
		this.tickSize = tickSize;
	}

	public BmInstrument() {
		super();
	}
	
	public Set<String> getPartialsParsed() {
		return partialsParsed;
	}

	public void setPartialsParsed(Set<String> partialsParsed) {
		this.partialsParsed = partialsParsed;
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
//		return "{\"op\":\"subscribe\", \"args\":[\"orderBookL2:" + this.symbol + "\",\"trade:" + this.symbol + "\",\"order:" + this.symbol + "\"]}";
//		return "{\"op\":\"subscribe\", \"args\":[\"orderBookL2:" + this.symbol + "\",\"trade:" + this.symbol + "\",\"order:" + this.symbol + "\",\"execution:" + this.symbol + "\"]}";
	}

	public String getUnSubscribeReq() {
		return "{\"op\":\"unsubscribe\", \"args\":[\"orderBookL2:" + this.symbol + "\",\"trade:" + this.symbol + "\"]}";	}

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
	
	public void clearOrderBook() {
		 this.orderBook = new OrderBook();
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
	
	public BlockingQueue<UnitOrder> getExecutionQueue() {
		return executionQueue;
	}

	public void setExecutionQueue(BlockingQueue<UnitOrder> executionQueue) {
		this.executionQueue = executionQueue;
	}

	public BlockingQueue<UnitPosition> getPositionQueue() {
		return positionQueue;
	}

	public void setPositionQueue(BlockingQueue<UnitPosition> positionQueue) {
		this.positionQueue = positionQueue;
	}

	public UnitPosition getValidPosition() {
		return validPosition;
	}

	public void setValidPosition(UnitPosition validPosition) {
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

	public Map<String, Boolean> getInstrumentPartialsParsed() {
		return instrumentPartialsParsed;
	}

	public void setInstrumentPartialsParsed(Map<String, Boolean> instrumentPartialsParsed) {
		this.instrumentPartialsParsed = instrumentPartialsParsed;
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
	
	
}
