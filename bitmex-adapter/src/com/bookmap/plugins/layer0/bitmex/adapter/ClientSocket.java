package com.bookmap.plugins.layer0.bitmex.adapter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

@WebSocket(maxTextMessageSize = Integer.MAX_VALUE, maxBinaryMessageSize = Integer.MAX_VALUE)
public class ClientSocket {

	private Session session;
	private CountDownLatch openingLatch = new CountDownLatch(1);
	private CountDownLatch closingLatch = new CountDownLatch(1);
	private JsonParser parser;
	private AtomicBoolean isConnectionPossiblyLost = new AtomicBoolean(false);
	private AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());
	long maxDelay = 5_000;
	ScheduledExecutorService pingTimer;

	@OnWebSocketClose
	public void OnClose(int i, String str) {
		Log.info("ClientSocket: CLOSED WITH STATUS " + i + " FOR " + str + " REASON");
		closingLatch.countDown();
	}

	@OnWebSocketMessage
	public void onText(Session session, String message) throws IOException {
		isConnectionPossiblyLost.set(false);
		lastMessageTime.set(System.currentTimeMillis());

		if (session != null && message != null) {
			parser.parse(message);
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
				return new Thread(r, "-> BmConnector: pingTimer");
			}
		}


		ScheduledExecutorService pingTimer = Executors.newSingleThreadScheduledExecutor(new CustomThreadFactory());
		this.pingTimer = pingTimer;

		pingTimer.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				// if the last message was > [5s + time to launch this timer]
				// seconds ago
				long l = lastMessageTime.get();
				
				if (System.currentTimeMillis() - l > maxDelay + 500) {
					Log.info("[bitmex] ClientSocket launchPingTimer: last message UTC=" + Instant.ofEpochMilli(l));
					// and if this happened before
					if (isConnectionPossiblyLost.get()) {
						Log.info("[bitmex] ClientSocket launchPingTimer: connection lost UTC="
								+ Instant.ofEpochMilli(System.currentTimeMillis()));
						Log.info("[bitmex] ClientSocket launchPingTimer: pingTimer closes connection");
						close();
					} else {// but this did not happen before
						isConnectionPossiblyLost.set(true);
						sendPing();
						Log.info("[bitmex] ClientSocket launchPingTimer: connection possibly lost UTC="
								+ Instant.ofEpochMilli(System.currentTimeMillis()));
					}
				} else {
					// the last message was <5 seconds ago, everything is OK
					// Log.info("[bitmex] ClientSocket launchPingTimer:
					// connection alive UTC=" + System.currentTimeMillis() );
					isConnectionPossiblyLost.set(false);
				}
			}
			// sleep maxDelay=5 seconds
		}, 0, maxDelay, TimeUnit.MILLISECONDS);
	}

	public void sendMessage(String str) {
		Log.info("[bitmex] ClientSocket sendMessage: " + str);
		try {
			if (session != null) {
				session.getRemote().sendString(str);
			} else {
				Log.info("[bitmex] ClientSocket sendMessage: session is null");
			}
		} catch (WebSocketException | IOException e) {
			e.printStackTrace();
		}
	}

	public CountDownLatch getOpeningLatch() {
		return openingLatch;
	}

	public CountDownLatch getClosingLatch() {
		return closingLatch;
	}

	@OnWebSocketError
	public void onError(Session session, Throwable error) throws Exception {
		Log.info("[bitmex] ClientSockeT onError: " + error.toString());
		error.printStackTrace();
		close();
	}

	public void close() {
		if (pingTimer != null) {
			pingTimer.shutdownNow();
		}

		if (session != null) {
			try {
				session.disconnect();
			} catch (IOException e) {
				// Connection may be lost suddenly
				e.printStackTrace();
			}
		}
		Log.info("[bitmex] ClientSockeT close(): socket interrupted");
		closingLatch.countDown();
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
			// Log.info("[bitmex] ClientSocket sendPing: PING");
		} catch (WebSocketException e) {
			// e.printStackTrace(System.err);
			 Log.info("RemoteEndpoint unavailable");
			// e.printStackTrace();
		} catch (IOException e) {
			// e.printStackTrace(System.err);
			e.printStackTrace();
		}
	}

	@OnWebSocketFrame
	public void onFrame(Frame frame) {
		if (frame.getType() == Type.PONG) {
			isConnectionPossiblyLost.set(false);
			lastMessageTime.set(System.currentTimeMillis());
			Log.info("[bitmex] ClientSocket onFrame: PONG");
		}
	}

}
