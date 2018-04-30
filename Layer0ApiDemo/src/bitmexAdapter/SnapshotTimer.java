package bitmexAdapter;

import velox.api.layer1.common.Log;

public class SnapshotTimer implements Runnable {
	private final BmInstrument instr;
	private final BitmexConnector connector;

	public SnapshotTimer(BmInstrument instr, BitmexConnector connector) {
		super();
		this.instr = instr;
		this.connector = connector;
		Thread.currentThread().setName("BITMEX ADAPTER: SNAPSHOT TIMER");
	}

	private void requestReSubscribe() {
		Log.info("SNAPSHOT TIMER RESUBSCRIBE REQUESTED");
		connector.unSubscribe(instr);
		connector.subscribe(instr);
	}

	public void check() {
		try {
			Thread.sleep(8000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Log.info("SNAPSHOT TIMER WOKE UP");

		if (!instr.isFirstSnapshotParsed()) {
			requestReSubscribe();
		}
		Log.info("SNAPSHOT TIMER SAYS GOODBYE");
	}

	@Override
	public void run() {
		Log.info("SNAPSHOT TIMER STARTED");
		check();
		
	}

}
