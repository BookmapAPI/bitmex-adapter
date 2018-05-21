package bitmexAdapter;

public class Position {


	private long account;
	private String symbol;
	private String currency;
	private String underlying;
	private String quoteCurrency;
	private double commission;
	private double initMarginReq;
	private double maintMarginReq;
	private long riskLimit;
	private double leverage;
	private boolean	crossMargin;
	private double deleveragePercentile;
	private long rebalancedPnl;
	private long prevRealisedPnl;
	private long prevUnrealisedPnl;
	private double prevClosePrice;
	private String openingTimestamp;
	private long openingQty;
	private long openingCost;
	private long openingComm;
	private long openOrderBuyQty;
	private long openOrderBuyCost;
	private long openOrderBuyPremium;
	private long openOrderSellQty;
	private long openOrderSellCost;
	private long openOrderSellPremium;
	private long execBuyQty;
	private long execBuyCost;
	private long execSellQty;
	private long execSellCost;
	private long execQty;
	private long execCost;
	private long execComm;
	private String currentTimestamp;
	private long currentQty;
	private long currentCost;
	private long currentComm;
	private long realisedCost;
	private long unrealisedCost;
	private long grossOpenCost;
	private long grossOpenPremium;
	private long grossExecCost;
	private boolean	isOpen;
	private double markPrice;
	private long markValue;
	private long riskValue;
	private double homeNotional;
	private double foreignNotional;
	private String posState;
	private long posCost;
	private long posCost2;
	private long posCross;
	private long posInit;
	private long posComm;
	private long posLoss;
	private long posMargin;
	private long posMaint;
	private long posAllowance;
	private long taxableMargin;
	private long initMargin;
	private long maintMargin;
	private long sessionMargin;
	private long targetExcessMargin;
	private long varMargin;
	private long realisedGrossPnl;
	private long realisedTax;
	private long realisedPnl;
	private long unrealisedGrossPnl;
	private long longBankrupt;
	private long shortBankrupt;
	private long taxBase;
	private double indicativeTaxRate;
	private long indicativeTax;
	private long unrealisedTax;
	private long unrealisedPnl;
	private double unrealisedPnlPcnt;
	private double unrealisedRoePcnt;
	private double simpleQty;
	private double simpleCost;
	private double simpleValue;
	private double simplePnl;
	private double simplePnlPcnt;
	private double avgCostPrice;
	private double avgEntryPrice;
	private double breakEvenPrice;
	private double marginCallPrice;
	private double liquidationPrice;
	private double bankruptPrice;
	private String timestamp;
	private double lastPrice;
	private long lastValue;


