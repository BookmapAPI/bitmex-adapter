package bitmexAdapter;

public class DataUnit {

	// public String symbol;
	// public long id;
	// public String side;
	// public int size;
	// public double price;
	private int intPrice;

	// Fields below refer to "orderBookL2" table dataUnits only
	private String symbol;
	private long id;
	private String side;
	private long size;
	private double price;

	// ...these and above (except id) refer to "trade" table dataUnits
	private String timestamp;
	private String tickDirection;
	private String trdMatchID;
	private long grossValue;
	private double homeNotional;
	private double foreignNotional;

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

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public void setTickDirection(String tickDirection) {
		this.tickDirection = tickDirection;
	}

	public void setTrdMatchID(String trdMatchID) {
		this.trdMatchID = trdMatchID;
	}

	public void setGrossValue(long grossValue) {
		this.grossValue = grossValue;
	}

	public void setHomeNotional(double homeNotional) {
		this.homeNotional = homeNotional;
	}

	public void setForeignNotional(double foreignNotional) {
		this.foreignNotional = foreignNotional;
	}

	@Override
	public String toString() {
		return "DataUnit [intPrice=" + intPrice + ", symbol=" + symbol + ", id=" + id + ", side=" + side + ", size="
				+ size + ", price=" + price + ", timestamp=" + timestamp + "]";
	}

}
