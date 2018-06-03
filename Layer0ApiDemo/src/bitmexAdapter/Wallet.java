package bitmexAdapter;

public class Wallet extends DataUnit{

	private Long account;
	
	private String currency;
	
	private Long prevDeposited;
	private Long prevWithdrawn;
	private Long prevTransferIn;
	private Long prevTransferOut;
	private Long prevAmount;
	private String  prevTimestamp; 
	private Long deltaDeposited;
	private Long deltaWithdrawn;
	private Long deltaTransferIn;
	private Long deltaTransferOut;
	private Long deltaAmount;
	private Long deposited;
	private Long withdrawn;
	private Long transferIn;
	private Long transferOut;
	private Long amount;
	private Long pendingCredit;
	private Long pendingDebit;
	private Long confirmedDebit;
	private String timestamp;
	private String addr;
	private String script;
//	private String withdrawalLock; 

	
	public Long getPrevDeposited() {
		return prevDeposited;
	}
	public Long getPrevWithdrawn() {
		return prevWithdrawn;
	}
	public Long getPrevTransferIn() {
		return prevTransferIn;
	}
	public Long getPrevTransferOut() {
		return prevTransferOut;
	}
	public Long getPrevAmount() {
		return prevAmount;
	}
	public String getPrevTimestamp() {
		return prevTimestamp;
	}
	public Long getDeltaDeposited() {
		return deltaDeposited;
	}
	public Long getDeltaWithdrawn() {
		return deltaWithdrawn;
	}
	public Long getDeltaTransferIn() {
		return deltaTransferIn;
	}
	public Long getDeltaTransferOut() {
		return deltaTransferOut;
	}
	public Long getDeltaAmount() {
		return deltaAmount;
	}
	public Long getDeposited() {
		return deposited;
	}
	public Long getWithdrawn() {
		return withdrawn;
	}
	public Long getTransferIn() {
		return transferIn;
	}
	public Long getTransferOut() {
		return transferOut;
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
	public String getTimestamp() {
		return timestamp;
	}
	public String getAddr() {
		return addr;
	}
	public String getScript() {
		return script;
	}
//	public String getWithdrawalLock() {
//		return withdrawalLock;
//	}
	public void setPrevDeposited(Long prevDeposited) {
		this.prevDeposited = prevDeposited;
	}
	public void setPrevWithdrawn(Long prevWithdrawn) {
		this.prevWithdrawn = prevWithdrawn;
	}
	public void setPrevTransferIn(Long prevTransferIn) {
		this.prevTransferIn = prevTransferIn;
	}
	public void setPrevTransferOut(Long prevTransferOut) {
		this.prevTransferOut = prevTransferOut;
	}
	public void setPrevAmount(Long prevAmount) {
		this.prevAmount = prevAmount;
	}
	public void setPrevTimestamp(String prevTimestamp) {
		this.prevTimestamp = prevTimestamp;
	}
	public void setDeltaDeposited(Long deltaDeposited) {
		this.deltaDeposited = deltaDeposited;
	}
	public void setDeltaWithdrawn(Long deltaWithdrawn) {
		this.deltaWithdrawn = deltaWithdrawn;
	}
	public void setDeltaTransferIn(Long deltaTransferIn) {
		this.deltaTransferIn = deltaTransferIn;
	}
	public void setDeltaTransferOut(Long deltaTransferOut) {
		this.deltaTransferOut = deltaTransferOut;
	}
	public void setDeltaAmount(Long deltaAmount) {
		this.deltaAmount = deltaAmount;
	}
	public void setDeposited(Long deposited) {
		this.deposited = deposited;
	}
	public void setWithdrawn(Long withdrawn) {
		this.withdrawn = withdrawn;
	}
	public void setTransferIn(Long transferIn) {
		this.transferIn = transferIn;
	}
	public void setTransferOut(Long transferOut) {
		this.transferOut = transferOut;
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
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
	public void setAddr(String addr) {
		this.addr = addr;
	}
	public void setScript(String script) {
		this.script = script;
	}
	
	public Long getAccount() {
		return account;
	}
	public String getCurrency() {
		return currency;
	}
	public void setAccount(Long account) {
		this.account = account;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	
//	public void setWithdrawalLock(String withdrawalLock) {
//		this.withdrawalLock = withdrawalLock;
//	}
	@Override
	public String toString() {
		return "Wallet [prevAmount=" + prevAmount + ", deposited=" + deposited + ", amount=" + amount + "]";
	}
	

}
