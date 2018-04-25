package bitmexAdapter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import velox.api.layer1.layers.utils.OrderBook;

public class BmInstrument {

	private String symbol;
	private String rootSymbol;
	private double tickSize;
	private long symbolIdx;

	private Object bookLock = new Object();
	private CustomOrderBook orderBook = new CustomOrderBook();
	private BlockingQueue<Msg> queue = new LinkedBlockingQueue<>();
	

	private boolean isSubscribed = false;
	public boolean isBookRead = false;
	public boolean isBookFilled = false;
	// private boolean isPartialParsed = false;

	public BmInstrument(String symbol, String rootSymbol, double tickSize) {
		super();
		this.symbol = symbol;
		this.rootSymbol = rootSymbol;
		this.tickSize = tickSize;
	}

	public BmInstrument() {
		super();
		// TODO Auto-generated constructor stub
	}

	public BlockingQueue<Msg> getQueue() {
		return queue;
	}

	public void setQueue(BlockingQueue<Msg> queue) {
		this.queue = queue;
	}
	
	public String getSymbol() {
		return symbol;
	}

	public String getRootSymbol() {
		return rootSymbol;
	}

	public double getTickSize() {
		return tickSize;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public void setRootSymbol(String rootSymbol) {
		this.rootSymbol = rootSymbol;
	}

	public void setTickSize(double tickSize) {
		this.tickSize = tickSize;
	}

	public CustomOrderBook getOrderBook() {
		synchronized (bookLock) {
			return orderBook;
		}
	}

	// public BlockingQueue<Msg> getQueue() {
	// return queue;
	// }

	public boolean isBookRead() {
		return isBookRead;
	}

	public void setBookRead(boolean isBookRead) {
		this.isBookRead = isBookRead;
	}

	public long getSymbolIdx() {
		return symbolIdx;
	}

	public void setSymbolIdx(long symbolIdx) {
		this.symbolIdx = symbolIdx;
	}

	public boolean isSubscribed() {
		return isSubscribed;
	}

	public void setSubscribed(boolean isSubscribed) {
		this.isSubscribed = isSubscribed;
	}

	
}
