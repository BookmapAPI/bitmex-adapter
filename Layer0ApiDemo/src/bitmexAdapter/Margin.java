package bitmexAdapter;

public class Margin extends RawUnit{

	private Long account;
	private String currency;
	private Long riskLimit;
	private String prevState;
	private String state;
	private String action;
	private Long amount;
	private Long pendingCredit;
	private Long pendingDebit;
	private Long confirmedDebit;
	private Long prevRealisedPnl;
	private Long prevUnrealisedPnl;
	private Long grossComm;
	private Long grossOpenCost;
	private Long grossOpenPremium;
	private Long grossExecCost;
	private Long grossMarkValue;
	private Long riskValue;
	private Long taxableMargin;
	private Long initMargin;
	private Long maintMargin;
	private Long sessionMargin;
	private Long targetExcessMargin;
	private Long varMargin;
	private Long realisedPnl;
	private Long unrealisedPnl;
	private Long indicativeTax;
	private Long unrealisedProfit;
	private Long syntheticMargin;
	private Long walletBalance;
	private Long marginBalance;
	private double marginBalancePcnt;
	private double marginLeverage;
	private double marginUsedPcnt;
	private Long excessMargin;
	private double excessMarginPcnt;
	private Long availableMargin;
	private Long withdrawableMargin;
	private	String timestamp;
	private Long grossLastValue;
	private double commission;
	public Long getAccount() {
		return account;
	}
	public String getCurrency() {
		return currency;
	}
	public Long getRiskLimit() {
		return riskLimit;
	}
	public String getPrevState() {
		return prevState;
	}
	public String getState() {
		return state;
	}
	public String getAction() {
		return action;
	}
	public Long getAmount() {
		return amount;
	}
	public Long getPendingCredit() {
		return pendingCredit;
	}
	public Long getPendingDebit() {
		return pendingDebit;
	}
	public Long getConfirmedDebit() {
		return confirmedDebit;
	}
	public Long getPrevRealisedPnl() {
		return prevRealisedPnl;
	}
	public Long getPrevUnrealisedPnl() {
		return prevUnrealisedPnl;
	}
	public Long getGrossComm() {
		return grossComm;
	}
	public Long getGrossOpenCost() {
		return grossOpenCost;
	}
	public Long getGrossOpenPremium() {
		return grossOpenPremium;
	}
	public Long getGrossExecCost() {
		return grossExecCost;
	}
	public Long getGrossMarkValue() {
		return grossMarkValue;
	}
	public Long getRiskValue() {
		return riskValue;
	}
	public Long getTaxableMargin() {
		return taxableMargin;
	}
	public Long getInitMargin() {
		return initMargin;
	}
	public Long getMaintMargin() {
		return maintMargin;
	}
	public Long getSessionMargin() {
		return sessionMargin;
	}
	public Long getTargetExcessMargin() {
		return targetExcessMargin;
	}
	public Long getVarMargin() {
		return varMargin;
	}
	public Long getRealisedPnl() {
		return realisedPnl;
	}
	public Long getUnrealisedPnl() {
		return unrealisedPnl;
	}
	public Long getIndicativeTax() {
		return indicativeTax;
	}
	public Long getUnrealisedProfit() {
		return unrealisedProfit;
	}
	public Long getSyntheticMargin() {
		return syntheticMargin;
	}
	public Long getWalletBalance() {
		return walletBalance;
	}
	public Long getMarginBalance() {
		return marginBalance;
	}
	public double getMarginBalancePcnt() {
		return marginBalancePcnt;
	}
	public double getMarginLeverage() {
		return marginLeverage;
	}
	public double getMarginUsedPcnt() {
		return marginUsedPcnt;
	}
	public Long getExcessMargin() {
		return excessMargin;
	}
	public double getExcessMarginPcnt() {
		return excessMarginPcnt;
	}
	public Long getAvailableMargin() {
		return availableMargin;
	}
	public Long getWithdrawableMargin() {
		return withdrawableMargin;
	}
	public String getTimestamp() {
		return timestamp;
	}
	public Long getGrossLastValue() {
		return grossLastValue;
	}
	public double getCommission() {
		return commission;
	}
	public void setAccount(Long account) {
		this.account = account;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	public void setRiskLimit(Long riskLimit) {
		this.riskLimit = riskLimit;
	}
	public void setPrevState(String prevState) {
		this.prevState = prevState;
	}
	public void setState(String state) {
		this.state = state;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public void setAmount(Long amount) {
		this.amount = amount;
	}
	public void setPendingCredit(Long pendingCredit) {
		this.pendingCredit = pendingCredit;
	}
	public void setPendingDebit(Long pendingDebit) {
		this.pendingDebit = pendingDebit;
	}
	public void setConfirmedDebit(Long confirmedDebit) {
		this.confirmedDebit = confirmedDebit;
	}
	public void setPrevRealisedPnl(Long prevRealisedPnl) {
		this.prevRealisedPnl = prevRealisedPnl;
	}
	public void setPrevUnrealisedPnl(Long prevUnrealisedPnl) {
		this.prevUnrealisedPnl = prevUnrealisedPnl;
	}
	public void setGrossComm(Long grossComm) {
		this.grossComm = grossComm;
	}
	public void setGrossOpenCost(Long grossOpenCost) {
		this.grossOpenCost = grossOpenCost;
	}
	public void setGrossOpenPremium(Long grossOpenPremium) {
		this.grossOpenPremium = grossOpenPremium;
	}
	public void setGrossExecCost(Long grossExecCost) {
		this.grossExecCost = grossExecCost;
	}
	public void setGrossMarkValue(Long grossMarkValue) {
		this.grossMarkValue = grossMarkValue;
	}
	public void setRiskValue(Long riskValue) {
		this.riskValue = riskValue;
	}
	public void setTaxableMargin(Long taxableMargin) {
		this.taxableMargin = taxableMargin;
	}
	public void setInitMargin(Long initMargin) {
		this.initMargin = initMargin;
	}
	public void setMaintMargin(Long maintMargin) {
		this.maintMargin = maintMargin;
	}
	public void setSessionMargin(Long sessionMargin) {
		this.sessionMargin = sessionMargin;
	}
	public void setTargetExcessMargin(Long targetExcessMargin) {
		this.targetExcessMargin = targetExcessMargin;
	}
	public void setVarMargin(Long varMargin) {
		this.varMargin = varMargin;
	}
	public void setRealisedPnl(Long realisedPnl) {
		this.realisedPnl = realisedPnl;
	}
	public void setUnrealisedPnl(Long unrealisedPnl) {
		this.unrealisedPnl = unrealisedPnl;
	}
	public void setIndicativeTax(Long indicativeTax) {
		this.indicativeTax = indicativeTax;
	}
	public void setUnrealisedProfit(Long unrealisedProfit) {
		this.unrealisedProfit = unrealisedProfit;
	}
	public void setSyntheticMargin(Long syntheticMargin) {
		this.syntheticMargin = syntheticMargin;
	}
	public void setWalletBalance(Long walletBalance) {
		this.walletBalance = walletBalance;
	}
	public void setMarginBalance(Long marginBalance) {
		this.marginBalance = marginBalance;
	}
	public void setMarginBalancePcnt(double marginBalancePcnt) {
		this.marginBalancePcnt = marginBalancePcnt;
	}
	public void setMarginLeverage(double marginLeverage) {
		this.marginLeverage = marginLeverage;
	}
	public void setMarginUsedPcnt(double marginUsedPcnt) {
		this.marginUsedPcnt = marginUsedPcnt;
	}
	public void setExcessMargin(Long excessMargin) {
		this.excessMargin = excessMargin;
	}
	public void setExcessMarginPcnt(double excessMarginPcnt) {
		this.excessMarginPcnt = excessMarginPcnt;
	}
	public void setAvailableMargin(Long availableMargin) {
		this.availableMargin = availableMargin;
	}
	public void setWithdrawableMargin(Long withdrawableMargin) {
		this.withdrawableMargin = withdrawableMargin;
	}
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
	public void setGrossLastValue(Long grossLastValue) {
		this.grossLastValue = grossLastValue;
	}
	public void setCommission(double commission) {
		this.commission = commission;
	}

	
	
}
