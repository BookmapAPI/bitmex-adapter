package bitmexAdapter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
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
	public JsonParser parser = new JsonParser();
	private long lastMessageTime = System.currentTimeMillis();

	@OnWebSocketClose
	public void OnClose(int i, String str) {
		Log.info("ClientSocket: CLOSED WITH STATUS " + i + " FOR " + str + " REASON");
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
		Log.info("Connected to server");
		this.session = session;
		openingLatch.countDown();
		Log.info("Socket openingLatch count down");
		
		PingTimer timer = new PingTimer(this);
		Thread th = new Thread(timer);
		th.setName("BITMEX ADAPTER: PING TIMER");
		th.start();
	}

	public void sendMessage(String str) {
		try {
			session.getRemote().sendString(str);
		} catch (WebSocketException | IOException e) {
			e.printStackTrace();
		} catch (Exception d) {
			d.printStackTrace();
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
		Log.info("WEBSOCKET ERROR Session = " + session);
		close();
		error.printStackTrace();
	}

	public void close() throws Exception {
		if (session != null) {
			session.disconnect();
		}
		Log.info("WEBSOCKET HAS BEEN CLOSED");
		Thread.currentThread().interrupt();
	}

	public void sendPing() {
		try {
			RemoteEndpoint remote = session.getRemote();
			Log.info("CLIENT SOCKET: PING");
			String data = "ping";
			ByteBuffer payload = ByteBuffer.wrap(data.getBytes());
			remote.sendPing(payload);
		} catch (WebSocketException | IOException e) {
			// e.printStackTrace(System.err);
			Log.info("COnnection problems0");
			// e.printStackTrace();
		} catch (Exception e) {
			// e.printStackTrace();
			Log.info("COnnection problems1");

		}
	}

	@OnWebSocketFrame
	public void onFrame(Frame frame) {
		if (frame.getType() == Type.PONG) {
			Log.info("SERVER: PONG");
			lastMessageTime = System.currentTimeMillis();
		}
	}

}
