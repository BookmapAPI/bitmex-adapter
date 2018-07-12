package bitmexAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.HttpsURLConnection;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import org.eclipse.jetty.websocket.api.UpgradeException;
import org.eclipse.jetty.websocket.api.WebSocketException;
import java.nio.channels.UnresolvedAddressException;

import bitmexAdapter.ConnectorUtils.WebSocketOperation;
import velox.api.layer0.live.Provider;
import velox.api.layer1.common.Log;

public class BmConnector implements Runnable {

	private boolean interruptionNeeded = false;
	private String wssUrl;
	private String restApi;
	private String restActiveInstrUrl;
	private HashMap<String, BmInstrument> activeBmInstrumentsMap = new HashMap<>();
	private CountDownLatch webSocketStartingLatch = new CountDownLatch(1);
	private CountDownLatch webSocketAuthLatch = new CountDownLatch(1);
	private ClientSocket socket;

	private JsonParser parser = new JsonParser();
	private boolean isReconnecting = false;
	private Provider provider;
	private TradeConnector tradeConnector;

	public Provider getProvider() {
		return provider;
	}

	public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public ClientSocket getSocket() {
		return socket;
	}

	public void setInterruptionNeeded(boolean interruptionNeeded) {
		this.interruptionNeeded = interruptionNeeded;
	}

	public String getRestApi() {
		return restApi;
	}

	public CountDownLatch getWebSocketAuthLatch() {
		return webSocketAuthLatch;
	}

	public TradeConnector getTradeConnector() {
		return tradeConnector;
	}

	public void setTradeConnector(TradeConnector tradeConnector) {
		this.tradeConnector = tradeConnector;
	}

	public CountDownLatch getWebSocketStartingLatch() {
		return webSocketStartingLatch;
	}

	private boolean isConnectionEstablished() {
		if (getServerResponse(restApi) == null) {
			return false;
		}
		return true;
	}

	public void setWssUrl(String wssUrl) {
		this.wssUrl = wssUrl;
	}

	public void setRestApi(String restApi) {
		this.restApi = restApi;
	}

	public void setRestActiveInstrUrl(String restActiveInstrUrl) {
		this.restActiveInstrUrl = restActiveInstrUrl;
	}

	public String wssAuthTwo() {
		String method = "GET";
		String subPath = "/realtime";
		String orderApiKey = tradeConnector.getOrderApiKey();
		String orderApiSecret = tradeConnector.getOrderApiSecret();
		long moment = ConnectorUtils.getMomentAndTimeToLive();
		String res = null;
		String messageBody = ConnectorUtils.createMessageBody(method, subPath, null, moment);
		String signature = ConnectorUtils.generateSignature(orderApiSecret, messageBody);

		WsData wsData = new WsData(WebSocketOperation.AUTHKEY, orderApiKey, moment, signature);
		res = JsonParser.gson.toJson(wsData);

		return res;
	}

	public void wsConnect() {
		SslContextFactory ssl = new SslContextFactory();
		WebSocketClient client = new WebSocketClient(ssl);
		Log.info("[bitmex] BmConnector wsConnect websocket starting...");

		try {
			Log.info("[bitmex] BmConnector wsConnect websocket being created...");
			socket = new ClientSocket();
			parser.setActiveInstrumentsMap(Collections.unmodifiableMap(activeBmInstrumentsMap));
			socket.setParser(parser);
			parser.setProvider(provider);

			Log.info("[bitmex] BmConnector wsConnect client starting...");

			client.start();
			URI echoUri = new URI(wssUrl);
			ClientUpgradeRequest request = new ClientUpgradeRequest();

			Log.info("[bitmex] BmConnector wsConnect websocket connecting...");
			client.connect(socket, echoUri, request);
			socket.getOpeningLatch().await();

			if (!provider.isCredentialsEmpty()) {// authentication needed
				Log.info("[bitmex] BmConnector wsConnect websocket auth...");
				String mes = wssAuthTwo();
				Log.info("[bitmex] BmConnector wsConnect websocket auth message passed");
				socket.sendMessage(mes);
				webSocketAuthLatch.await();
				WsData wsData = new WsData(WebSocketOperation.SUBSCRIBE,
						(Object[]) ConnectorUtils.getAuthenticatedTopicsList());
				String res = JsonParser.gson.toJson(wsData);
				socket.sendMessage(res);
			}

			webSocketStartingLatch.countDown();
			Log.info("[bitmex] BmConnector wsConnect websocket webSocketStartingLatch is down");

			if (isReconnecting) {
				provider.reportRestoredCoonection();

				for (BmInstrument instr : activeBmInstrumentsMap.values()) {
					if (instr.isSubscribed()) {
						subscribe(instr);
					}
				}
				isReconnecting = false;
			}

			Log.info("[bitmex] BmConnector wsConnect subscribed to an instrument ");
			// WAITING FOR THE SOCKET TO CLOSE
			socket.getClosingLatch().await();
			socket = null;
			isReconnecting = true;

		} catch (UpgradeException e) {
			e.printStackTrace();
			Log.info("[bitmex] BmConnector wsConnect client cannot connect 0 ");
		} catch (UnresolvedAddressException e) {
			e.printStackTrace();
			Log.info("[bitmex] BmConnector wsConnect client cannot connect 1 ");
		} catch (WebSocketException e) {
			e.printStackTrace();
			Log.info("[bitmex] BmConnector wsConnect connection must be lost ");
		} catch (Exception | Error e) {
			Log.info("[bitmex] BmConnector wsConnect an Exception thrown from the websocket");
			throw new RuntimeException(e);
		} finally {
			try {
				client.stop();
			} catch (Exception e) {
				Log.info("[bitmex] BmConnector wsConnect Got trouble stoppping client");
				e.printStackTrace();
				throw new RuntimeException(e);
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
		Log.info("[bitmex] BmConnector wsConnect Send websocket message");
		if (socket != null) {//this solution still needs to be examined
			synchronized (socket) {
				socket.sendMessage(message);
			}
		}
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
				StringBuilder sb = new StringBuilder();
				String output = null;

				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				// conn.disconnect();
				response = sb.toString();
			}
		} catch (UnknownHostException | NoRouteToHostException e) {
			Log.info("[bitmex] BmConnector getServerResponse: no response from server");
		} catch (SocketException e) {
			Log.info("[bitmex] BmConnector getServerResponse: network is unreachable");
		} catch (IOException e) {
			Log.info("[bitmex] BmConnector getServerResponse: buffer reading exception");
			e.printStackTrace();
		}
		return response;
	}

