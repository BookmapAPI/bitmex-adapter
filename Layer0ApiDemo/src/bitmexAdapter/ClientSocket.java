package bitmexAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketFrame;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.api.extensions.Frame.Type;

@WebSocket(maxTextMessageSize = 1048576, maxBinaryMessageSize = 1048576)
//public class ClientSocket implements Runnable, AutoCloseable {
	public class ClientSocket {

	private Session session;
	private CountDownLatch openingLatch = new CountDownLatch(1);
	private CountDownLatch closingLatch = new CountDownLatch(1);
	public JsonParser parser = new JsonParser();
	private long lastMessageTime = System.currentTimeMillis();

	@OnWebSocketClose
	public void OnClose(int i, String str) {
		System.out.println("CLOSED WITH STATUS " + i + " FOR " + str + " REASON");
		// closingLatch.countDown();
	}

	@OnWebSocketMessage
	public void onText(Session session, String message) throws IOException {
		lastMessageTime = System.currentTimeMillis();
//		System.out.println(message);
		parser.parse(message);
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		System.out.println("Connected to server");
		this.session = session;
		openingLatch.countDown();
		System.out.println("Socket openingLatch count down");
	}

	public void sendMessage(String str) {
		try {
			session.getRemote().sendString(str);
		} catch (IOException e) {
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
		System.out.println(session);
		System.out.println("ERROR");
		error.printStackTrace();
	}


	public void close() throws Exception {
		session.disconnect();
		System.out.println("Closing!");
		Thread.currentThread().interrupt();
	}

	public void sendPing() {
		RemoteEndpoint remote = session.getRemote();
		System.out.println("PING");
		String data = "ping";
		ByteBuffer payload = ByteBuffer.wrap(data.getBytes());
		
		try {
			remote.sendPing(payload);
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

	@OnWebSocketFrame
	public void onFrame(Frame frame) {
		if (frame.getType() == Type.PONG) {
			// do your processing of PONG
			System.out.println("PONG");
			lastMessageTime = System.currentTimeMillis();
		}
	}

	// public boolean awaitClose(int duration, TimeUnit unit) throws
	// InterruptedException {
	// return this.latch.await(duration, unit);
	//
	// }

	// public void sendPing() {
	// RemoteEndpoint remote = session.getRemote();
	//
	// // Blocking Send of a PING to remote endpoint
	// String data = "You There?";
	// ByteBuffer payload = ByteBuffer.wrap(data.getBytes());
	// try {
	// remote.sendPing(payload);
	// } catch (IOException e) {
	// e.printStackTrace(System.err);
	// }
	// }
}