	public long getAccount() {
		return account;
	}
	public String getSymbol() {
		return symbol;
	}
	public String getCurrency() {
		return currency;
	}
	public String getUnderlying() {
		return underlying;
	}
	public String getQuoteCurrency() {
		return quoteCurrency;
	}
	public double getCommission() {
		return commission;
	}
	public double getInitMarginReq() {
		return initMarginReq;
	}
	public double getMaintMarginReq() {
		return maintMarginReq;
	}
	public long getRiskLimit() {
		return riskLimit;
	}
	public double getLeverage() {
		return leverage;
	}
	public boolean isCrossMargin() {
		return crossMargin;
	}
	public double getDeleveragePercentile() {
		return deleveragePercentile;
	}
	public long getRebalancedPnl() {
		return rebalancedPnl;
	}
	public long getPrevRealisedPnl() {
		return prevRealisedPnl;
	}
	public long getPrevUnrealisedPnl() {
		return prevUnrealisedPnl;
	}
	public double getPrevClosePrice() {
		return prevClosePrice;
	}
	public String getOpeningTimestamp() {
		return openingTimestamp;
	}
	public long getOpeningQty() {
		return openingQty;
	}
	public long getOpeningCost() {
		return openingCost;
	}
	public long getOpeningComm() {
		return openingComm;
	}
	public long getOpenOrderBuyQty() {
		return openOrderBuyQty;
	}
	public long getOpenOrderBuyCost() {
		return openOrderBuyCost;
	}
	public long getOpenOrderBuyPremium() {
		return openOrderBuyPremium;
	}
	public long getOpenOrderSellQty() {
		return openOrderSellQty;
	}
	public long getOpenOrderSellCost() {
		return openOrderSellCost;
	}
	public long getOpenOrderSellPremium() {
		return openOrderSellPremium;
	}
	public long getExecBuyQty() {
		return execBuyQty;
	}
	public long getExecBuyCost() {
		return execBuyCost;
	}
	public long getExecSellQty() {
		return execSellQty;
	}
	public long getExecSellCost() {
		return execSellCost;
	}
	public long getExecQty() {
		return execQty;
	}
	public long getExecCost() {
		return execCost;
	}
	public long getExecComm() {
		return execComm;
	}
	public String getCurrentTimestamp() {
		return currentTimestamp;
	}
	public long getCurrentQty() {
		return currentQty;
	}
	public long getCurrentCost() {
		return currentCost;
	}
	public long getCurrentComm() {
		return currentComm;
	}
	public long getRealisedCost() {
		return realisedCost;
	}
	public long getUnrealisedCost() {
		return unrealisedCost;
	}
	public long getGrossOpenCost() {
		return grossOpenCost;
	}
	public long getGrossOpenPremium() {
		return grossOpenPremium;
	}
	public long getGrossExecCost() {
		return grossExecCost;
	}
	public boolean isOpen() {
		return isOpen;
	}
	public double getMarkPrice() {
		return markPrice;
	}
	public long getMarkValue() {
		return markValue;
	}
	public long getRiskValue() {
		return riskValue;
	}
	public double getHomeNotional() {
		return homeNotional;
	}
	public double getForeignNotional() {
		return foreignNotional;
	}
	public String getPosState() {
		return posState;
	}
	public long getPosCost() {
		return posCost;
	}
	public long getPosCost2() {
		return posCost2;
	}
	public long getPosCross() {
		return posCross;
	}
	public long getPosInit() {
		return posInit;
	}
	public long getPosComm() {
		return posComm;
	}
	public long getPosLoss() {
		return posLoss;
	}
	public long getPosMargin() {
		return posMargin;
	}
	public long getPosMaint() {
		return posMaint;
	}
	public long getPosAllowance() {
		return posAllowance;
	}
	public long getTaxableMargin() {
		return taxableMargin;
	}
	public long getInitMargin() {
		return initMargin;
	}
	public long getMaintMargin() {
		return maintMargin;
	}
	public long getSessionMargin() {
		return sessionMargin;
	}
	public long getTargetExcessMargin() {
		return targetExcessMargin;
	}
	public long getVarMargin() {
		return varMargin;
	}
	public long getRealisedGrossPnl() {
		return realisedGrossPnl;
	}
	public long getRealisedTax() {
		return realisedTax;
	}
	public long getRealisedPnl() {
		return realisedPnl;
	}
	public long getUnrealisedGrossPnl() {
		return unrealisedGrossPnl;
	}
	public long getLongBankrupt() {
		return longBankrupt;
	}
	public long getShortBankrupt() {
		return shortBankrupt;
	}
	public long getTaxBase() {
		return taxBase;
	}
	public double getIndicativeTaxRate() {
		return indicativeTaxRate;
	}
	public long getIndicativeTax() {
		return indicativeTax;
	}
	public long getUnrealisedTax() {
		return unrealisedTax;
	}
	public long getUnrealisedPnl() {
		return unrealisedPnl;
	}
	public double getUnrealisedPnlPcnt() {
		return unrealisedPnlPcnt;
	}
	public double getUnrealisedRoePcnt() {
		return unrealisedRoePcnt;
	}
	public double getSimpleQty() {
		return simpleQty;
	}
	public double getSimpleCost() {
		return simpleCost;
	}
	public double getSimpleValue() {
		return simpleValue;
	}
	public double getSimplePnl() {
		return simplePnl;
	}
	public double getSimplePnlPcnt() {
		return simplePnlPcnt;
	}
	public double getAvgCostPrice() {
		return avgCostPrice;
	}
	public double getAvgEntryPrice() {
		return avgEntryPrice;
	}
	public double getBreakEvenPrice() {
		return breakEvenPrice;
	}
	public double getMarginCallPrice() {
		return marginCallPrice;
	}
	public double getLiquidationPrice() {
		return liquidationPrice;
	}
	public double getBankruptPrice() {
		return bankruptPrice;
	}
	public String getTimestamp() {
		return timestamp;
	}
	public double getLastPrice() {
		return lastPrice;
	}
	public long getLastValue() {
		return lastValue;
	}
	public void setAccount(long account) {
		this.account = account;
	}
	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	public void setUnderlying(String underlying) {
		this.underlying = underlying;
	}
	public void setQuoteCurrency(String quoteCurrency) {
		this.quoteCurrency = quoteCurrency;
	}
	public void setCommission(double commission) {
		this.commission = commission;
	}
	public void setInitMarginReq(double initMarginReq) {
		this.initMarginReq = initMarginReq;
	}
	public void setMaintMarginReq(double maintMarginReq) {
		this.maintMarginReq = maintMarginReq;
	}
	public void setRiskLimit(long riskLimit) {
		this.riskLimit = riskLimit;
	}
	public void setLeverage(double leverage) {
		this.leverage = leverage;
	}
	public void setCrossMargin(boolean crossMargin) {
		this.crossMargin = crossMargin;
	}
	public void setDeleveragePercentile(double deleveragePercentile) {
		this.deleveragePercentile = deleveragePercentile;
	}
	public void setRebalancedPnl(long rebalancedPnl) {
		this.rebalancedPnl = rebalancedPnl;
	}
	public void setPrevRealisedPnl(long prevRealisedPnl) {
		this.prevRealisedPnl = prevRealisedPnl;
	}
	public void setPrevUnrealisedPnl(long prevUnrealisedPnl) {
		this.prevUnrealisedPnl = prevUnrealisedPnl;
	}
	public void setPrevClosePrice(double prevClosePrice) {
		this.prevClosePrice = prevClosePrice;
	}
	public void setOpeningTimestamp(String openingTimestamp) {
		this.openingTimestamp = openingTimestamp;
	}
	public void setOpeningQty(long openingQty) {
		this.openingQty = openingQty;
	}
	public void setOpeningCost(long openingCost) {
		this.openingCost = openingCost;
	}
	public void setOpeningComm(long openingComm) {
		this.openingComm = openingComm;
	}
	public void setOpenOrderBuyQty(long openOrderBuyQty) {
		this.openOrderBuyQty = openOrderBuyQty;
	}
	public void setOpenOrderBuyCost(long openOrderBuyCost) {
		this.openOrderBuyCost = openOrderBuyCost;
	}
	public void setOpenOrderBuyPremium(long openOrderBuyPremium) {
		this.openOrderBuyPremium = openOrderBuyPremium;
	}
	public void setOpenOrderSellQty(long openOrderSellQty) {
		this.openOrderSellQty = openOrderSellQty;
	}
	public void setOpenOrderSellCost(long openOrderSellCost) {
		this.openOrderSellCost = openOrderSellCost;
	}
	public void setOpenOrderSellPremium(long openOrderSellPremium) {
		this.openOrderSellPremium = openOrderSellPremium;
	}
	public void setExecBuyQty(long execBuyQty) {
		this.execBuyQty = execBuyQty;
	}
	public void setExecBuyCost(long execBuyCost) {
		this.execBuyCost = execBuyCost;
	}
	public void setExecSellQty(long execSellQty) {
		this.execSellQty = execSellQty;
	}
	public void setExecSellCost(long execSellCost) {
		this.execSellCost = execSellCost;
	}
	public void setExecQty(long execQty) {
		this.execQty = execQty;
	}
	public void setExecCost(long execCost) {
		this.execCost = execCost;
	}
	public void setExecComm(long execComm) {
		this.execComm = execComm;
	}
	public void setCurrentTimestamp(String currentTimestamp) {
		this.currentTimestamp = currentTimestamp;
	}
	public void setCurrentQty(long currentQty) {
		this.currentQty = currentQty;
	}
	public void setCurrentCost(long currentCost) {
		this.currentCost = currentCost;
	}
	public void setCurrentComm(long currentComm) {
		this.currentComm = currentComm;
	}
	public void setRealisedCost(long realisedCost) {
		this.realisedCost = realisedCost;
	}
	public void setUnrealisedCost(long unrealisedCost) {
		this.unrealisedCost = unrealisedCost;
	}
	public void setGrossOpenCost(long grossOpenCost) {
		this.grossOpenCost = grossOpenCost;
	}
	public void setGrossOpenPremium(long grossOpenPremium) {
		this.grossOpenPremium = grossOpenPremium;
	}
	public void setGrossExecCost(long grossExecCost) {
		this.grossExecCost = grossExecCost;
	}
	public void setOpen(boolean isOpen) {
		this.isOpen = isOpen;
	}
	public void setMarkPrice(double markPrice) {
		this.markPrice = markPrice;
	}
	public void setMarkValue(long markValue) {
		this.markValue = markValue;
	}
	public void setRiskValue(long riskValue) {
		this.riskValue = riskValue;
	}
	public void setHomeNotional(double homeNotional) {
		this.homeNotional = homeNotional;
	}
	public void setForeignNotional(double foreignNotional) {
		this.foreignNotional = foreignNotional;
	}
	public void setPosState(String posState) {
		this.posState = posState;
	}
	public void setPosCost(long posCost) {
		this.posCost = posCost;
	}
	public void setPosCost2(long posCost2) {
		this.posCost2 = posCost2;
	}
	public void setPosCross(long posCross) {
		this.posCross = posCross;
	}
	public void setPosInit(long posInit) {
		this.posInit = posInit;
	}
	public void setPosComm(long posComm) {
		this.posComm = posComm;
	}
	public void setPosLoss(long posLoss) {
		this.posLoss = posLoss;
	}
	public void setPosMargin(long posMargin) {
		this.posMargin = posMargin;
	}
	public void setPosMaint(long posMaint) {
		this.posMaint = posMaint;
	}
	public void setPosAllowance(long posAllowance) {
		this.posAllowance = posAllowance;
	}
	public void setTaxableMargin(long taxableMargin) {
		this.taxableMargin = taxableMargin;
	}
	public void setInitMargin(long initMargin) {
		this.initMargin = initMargin;
	}
	public void setMaintMargin(long maintMargin) {
		this.maintMargin = maintMargin;
	}
	public void setSessionMargin(long sessionMargin) {
		this.sessionMargin = sessionMargin;
	}
	public void setTargetExcessMargin(long targetExcessMargin) {
		this.targetExcessMargin = targetExcessMargin;
	}
	public void setVarMargin(long varMargin) {
		this.varMargin = varMargin;
	}
	public void setRealisedGrossPnl(long realisedGrossPnl) {
		this.realisedGrossPnl = realisedGrossPnl;
	}
	public void setRealisedTax(long realisedTax) {
		this.realisedTax = realisedTax;
	}
	public void setRealisedPnl(long realisedPnl) {
		this.realisedPnl = realisedPnl;
	}
	public void setUnrealisedGrossPnl(long unrealisedGrossPnl) {
		this.unrealisedGrossPnl = unrealisedGrossPnl;
	}
	public void setLongBankrupt(long longBankrupt) {
		this.longBankrupt = longBankrupt;
	}
	public void setShortBankrupt(long shortBankrupt) {
		this.shortBankrupt = shortBankrupt;
	}
	public void setTaxBase(long taxBase) {
		this.taxBase = taxBase;
	}
	public void setIndicativeTaxRate(double indicativeTaxRate) {
		this.indicativeTaxRate = indicativeTaxRate;
	}
	public void setIndicativeTax(long indicativeTax) {
		this.indicativeTax = indicativeTax;
	}
	public void setUnrealisedTax(long unrealisedTax) {
		this.unrealisedTax = unrealisedTax;
	}
	public void setUnrealisedPnl(long unrealisedPnl) {
		this.unrealisedPnl = unrealisedPnl;
	}
	public void setUnrealisedPnlPcnt(double unrealisedPnlPcnt) {
		this.unrealisedPnlPcnt = unrealisedPnlPcnt;
	}
	public void setUnrealisedRoePcnt(double unrealisedRoePcnt) {
		this.unrealisedRoePcnt = unrealisedRoePcnt;
	}
	public void setSimpleQty(double simpleQty) {
		this.simpleQty = simpleQty;
	}
	public void setSimpleCost(double simpleCost) {
		this.simpleCost = simpleCost;
	}
	public void setSimpleValue(double simpleValue) {
		this.simpleValue = simpleValue;
	}
	public void setSimplePnl(double simplePnl) {
		this.simplePnl = simplePnl;
	}
	public void setSimplePnlPcnt(double simplePnlPcnt) {
		this.simplePnlPcnt = simplePnlPcnt;
	}
	public void setAvgCostPrice(double avgCostPrice) {
		this.avgCostPrice = avgCostPrice;
	}
	public void setAvgEntryPrice(double avgEntryPrice) {
		this.avgEntryPrice = avgEntryPrice;
	}
	public void setBreakEvenPrice(double breakEvenPrice) {
		this.breakEvenPrice = breakEvenPrice;
	}
	public void setMarginCallPrice(double marginCallPrice) {
		this.marginCallPrice = marginCallPrice;
	}
	public void setLiquidationPrice(double liquidationPrice) {
		this.liquidationPrice = liquidationPrice;
	}
	public void setBankruptPrice(double bankruptPrice) {
		this.bankruptPrice = bankruptPrice;
	}
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
	public void setLastPrice(double lastPrice) {
		this.lastPrice = lastPrice;
	}
	public void setLastValue(long lastValue) {
		this.lastValue = lastValue;
	}

}