	public void fillActiveBmInstrumentsMap() {
		synchronized (activeBmInstrumentsMap) {
			String str = getServerResponse(restActiveInstrUrl);
			if (str != null) {
				BmInstrument[] instrs = JsonParser.getArrayFromJson(str, BmInstrument[].class);

				for (BmInstrument instr : instrs) {
					activeBmInstrumentsMap.put(instr.getSymbol(), instr);
				}
				activeBmInstrumentsMap.notify();
			}
		}
	}

	public HashMap<String, BmInstrument> getActiveInstrumentsMap() {
		return activeBmInstrumentsMap;
	}

	private void launchSnapshotTimer(BmInstrument instr) {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if (!instr.isOrderBookSnapshotParsed() == true) {
					Log.info("[bitmex] BmConnector launchSnapshotTimer(): resubscribe");
					unSubscribe(instr);
					subscribe(instr);
				}
				Log.info("[bitmex] BmConnector launchSnapshotTimer(): end");
			}
		};
		Timer timer = new Timer();
		Log.info("[bitmex] BmConnector launchSnapshotTimer(): ");
		timer.schedule(task, 10000);

	}

	public void subscribe(BmInstrument instr) {
		Log.info("[bitmex] BmConnector subscribe: " + instr.getSymbol());
		instr.setSubscribed(true);
		sendWebsocketMessage(instr.getSubscribeReq());
		launchSnapshotTimer(instr);

		if (!provider.isCredentialsEmpty()) {// if authenticated
			instr.setExecutionsVolume(countExecutionsVolume(instr.getSymbol()));
			reportFilled(instr.getSymbol());
			reportCancelled(instr.getSymbol());
		}
	}

	public void unSubscribe(BmInstrument instr) {
		instr.setSubscribed(false);
		sendWebsocketMessage(instr.getUnSubscribeReq());
	}

	public static long getMoment() {
		return System.currentTimeMillis() + 10000;
	}

	private int countExecutionsVolume(String symbol) {
		String z = ConnectorUtils.getDateTwentyFourHoursAgoAsUrlEncodedString();
		int sum = 0;
		// long moment = ConnectorUtils.getMomentAndTimeToLive();
		String addr = "/api/v1/execution?symbol=" + symbol
				+ "&filter=%7B%22ordStatus%22%3A%22Filled%22%7D&count=100&reverse=false&startTime=" + z;

		String st0 = tradeConnector.makeRestGetQuery(addr);
		UnitOrder[] orders = JsonParser.getArrayFromJson(st0, UnitOrder[].class);
		if (orders != null && orders.length > 0) {
			for (UnitOrder order : orders) {
				sum += order.getOrderQty();
			}
		}

		Log.info("[bitmex] BmConnector countExecution volume: " + st0);
		return sum;
	}

	private void reportFilled(String symbol) {
		String dateTwentyFourHoursAgo = ConnectorUtils.getDateTwentyFourHoursAgoAsUrlEncodedString();
		String addr = "/api/v1/execution?symbol=" + symbol
				+ "&filter=%7B%22ordStatus%22%3A%20%22Filled%22%7D&count=500&reverse=true&startTime="
				+ dateTwentyFourHoursAgo;
		String st0 = tradeConnector.makeRestGetQuery(addr);

		UnitExecution[] execs = JsonParser.getArrayFromJson(st0, UnitExecution[].class);
		if (execs != null && execs.length > 0) {
			provider.updateExecutionsHistory(execs);
		}

		Log.info("[bitmex] BmConnector reportFilled: " + st0);
	}

	private void reportCancelled(String symbol) {
		String dateTwentyFourHoursAgo = ConnectorUtils.getDateTwentyFourHoursAgoAsUrlEncodedString();
		String addr = "/api/v1/execution?symbol=" + symbol
				+ "&filter=%7B%22ordStatus%22%3A%20%22Canceled%22%7D&count=500&reverse=true&startTime="
				+ dateTwentyFourHoursAgo;
		String st0 = tradeConnector.makeRestGetQuery(addr);

		UnitExecution[] execs = JsonParser.getArrayFromJson(st0, UnitExecution[].class);
		if (execs != null && execs.length > 0) {
			provider.updateExecutionsHistory(execs);
		}

		Log.info("[bitmex] BmConnector reportCancelled: " + st0);
	}

	@Override
	public void run() {
		while (!interruptionNeeded) {

			if (!isConnectionEstablished()) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
				continue;
			}

			if (activeBmInstrumentsMap.isEmpty()) {
				fillActiveBmInstrumentsMap();
				if (activeBmInstrumentsMap.isEmpty())
					continue;
			}
			if (!interruptionNeeded) {
				wsConnect();
			}
			if (!interruptionNeeded) {
				provider.reportLostCoonection();
			}

		}
		if (socket != null) {
			socket.close();
		}
		Log.info("[bitmex] BmConnector run: closing");
	}
}