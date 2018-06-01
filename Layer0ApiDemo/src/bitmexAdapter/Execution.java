package bitmexAdapter;

public class Execution extends BmOrder{
	private String execType;
	private double lastPx;//from Execution
	private long foreignNotional;
	
	public long getForeignNotional() {
		return foreignNotional;
	}
	public void setForeignNotional(long foreignNotional) {
		this.foreignNotional = foreignNotional;
	}
	public double getLastPx() {
		return lastPx;
	}
	public void setLastPx(double lastPx) {
		this.lastPx = lastPx;
	}
	
	public String getExecType() {
		return execType;
	}
	public void setExecType(String execType) {
		this.execType = execType;
	}
}
