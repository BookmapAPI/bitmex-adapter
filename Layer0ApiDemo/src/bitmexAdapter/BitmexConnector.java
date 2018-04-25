package bitmexAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import javax.net.ssl.HttpsURLConnection;
import velox.api.layer1.layers.utils.OrderBook;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

public class BitmexConnector implements Runnable {

	private final String wssUrl = "wss://www.bitmex.com/realtime";
	private final String restActiveInstrUrl = "https://www.bitmex.com/api/v1/instrument/active";
	private final String instrumentsIdxList= "https://www.bitmex.com/api/v1/instrument?columns=symbol,tickSize&start=0&count=500";

	private HashMap<String, BmInstrument> activeBmInstrumentsMap = new HashMap<>();
	private HashMap<String, CustomOrderBook> orderBooks = new HashMap<>();
	private CountDownLatch webSocketStartingLatch = new CountDownLatch(1);

	public ClientSocket socket;

	public CountDownLatch getWebSocketStartingLatch() {
		return webSocketStartingLatch;
	}

	private void startPingTimer(ClientSocket socket) {
		PingTimer timer = new PingTimer(socket);
		Thread th = new Thread(timer);
		th.setName("**********TIMER*********");;
		th.start();
	}

	public void connect() {
		SslContextFactory ssl = new SslContextFactory();
		WebSocketClient client = new WebSocketClient(ssl);

		try {
			ClientSocket socket = new ClientSocket();
			client.start();
			URI echoUri = new URI(wssUrl);
			ClientUpgradeRequest request = new ClientUpgradeRequest();
//			try {
				client.connect(socket, echoUri, request);
//
//				System.out.println("BMConnector socket.getOpeningLatch().await()");
				socket.getOpeningLatch().await();
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			System.out.println("BMConnector socket.getOpeningLatch().await() PASSED");
			// socket.sendMessage("{\"op\": \"help\"}");

			System.out.println("BMConnector webSocketStartingLatch.countDown()");
			webSocketStartingLatch.countDown();
			System.out.println("BMConnector webSocketStartingLatch.countDown() PASSED");

			this.socket = socket;
			// this.queue = socket.parser.queue;

			startPingTimer(socket);

			socket.parser.setActiveInstrumentsMap(activeBmInstrumentsMap);
			socket.parser.setOrderBooks(orderBooks);

			// String query = "{\"op\":\"subscribe\",
			// \"args\":[\"orderBookL2:XBTUSD\", \"trade:XBTUSD\"]}";
			// String query = "{\"op\":\"subscribe\",
			// \"args\":[\"orderBookL2:XBTUSD\"]}";

			// socket.sendMessage(query);

			// WAITING FOR THE SOCKET TO CLOSE
			System.out.println("BMConnector getClosingLatch().await()");
			socket.getClosingLatch().await();
			System.out.println("BMConnector getClosingLatch().await() PASSED");

			this.socket = null;
			socket.close();

		} catch (Throwable t) {
			t.printStackTrace();
		}

	}

	public void sendWebsocketMessage(String message) {
		try {
			System.out.println("BMConnector webSocketStartingLatch await()");
			getWebSocketStartingLatch().await();
			System.out.println("BMConnector webSocketStartingLatch await() PASSED");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		;
		socket.sendMessage(message);
	}

	private String getServerResponse(String address) {
		boolean connectionEstablished = false;
		String response = "";
		while (!connectionEstablished) {
			try {
				URL url = new URL(address);
				HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Accept", "application/json");

				int responseCode;

				try {
					conn.getResponseCode();
				} catch (UnknownHostException | NoRouteToHostException e) {
					System.out.println("NO RESPONSE FROM SERVER");
					System.out.println("SLEEP 5000");
					Thread.sleep(5000);
					continue;
				}

				if (conn.getResponseCode() != 200) {
					System.out.println(conn.getResponseCode());
					System.out.println("SLEEP 5000");
					Thread.sleep(5000);

					// throw new RuntimeException("Failed : HTTP error code : "
					// + conn.getResponseCode());
				} else {
					BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
					StringBuilder sb = new StringBuilder("");
					String output = null;

					while ((output = br.readLine()) != null) {
						sb.append(output);
					}

					conn.disconnect();
					response = sb.toString();
					connectionEstablished = true;

				}

			} catch (MalformedURLException | InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return response;
	}

	public void fillActiveInstrumentsMap() {
		String str = getServerResponse(restActiveInstrUrl);
//		System.out.println(str);
	
		BmInstrument[] instrs = JsonParser.getArrayFromJson(str, BmInstrument[].class);

		for (BmInstrument inst : instrs) {
			this.activeBmInstrumentsMap.put(inst.getSymbol(), inst);

			Set<String> set = new HashSet<>();
			set = activeBmInstrumentsMap.keySet();
		}
	}

	public void setBmInstIdx(String url) {
		activeBmInstrumentsMap.get("XBTUSD").setTickSize(0.01);
		String str = getServerResponse(url);
		InstrumentForIdx[] instrsIdx = JsonParser.getArrayFromJson(str, InstrumentForIdx[].class);

		for (Map.Entry<String, BmInstrument> entry : activeBmInstrumentsMap.entrySet()) {
			String symbol = entry.getKey();

			for (int i = 0; i < instrsIdx.length; i++) {
				if (symbol.equals(instrsIdx[i].getSymbol())) {
					entry.getValue().setSymbolIdx(i * 100000000l);
					break;
				}
			}
		}
	}

	private void setCorrectedTickSize() {
		activeBmInstrumentsMap.get("XBTUSD").setTickSize(0.01);
	}

	public HashMap<String, BmInstrument> getActiveInstrumentsMap() {
		return activeBmInstrumentsMap;
	}

	@Override
	public void run() {

		while (true) {
			fillActiveInstrumentsMap();
			setBmInstIdx(instrumentsIdxList);
			setCorrectedTickSize();
			connect();
		}
	}

}
