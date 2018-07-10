package bitmexAdapter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketFrame;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.api.extensions.Frame.Type;

import velox.api.layer1.common.Log;

@WebSocket(maxTextMessageSize = 1048576, maxBinaryMessageSize = 1048576)
public class ClientSocket {

	private Session session;
	private CountDownLatch openingLatch = new CountDownLatch(1);
	private CountDownLatch closingLatch = new CountDownLatch(1);
	private JsonParser parser;
	private boolean isConnectionPossiblyLost = false;
	private long lastMessageTime = System.currentTimeMillis();
	ScheduledExecutorService snapshotTimer;

	@OnWebSocketClose
	public void OnClose(int i, String str) {
		// Log.info("ClientSocket: CLOSED WITH STATUS " + i + " FOR " + str + "
		// REASON");
		closingLatch.countDown();
	}

	@OnWebSocketMessage
	public void onText(Session session, String message) throws IOException {

		if (session != null && message != null) {
			parser.parse(message);
			lastMessageTime = System.currentTimeMillis();
		}
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		this.session = session;
		openingLatch.countDown();
		launchPingTimer();
	}

	private void launchPingTimer() {
		class CustomThreadFactory implements ThreadFactory {
			public Thread newThread(Runnable r) {
				return new Thread(r, "-> BitmexConnector: pingTimer");
			}
		}

		long maxDelay = 5000;
		ScheduledExecutorService snapshotTimer = Executors.newSingleThreadScheduledExecutor(new CustomThreadFactory());
		this.snapshotTimer = snapshotTimer;
		
		snapshotTimer.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				// if the last message was > [5s + time to launch this timer]
				// seconds ago
				if (System.currentTimeMillis() - getLastMessageTime() > maxDelay + 500) {
					Log.info("[BITMEX] ClientSocket launchPingTimer: last message UTC=" + getLastMessageTime());
					// and if this happened before
					if (isConnectionPossiblyLost()) {
						Log.info("[BITMEX] ClientSocket launchPingTimer: connection lost UTC=" + System.currentTimeMillis() );
						close();
						snapshotTimer.shutdown();
					} else {// but this did not happen before
						sendPing();
						setConnectionPossiblyLost(true);
						Log.info("[BITMEX] ClientSocket launchPingTimer: connection possibly lost UTC=" + System.currentTimeMillis() );
					}
				} else {
					// the last message was <5 seconds ago, everything is OK
					Log.info("[BITMEX] ClientSocket launchPingTimer: connection alive UTC=" + System.currentTimeMillis() );
					setConnectionPossiblyLost(false);
				}
			}
			// sleep maxDelay=5 seconds
		}, 0, maxDelay,  TimeUnit.MILLISECONDS);
	}

	public boolean isConnectionPossiblyLost() {
		return isConnectionPossiblyLost;
	}

	public void setConnectionPossiblyLost(boolean isConnectionPossiblyLost) {
		this.isConnectionPossiblyLost = isConnectionPossiblyLost;
	}

	public void sendMessage(String str) {
		Log.info("[BITMEX] ClientSocket sendMessage: " + str);
		try {
			session.getRemote().sendString(str);
		} catch (WebSocketException | IOException e) {
			e.printStackTrace();
		}
	}

	public CountDownLatch getOpeningLatch() {
		return openingLatch;
	}

	public long getLastMessageTime() {
		return lastMessageTime;
	}

	public CountDownLatch getClosingLatch() {
		return closingLatch;
	}

	@OnWebSocketError
	public void onError(Session session, Throwable error) throws Exception {
		close();
		Log.info("[BITMEX] ClientSockeT onError: " + error.toString());
		error.printStackTrace();
	}

	public void close() {
		snapshotTimer.shutdownNow();
		
		if (session != null) {
			try {
				session.disconnect();
			} catch (IOException e) {
				// Connection must be lost suddenly
				e.printStackTrace();
			}
		}
		
		Thread.currentThread().interrupt();
		Log.info("[BITMEX] ClientSockeT close(): socket interrupted");
	}

	public void setParser(JsonParser parser) {
		this.parser = parser;
	}

	public void sendPing() {
		try {
			RemoteEndpoint remote = session.getRemote();
			String data = "ping";
			ByteBuffer payload = ByteBuffer.wrap(data.getBytes());
			remote.sendPing(payload);
			Log.info("[BITMEX] ClientSocket sendPing: PING");
		} catch (WebSocketException e) {
			// e.printStackTrace(System.err);
			// Log.debug("RemoteEndpoint unavailable");
			// e.printStackTrace();
		} catch (IOException e) {
			// e.printStackTrace(System.err);
			e.printStackTrace();
		}
	}

	@OnWebSocketFrame
	public void onFrame(Frame frame) {
		if (frame.getType() == Type.PONG) {
			lastMessageTime = System.currentTimeMillis();
			Log.info("[BITMEX] ClientSocket onFrame: PONG");
		}
	}

}
