package bitmexAdapter;

public class DataUnit {
	private String symbol;
	private long id;
	private String side;
	private long size;
	private double price;
	private int intPrice;
	private boolean isBid;
	
	
	//this constructor is only used to create dataunits for the delete message
	public DataUnit(String symbol, int intPrice, boolean isBid) {
		super();
		this.symbol = symbol;
		this.intPrice = intPrice;
		this.isBid = isBid;
	}
	
	

	public DataUnit() {
		super();
	}



	public boolean isBid() {
		return isBid;
	}

	public void setBid(boolean isBid) {
		this.isBid = isBid;
	}

	public int getIntPrice() {
		return intPrice;
	}

	public String getSymbol() {
		return symbol;
	}

	public long getId() {
		return id;
	}

	public String getSide() {
		return side;
	}

	public long getSize() {
		return size;
	}

	public double getPrice() {
		return price;
	}

	public void setIntPrice(int intPrice) {
		this.intPrice = intPrice;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public void setId(long id) {
		this.id = id;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public void setPrice(double price) {
		this.price = price;
	}


	@Override
	public String toString() {
		return "DataUnit [intPrice=" + intPrice + ", symbol=" + symbol + ", id=" + id + ", side=" + side + ", size="
				+ size + ", price=" + price + "]";
	}

}
