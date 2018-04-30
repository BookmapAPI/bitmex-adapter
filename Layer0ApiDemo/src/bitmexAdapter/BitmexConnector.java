package bitmexAdapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
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
			client.start();
			URI echoUri = new URI(wssUrl);
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			client.connect(socket, echoUri, request);
			socket.getOpeningLatch().await();
			this.webSocketStartingLatch.countDown();
			this.socket = socket;
			socket.parser.setActiveInstrumentsMap(this.activeBmInstrumentsMap);

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
			Log.info("CONNECTOR: CLIENT CANNOT CONNECT");
		} catch (org.eclipse.jetty.websocket.api.WebSocketException e) {
			Log.info("CONNECTOR: CONNECTION MUST BE LOST");
		} catch (Throwable t) {
			Log.info("CONNECTOR: THROWABLE THROWN FROM WEBSOCKET");
			t.printStackTrace();
		} finally {
			try {
				client.stop();
				Log.info("CLIENT STOPPED");
			} catch (Exception e) {
				Log.info("CLIENT STOPPING TROUBLE");
				e.printStackTrace();
			}
		}
	}

	public void sendWebsocketMessage(String message) {
		try {
			getWebSocketStartingLatch().await();
		} catch (InterruptedException e) {
			e.printStackTrace();
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

			if (conn.getResponseCode() != 200) {
				Log.info("NO RESPONSE FROM SERVER   ***   RespCode = " + conn.getResponseCode());
			} else {
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
			Log.info("NO RESPONSE FROM SERVER");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	public void fillActiveBmInstrumentsMap() {
		String str = getServerResponse(restActiveInstrUrl);
		if (str == null) return;
		
		BmInstrument[] instrs = JsonParser.getArrayFromJson(str, BmInstrument[].class);

		for (BmInstrument instr : instrs) {
			this.activeBmInstrumentsMap.put(instr.getSymbol(), instr);
		}
	}

	public HashMap<String, BmInstrument> getActiveInstrumentsMap() {
		return activeBmInstrumentsMap;
	}

	public void subscribe(BmInstrument instr){
		instr.setSubscribed(true);
		sendWebsocketMessage(instr.getSubscribeReq());
		SnapshotTimer snTimer = new SnapshotTimer(instr, this);
		Thread th = new Thread(snTimer);
		th.start();
	}
	
	public void unSubscribe(BmInstrument instr){
		instr.setSubscribed(false);
		sendWebsocketMessage(instr.getUnSubscribeReq());
	}
	
	@Override
	public void run() {
		while (true) {
			if (!isConnectionEstablished()) {
				try {
					Thread.sleep(5000);
					Log.info("BITMEX CONNECTOR: SLEEP 5000");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}

			if (this.activeBmInstrumentsMap.isEmpty()) {
				fillActiveBmInstrumentsMap();
				if (this.activeBmInstrumentsMap.isEmpty()) continue;
			}
			wSconnect();
		}
	}

}
