package bitmexAdapter;

public class UnitOrder extends UnitRaw{
	
	private String execID;
	private String orderID;
	private String clientId;//for Bookmap
	
	private String clOrdID;
	private String clOrdLinkID;
	private long account;
	private String symbol;
	private String side;
	
	
	
	private double simpleOrderQty; // or int?
	private long orderQty;// ot int?
	private double price = Double.NaN;
	
	private long displayQty;
	private double stopPx = Double.NaN;
	private double pegOffsetValue = Double.NaN;
	
	private String pegPriceType;
	private String currency;
	private String settlCurrency;
	private String ordType;
	private String timeInForce;
	private String execInst;
	private String contingencyType;
	private String exDestination;
	private String ordStatus;
	private String triggered;
	
	private boolean workingIndicator;
	private String ordRejReason;
	private double simpleLeavesQty;
	private long leavesQty;
	private double simpleCumQty;
	private long cumQty;
	private double avgPx;
	
	private String multiLegReportingType;
	private String text;
	private String transactTime;
	private String timestamp;
	private boolean isSnapshot;//is set manually
	
	public boolean isSnapshot() {
		return isSnapshot;
	}
	public void setSnapshot(boolean isSnapshot) {
		this.isSnapshot = isSnapshot;
	}
	public String getOrderID() {
		return orderID;
	}
	public String getClOrdID() {
		return clOrdID;
	}
	public String getClOrdLinkID() {
		return clOrdLinkID;
	}
	public long getAccount() {
		return account;
	}
	public String getSymbol() {
		return symbol;
	}
	public String getSide() {
		return side;
	}
	public double getSimpleOrderQty() {
		return simpleOrderQty;
	}
	public long getOrderQty() {
		return orderQty;
	}
	public double getPrice() {
		return price;
	}
	public long getDisplayQty() {
		return displayQty;
	}
	public double getStopPx() {
		return stopPx;
	}
	public double getPegOffsetValue() {
		return pegOffsetValue;
	}
	public String getPegPriceType() {
		return pegPriceType;
	}
	public String getCurrency() {
		return currency;
	}
	public String getSettlCurrency() {
		return settlCurrency;
	}
	public String getOrdType() {
		return ordType;
	}
	public String getTimeInForce() {
		return timeInForce;
	}
	public String getExecInst() {
		return execInst;
	}
	public String getContingencyType() {
		return contingencyType;
	}
	public String getExDestination() {
		return exDestination;
	}
	public String getOrdStatus() {
		return ordStatus;
	}
	public String getTriggered() {
		return triggered;
	}
	public boolean isWorkingIndicator() {
		return workingIndicator;
	}
	public String getOrdRejReason() {
		return ordRejReason;
	}
	public double getSimpleLeavesQty() {
		return simpleLeavesQty;
	}
	public long getLeavesQty() {
		return leavesQty;
	}
	public double getSimpleCumQty() {
		return simpleCumQty;
	}
	public long getCumQty() {
		return cumQty;
	}
	public double getAvgPx() {
		return avgPx;
	}
	public String getMultiLegReportingType() {
		return multiLegReportingType;
	}
	public String getText() {
		return text;
	}
	public String getTransactTime() {
		return transactTime;
	}
	public String getTimestamp() {
		return timestamp;
	}
	public void setOrderID(String orderID) {
		this.orderID = orderID;
	}
	public void setClOrdID(String clOrdID) {
		this.clOrdID = clOrdID;
	}
	public void setClOrdLinkID(String clOrdLinkID) {
		this.clOrdLinkID = clOrdLinkID;
	}
	public void setAccount(long account) {
		this.account = account;
	}
	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}
	public void setSide(String side) {
		this.side = side;
	}
	public void setSimpleOrderQty(double simpleOrderQty) {
		this.simpleOrderQty = simpleOrderQty;
	}
	public void setOrderQty(long orderQty) {
		this.orderQty = orderQty;
	}
	public void setPrice(double price) {
		this.price = price;
	}
	public void setDisplayQty(long displayQty) {
		this.displayQty = displayQty;
	}
	public void setStopPx(double stopPx) {
		this.stopPx = stopPx;
	}
	public void setPegOffsetValue(double pegOffsetValue) {
		this.pegOffsetValue = pegOffsetValue;
	}
	public void setPegPriceType(String pegPriceType) {
		this.pegPriceType = pegPriceType;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	public void setSettlCurrency(String settlCurrency) {
		this.settlCurrency = settlCurrency;
	}
	public void setOrdType(String ordType) {
		this.ordType = ordType;
	}
	public void setTimeInForce(String timeInForce) {
		this.timeInForce = timeInForce;
	}
	public void setExecInst(String execInst) {
		this.execInst = execInst;
	}
	public void setContingencyType(String contingencyType) {
		this.contingencyType = contingencyType;
	}
	public void setExDestination(String exDestination) {
		this.exDestination = exDestination;
	}
	public void setOrdStatus(String ordStatus) {
		this.ordStatus = ordStatus;
	}
	public void setTriggered(String triggered) {
		this.triggered = triggered;
	}
	public void setWorkingIndicator(boolean workingIndicator) {
		this.workingIndicator = workingIndicator;
	}
	public void setOrdRejReason(String ordRejReason) {
		this.ordRejReason = ordRejReason;
	}
	public void setSimpleLeavesQty(double simpleLeavesQty) {
		this.simpleLeavesQty = simpleLeavesQty;
	}
	public void setLeavesQty(long leavesQty) {
		this.leavesQty = leavesQty;
	}
	public void setSimpleCumQty(double simpleCumQty) {
		this.simpleCumQty = simpleCumQty;
	}
	public void setCumQty(long cumQty) {
		this.cumQty = cumQty;
	}
	public void setAvgPx(double avgPx) {
		this.avgPx = avgPx;
	}
	public void setMultiLegReportingType(String multiLegReportingType) {
		this.multiLegReportingType = multiLegReportingType;
	}
	public void setText(String text) {
		this.text = text;
	}
	public void setTransactTime(String transactTime) {
		this.transactTime = transactTime;
	}
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
	public String getExecID() {
		return execID;
	}
	public void setExecID(String execID) {
		this.execID = execID;
	}


	public String getClientId() {
		return clientId;
	}
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	
	
}
