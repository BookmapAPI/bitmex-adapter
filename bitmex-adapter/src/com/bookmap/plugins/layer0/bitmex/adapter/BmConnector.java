package com.bookmap.plugins.layer0.bitmex.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.UpgradeException;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.bookmap.plugins.layer0.bitmex.Provider;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.WebSocketOperation;

import velox.api.layer1.common.Log;
import velox.api.layer1.data.SubscribeInfo;

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

	private ScheduledExecutorService executionsResetTimer;
	private int executionDay = 0;
	private boolean isExecutionReset;
	
	private Object socketLock = new Object();
	private int timerCount = 0;

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
		
		Log.info("[bitmex] BmConnector wssAuthTwo() moment = " + moment);
		
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

				reportHistoricalExecutions("Filled");
				reportHistoricalExecutions("Canceled");
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

			// WAITING FOR THE SOCKET TO CLOSE
			
			CountDownLatch closingLatch = socket.getClosingLatch();
			closingLatch.await();
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

		synchronized (socketLock) {
			if (socket != null) {
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
		int localTimerCount = timerCount;
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				Thread.currentThread().setName("-> BmConnector: snapshotTimer " + localTimerCount + " for" + instr.getSymbol() );
			
				if (socket == null || isReconnecting){
					Log.info("Waiting for the socket, timer " + localTimerCount + " shutdown");
					return;
				}
				
				if (!instr.isOrderBookSnapshotParsed()) {
					Log.info("[bitmex] BmConnector launchSnapshotTimer " + localTimerCount + " for" + instr.getSymbol() + ": resubscribe " + ZonedDateTime.now(ZoneOffset.UTC));
					unSubscribe(instr);
					
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					subscribe(instr);
				} else {
					Log.info("[bitmex] BmConnector launchSnapshotTimer " + localTimerCount + " for" + instr.getSymbol() + ": end " + ZonedDateTime.now(ZoneOffset.UTC));
				}
			}
		};
		
		Timer timer = new Timer();
		instr.setSnapshotTimer(timer);
		Log.info("[bitmex] BmConnector launchSnapshotTimer " + localTimerCount + " for" + instr.getSymbol() + ": " + ZonedDateTime.now(ZoneOffset.UTC));
		timer.schedule(task, 15000);
		timerCount++;
	}

	private void launchExecutionsResetTimer() {
		class CustomThreadFactory implements ThreadFactory {
			public Thread newThread(Runnable r) {
				return new Thread(r, "-> BmConnector: executionsResetTimer");
			}
		}

		ScheduledExecutorService executionsResetTimer = Executors
				.newSingleThreadScheduledExecutor(new CustomThreadFactory());
		this.executionsResetTimer = executionsResetTimer;
		Log.info("[bitmex] BmConnector launchExecutionsResetTimer(): ");
		executionsResetTimer.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				int dayNow = ZonedDateTime.now(ZoneOffset.UTC).getDayOfYear();

				if (executionDay < dayNow && !isExecutionReset) {
					executionDay = dayNow;

					Set<BmInstrument> instruments = new HashSet<>();
					synchronized (activeBmInstrumentsMap) {
						instruments.addAll(activeBmInstrumentsMap.values());
					}
					for (BmInstrument instrument : instruments) {
						instrument.setExecutionsVolume(0);
					}

					isExecutionReset = true;
				} else if (executionDay == dayNow) {
					isExecutionReset = false;
				}
			}
		}, 0, 1, TimeUnit.SECONDS);
	}

	public void subscribe(BmInstrument instr) {
		Log.info("[bitmex] BmConnector subscribe: " + instr.getSymbol());
		instr.setSubscribed(true);
		sendWebsocketMessage(instr.getSubscribeReq());
		launchSnapshotTimer(instr);

		if (!provider.isCredentialsEmpty()) {// if authenticated
			instr.setExecutionsVolume(countExecutionsVolume(instr.getSymbol()));
		}
	}

	public void unSubscribe(BmInstrument instr) {
		sendWebsocketMessage(instr.getUnSubscribeReq());
		
		Timer timer = instr.getSnapshotTimer();
		if (timer != null) {
			timer.cancel();
			Log.info("[bitmex] BmConnector unSubscribe: timer gets cancelled");
			}
		instr.setSubscribed(false);
		
	}

	private int countExecutionsVolume(String symbol) {
		String dataADayBefore = ConnectorUtils.getDateTwentyFourHoursAgoAsUrlEncodedString();
		StringBuilder sb = new StringBuilder();
		sb.append("/api/v1/execution?symbol=").append(symbol)
				.append("&filter=%7B%22ordStatus%22%3A%22Filled%22%7D&count=100&reverse=false&startTime=")
				.append(dataADayBefore);

		String addr = sb.toString();

		String st0 = tradeConnector.makeRestGetQuery(addr);
		UnitOrder[] orders = JsonParser.getArrayFromJson(st0, UnitOrder[].class);
		int sum = 0;

		if (orders != null && orders.length > 0) {
			for (UnitOrder order : orders) {
				sum += order.getOrderQty();
			}
		}

		Log.info("[bitmex] BmConnector countExecution volume: " + st0);
		return sum;
	}

	private void reportHistoricalExecutions(String ordStatus) {
		// private void reportHistoricalExecutions(String symbol, String
		// ordStatus) {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		ZonedDateTime startTime = now.minusDays(0)
				.minusHours(now.getHour())
				.minusMinutes(now.getMinute())
				.minusSeconds(now.getSecond())
				.minusNanos(now.getNano());

		List<UnitExecution> historicalExecutions = new LinkedList<>();
		for (int i = 0;; i += 500) {
			StringBuilder sb = new StringBuilder();
			sb.append("/api/v1/execution?").append("filter=%7B%22ordStatus%22%3A%20%22")
					.append(ordStatus).append("%22%7D&count=500&reverse=true&startTime=")
					.append(startTime).append("&endTime=").append(now)
					.append("&start=").append(i);

			String addr = sb.toString();
			String response = tradeConnector.makeRestGetQuery(addr);

			UnitExecution[] executions = JsonParser.getArrayFromJson(response,
					UnitExecution[].class);

			if (executions == null || executions.length == 0) {
				break;
			} else {
				for (int k = 0, n = executions.length; k < n; k++) {
					if (!executions[k].getExecType().equals("Funding")) {
						historicalExecutions.add(executions[k]);
					}
				}
			}
		}
		Log.info("[bitmex] BmConnector report " + ordStatus + ": listSize =  " + historicalExecutions.size());

		if (historicalExecutions != null && historicalExecutions.size() > 0) {
			provider.updateExecutionsHistory(
					historicalExecutions.toArray(new UnitExecution[historicalExecutions.size()]));
		}
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

				CopyOnWriteArrayList<SubscribeInfo> knownInstruments = new CopyOnWriteArrayList<>();
				for (BmInstrument instrument : activeBmInstrumentsMap.values()) {
						knownInstruments.add(new SubscribeInfo(instrument.getSymbol(), null, null));
				}
				provider.setKnownInstruments(knownInstruments);

				launchExecutionsResetTimer();
				if (activeBmInstrumentsMap.isEmpty()) {
					continue;
				}
			}
			if (!interruptionNeeded) {
				wsConnect();
			}
			if (!interruptionNeeded) {
				provider.reportLostConnection();
			}

		}
		executionsResetTimer.shutdownNow();
		closeSocket();

		Log.info("[bitmex] BmConnector run: closing");
	}
	
	public void closeSocket(){
		synchronized (socketLock) {
			if (socket != null) {
				socket.close();
			}
		}
	}
}
