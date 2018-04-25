package bitmexAdapter;

public class PingTimer implements Runnable {

	ClientSocket socket;

	public int counter = 0;
	boolean reconnectRequested = false;

	public PingTimer(ClientSocket socket) {
		super();
		this.socket = socket;
	}

	private void sleep(long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void requestReconnect() {
		socket.getClosingLatch().countDown();
		reconnectRequested = true;
	}

	public void check() {
		long timeToSleep = (System.currentTimeMillis() - socket.getLastMessageTime());
		
		if (timeToSleep < 5000) {
			sleep(5000 - timeToSleep);
			return;
		}
		
		socket.sendPing();
		sleep(5000);
		long intrv1 = System.currentTimeMillis() - socket.getLastMessageTime();
		
		if (intrv1 < 5050) {
			return;
		} else {
			System.out.println("REQUEST RECONNECT" );
			requestReconnect();
		}
	}

	@Override
	public void run() {
		while (!reconnectRequested) {
			check();
		}
	}

}
