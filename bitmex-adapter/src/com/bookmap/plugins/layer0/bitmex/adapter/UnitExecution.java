package com.bookmap.plugins.layer0.bitmex.adapter;

public class UnitExecution extends UnitOrder{
	private String execType;
	private double lastPx;//from UnitExecution
	private long lastQty;
	
	//this field is set as the result processing timestamp acquired from Bitmex
	private long execTransactTime;
	
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
	
	public long getLastQty() {
		return lastQty;
	}
	public void setLastQty(long lastQty) {
		this.lastQty = lastQty;
	}
	
	public long getExecTransactTime() {
		return execTransactTime;
	}
	public void setExecTransactTime(long execTransactTime) {
		this.execTransactTime = execTransactTime;
	}
	@Override
	public String toString() {
		return "UnitExecution [execType=" + execType + ", ordStatus=" + getOrdStatus() + ", transactTime=" + getTransactTime() + ", lastPx=" + lastPx + ", lastQty=" + lastQty
				+ ", execTransactTime=" + execTransactTime + ", getOrderID()=" + getOrderID() + ", getSymbol()="
				+ getSymbol() + ", getSide()=" + getSide() + ", getOrdType()=" + getOrdType() + ", timeInForce"
				+ getTimeInForce() + ", execInst" + getExecInst() + "]";
	}
	
	
}
