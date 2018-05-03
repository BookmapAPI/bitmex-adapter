package bitmexAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.HttpsURLConnection;
import velox.api.layer1.common.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

public class BitmexConnector implements Runnable {
	private final String wssUrl = "wss://www.bitmex.com/realtime";
	private final String restApi = "https://www.bitmex.com/api/v1";
	private final String restActiveInstrUrl = "https://www.bitmex.com/api/v1/instrument/active";
	private HashMap<String, BmInstrument> activeBmInstrumentsMap = new HashMap<>();
	private CountDownLatch webSocketStartingLatch = new CountDownLatch(1);
	private ClientSocket socket;
	public JsonParser parser = new JsonParser();

	public CountDownLatch getWebSocketStartingLatch() {
		return webSocketStartingLatch;
	}

	private boolean isConnectionEstablished() {
		if (getServerResponse(restApi) == null) {
			return false;
		}
		return true;
	}

	public void wSconnect() {
		SslContextFactory ssl = new SslContextFactory();
		WebSocketClient client = new WebSocketClient(ssl);

		try {
			ClientSocket socket = new ClientSocket();
			this.socket = socket;
			this.parser.setActiveInstrumentsMap(Collections.unmodifiableMap(activeBmInstrumentsMap));
			this.socket.setParser(parser);

			client.start();
			URI echoUri = new URI(wssUrl);
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			client.connect(socket, echoUri, request);
			this.socket.getOpeningLatch().await();
			this.webSocketStartingLatch.countDown();

			for (BmInstrument instr : activeBmInstrumentsMap.values()) {
				if (instr.isSubscribed()) {
					subscribe(instr);
				}
			}

			// WAITING FOR THE SOCKET TO CLOSE
			socket.getClosingLatch().await();
			for (BmInstrument instr : activeBmInstrumentsMap.values()) {
				instr.setFirstSnapshotParsed(false);
			}

			this.socket = null;

		} catch (java.nio.channels.UnresolvedAddressException e) {
			e.printStackTrace();
			// Log.info("CONNECTOR: CLIENT CANNOT CONNECT");
		} catch (org.eclipse.jetty.websocket.api.WebSocketException e) {
			e.printStackTrace();
			// Log.info("CONNECTOR: CONNECTION MUST BE LOST");
		} catch (Exception | Error e) {
//			Log.debug("CONNECTOR: THROWABLE THROWN FROM WEBSOCKET");
			throw new RuntimeException();
		} finally {
			try {
				client.stop();
			} catch (Exception e) {
//				Log.debug("CLIENT STOPPING TROUBLE");
				e.printStackTrace();
				throw new RuntimeException();
			}
		}
	}

	public void sendWebsocketMessage(String message) {
		try {
			getWebSocketStartingLatch().await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
		socket.sendMessage(message);
	}

	private String getServerResponse(String address) {
		String response = null;

		try {
			URL url = new URL(address);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			if (conn.getResponseCode() == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
				StringBuilder sb = new StringBuilder("");
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				conn.disconnect();
				response = sb.toString();
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
//			Log.info("NO RESPONSE FROM SERVER");
		} catch (java.net.SocketException e) {
//			Log.info("NETWORK IS UNREACHABLE");
		} catch (IOException e){
//			Log.debug("BUFFER READING ERROR");
			e.printStackTrace();
		}
		return response;
	}

	public void fillActiveBmInstrumentsMap() {
		synchronized (activeBmInstrumentsMap) {
			String str = getServerResponse(restActiveInstrUrl);
			if (str == null)
				return;

			BmInstrument[] instrs = JsonParser.getArrayFromJson(str, BmInstrument[].class);

			for (BmInstrument instr : instrs) {
				this.activeBmInstrumentsMap.put(instr.getSymbol(), instr);
			}
			activeBmInstrumentsMap.notify();
		}
	}

	public HashMap<String, BmInstrument> getActiveInstrumentsMap() {
		return activeBmInstrumentsMap;
	}

	private void launchSnapshotTimer(BitmexConnector connector, BmInstrument instr) {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if (!instr.isFirstSnapshotParsed()) {
					connector.unSubscribe(instr);
					connector.subscribe(instr);
				}
			}
		};

		Timer timer = new Timer();
		timer.schedule(task, 8000);
	}

	public void subscribe(BmInstrument instr) {
		instr.setSubscribed(true);
		sendWebsocketMessage(instr.getSubscribeReq());
		launchSnapshotTimer(this, instr);
	}

	public void unSubscribe(BmInstrument instr) {
		instr.setSubscribed(false);
		sendWebsocketMessage(instr.getUnSubscribeReq());
	}

	@Override
	public void run() {
		while (true) {
			if (!isConnectionEstablished()) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
				continue;
			}

			if (this.activeBmInstrumentsMap.isEmpty()) {
				fillActiveBmInstrumentsMap();
				if (this.activeBmInstrumentsMap.isEmpty())
					continue;
			}
			wSconnect();
		}
	}

}
