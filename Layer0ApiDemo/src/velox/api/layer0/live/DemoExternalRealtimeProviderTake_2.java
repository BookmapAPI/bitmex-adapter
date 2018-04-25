package velox.api.layer0.live;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;

import velox.api.layer1.Layer1ApiAdminListener;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.LoginFailedReason;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderUpdateParameters;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.data.UserPasswordDemoLoginData;

import bitmexAdapter.BitmexConnector;
import bitmexAdapter.DataUnit;
import bitmexAdapter.Msg;
import bitmexAdapter.BmInstrument;

/**
 * <p>
 * This a demo provider that generates data instead of actually receiving it.
 * </p>
 */
public class DemoExternalRealtimeProviderTake_2 extends ExternalLiveBaseProvider {

	// CONNECTOR
	BitmexConnector connector = new BitmexConnector();
	{
		File file = new File("Map" + System.currentTimeMillis() + ".txt");

		try (PrintWriter pWriter = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {
			 pWriter.append("HELLO WORLD");
			 pWriter.append(System.lineSeparator());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("BmConnector starting");
		Thread thread = new Thread(connector);
		thread.setName("**********CONNECTOR THREAD*********");
		thread.start();
		
	
	}

	protected class Instrument {
		// **************************
		{
			System.out.println("CLASS Instruments OBJECT INVOKED");
		}

		protected final String alias;
		protected final double pips;

		public Instrument(String alias, double pips) {

			// **************************
			System.out.println("Instrument constructor INVOKED");

			this.alias = alias;
			this.pips = pips;
		}

		private void readTheBook(BmInstrument instr, TreeMap<Integer, Long> askMap, TreeMap<Integer, Long> bidMap) {

			String symbol = instr.getSymbol();

			synchronized (askMap) {

				for (Map.Entry<Integer, Long> entry : askMap.entrySet()) {
					Integer key = entry.getKey();
//					int value = Math.toIntExact(entry.getValue());
					int value = Math.toIntExact(entry.getValue());
					// dataListeners.forEach(l -> l.onDepth(alias, false, key,
					// value));
					for (Layer1ApiDataListener listener : dataListeners) {
						listener.onDepth(symbol, false, key, value);
					}
//****************************************
					 System.out.println(key + " => " + value);
				}
			}

			synchronized (bidMap) {

				for (Map.Entry<Integer, Long> entry : bidMap.entrySet()) {
					Integer key = entry.getKey();
					int value = Math.toIntExact(entry.getValue());
					dataListeners.forEach(l -> l.onDepth(symbol, true, key, value));
//*******************************************
//					System.out.println(alias + "\t" + key + " => " + value);
					System.out.println(key + " => " + value);
				}
			}

			instr.setBookRead(true);

		}

		public void generateData(String symbol) {

			//// ВРЕМЕННЫЙ КОСТЫЛЬ
			// Msg mes = connector.queue.peek();
			// if(mes == null || mes.getData()==null)
			// return;
			//
			// if(mes.getData().get(0).getSymbol() == null)
			// return;
			//
			// String tempSymb = mes.getData().get(0).getSymbol();
			// if (!tempSymb.equals(symbol))
			// return;
			//
			//// КОСТЫЛЬ END

			// *************************
			// System.out.println("METHOD generateData");

			if (connector.socket == null) {
				return;
				// connector.connect();
			}

			//
			if (!connector.socket.parser.isFirstSnapshotParsed) {
				return;
			}

			BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);

			if (!bmInstrument.isBookRead) {

				readTheBook(bmInstrument, bmInstrument.getOrderBook().getAskMap(),
						bmInstrument.getOrderBook().getBidMap());

			} else {

				BlockingQueue<Msg> messages = bmInstrument.getQueue();

				if (!messages.isEmpty()) {
					Msg message = messages.poll();
					List<DataUnit> units = message.data;

					if (message.table.equals("orderBookL2")) {
						for (DataUnit nextUnit : units) {
							boolean isBid = nextUnit.getSide().equals("Buy") ? true : false;
							String unitSymbol = nextUnit.getSymbol();
							long diff = bmInstrument.getSymbolIdx() - nextUnit.getId();
							int price = (int)(diff);
							int size = (int)(nextUnit.getSize());

							if (message.action.equals("delete")) {
								for (Layer1ApiDataListener listener : dataListeners) {
									listener.onDepth(symbol, isBid, price, 0);
//			*************************************
									System.out.println("DELETE " + unitSymbol + "\t" + alias + "\t" + price + " => ");
								}
								// dataListeners.forEach(l -> l.onDepth(alias,
								// isBid, key, 0));
							} else { // action equals "insert" or "update"
								for (Layer1ApiDataListener listener : dataListeners) {
									listener.onDepth(symbol, isBid, price, size);
//		************************************************
									System.out.println("UPDATE" + unitSymbol + "\t" + alias + "\t" + price + " => " + size
											+ "\t\tprice =" + nextUnit.getPrice());
								}
								// dataListeners.forEach(l -> l.onDepth(alias,
								// isBid, key, valueS));
								// System.out.println("alias " + alias + "\tis
								// bid " + isBid +"\tkey" + key +"\tValue " +
								// valueS);

							}
						}
					}

					if (message.table.equals("trade")) {
						for (DataUnit nextUnit : units) {
							boolean isBid = nextUnit.getSide().equals("Buy") ? true : false;

							if (message.table.equals("trade") && message.action.equals("insert")) {
								final boolean isOtc = false;
								int valueS = Math.toIntExact(nextUnit.getSize());
								// the other side is agressor
								boolean isAgressor = !isBid;
								
//**************************
//								if (nextUnit.getSymbol().equals("ADAM18")) {
//									System.out.println("TRAD E" + nextUnit);
//
//								}

								int intPrice = (int) (nextUnit.getPrice() / bmInstrument.getTickSize());
								// dataListeners.forEach(
								// l -> l.onTrade(alias, intPrice, valueS, new
								// TradeInfo(isOtc, isAgressor)));

								for (Layer1ApiDataListener listener : dataListeners) {
									listener.onTrade(symbol, intPrice, valueS, new TradeInfo(isOtc, isAgressor));
//***********************************									
									System.out.println("TRADE " + symbol + "\t" + intPrice + " => " + valueS);
								}

							}

						}

					}
				}
			}
		}
	}

	protected HashMap<String, Instrument> instruments = new HashMap<>();

	// This thread will perform data generation.
	private Thread connectionThread = null;

	/**
	 * <p>
	 * Generates alias from symbol, exchange and type of the instrument. Alias
	 * is a unique identifier for the instrument, but it's also used in many
	 * places in UI, so it should also be easily readable.
	 * </p>
	 * <p>
	 * Note, that you don't have to use all 3 fields. You can just ignore some
	 * of those, for example use symbol only.
	 * </p>
	 */
	private static String createAlias(String symbol, String exchange, String type) {

		// **************************
		System.out.println("METHOD createAlias");

		// return symbol + "/" + exchange + "/" + type;
		return symbol;
	}

	@Override
	public void subscribe(String symbol, String exchange, String type) {

		String alias = createAlias(symbol, exchange, type);
		// Since instruments also will be accessed from the data generation
		// thread, synchronization is required
		//
		// No need to worry about calling listener from synchronized block,
		// since those will be processed asynchronously
		synchronized (instruments) {
			if (instruments.containsKey(alias)) {
				instrumentListeners.forEach(l -> l.onInstrumentAlreadySubscribed(symbol, exchange, type));
			} else {
				// We are performing subscription synchronously for simplicity,
				// but if subscription process takes long it's better to do it
				// asynchronously (e.g use Executor)

				HashMap<String, BmInstrument> activeBmInstruments = connector.getActiveInstrumentsMap();
				Set<String> set = activeBmInstruments.keySet();		
				
				if (set.contains(symbol)) {
					BmInstrument instr = activeBmInstruments.get(symbol);
					double pips = instr.getTickSize();
					instr.setSubscribed(true);

					String query = "{\"op\":\"subscribe\", \"args\":[\"orderBookL2:" + symbol + "\",\"trade:" + symbol
							+ "\"]}";
					
					try {
						System.out.println("DEMO webSocketStartingLatch await()");
						connector.getWebSocketStartingLatch().await();
						System.out.println("DEMO webSocketStartingLatch await() PASSED");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					connector.sendWebsocketMessage(query);
					final Instrument newInstrument = new Instrument(alias, pips);
					instruments.put(alias, newInstrument);
					final InstrumentInfo instrumentInfo = new InstrumentInfo(symbol, exchange, type, newInstrument.pips,
							1, "", false);

					instrumentListeners.forEach(l -> l.onInstrumentAdded(alias, instrumentInfo));
				}
			}
		}
	}

	@Override
	public void unsubscribe(String alias) {

		// **************************
		System.out.println("METHOD unsubscribe");

		synchronized (instruments) {
			if (instruments.remove(alias) != null) {
				instrumentListeners.forEach(l -> l.onInstrumentRemoved(alias));
			}
		}
		BmInstrument instr = connector.getActiveInstrumentsMap().get(alias);
		instr.setSubscribed(false);

		String str = "{\"op\":\"unsubscribe\", \"args\":[\"orderBookL2:" + alias + "\",\"trade:" + alias + "\"]}";

		connector.socket.sendMessage(str);

	}

	@Override
	public String formatPrice(String alias, double price) {

		// **************************
		// System.out.println("METHOD formatPrice");

		// Use default Bookmap price formatting logic for simplicity.
		// Values returned by this method will be used on price axis and in few
		// other places.

		double pips;
		synchronized (instruments) {
			pips = instruments.get(alias).pips;
		}

		return formatPriceDefault(pips, price);
	}

	@Override
	public void sendOrder(OrderSendParameters orderSendParameters) {

		// **************************
		System.out.println("METHOD sendOrder");

		// This method will not be called because this adapter does not report
		// trading capabilities
		throw new RuntimeException("Not trading capable");
	}

	@Override
	public void updateOrder(OrderUpdateParameters orderUpdateParameters) {

		// **************************
		System.out.println("METHOD updateOrder");

		// This method will not be called because this adapter does not report
		// trading capabilities
		throw new RuntimeException("Not trading capable");
	}

	@Override
	public void login(LoginData loginData) {

		// **************************
		System.out.println("METHOD login");

		UserPasswordDemoLoginData userPasswordDemoLoginData = (UserPasswordDemoLoginData) loginData;

		// If connection process takes a while then it's better to do it in
		// separate thread
		connectionThread = new Thread(() -> handleLogin(userPasswordDemoLoginData));		
		connectionThread.setName("-> INSTRUMENT");;
		connectionThread.start();
	}

	private void handleLogin(UserPasswordDemoLoginData userPasswordDemoLoginData) {

		// **************************
		System.out.println("METHOD handleLogin");

		// With real connection provider would attempt establishing connection
		// here.
		boolean isValid = "pass".equals(userPasswordDemoLoginData.password)
				&& "user".equals(userPasswordDemoLoginData.user) && userPasswordDemoLoginData.isDemo == true;

		if (isValid) {
			// Report succesful login
			adminListeners.forEach(Layer1ApiAdminListener::onLoginSuccessful);

			// CONNECTOR
			// this.connector = new Connector();
			// Thread thOne = new Thread(this.connector);
			// thOne.start();

			// Generate some events each second
			while (!Thread.interrupted()) {

				// Generate some data changes
				simulate();

				// Waiting a bit before generating more data
				// try {
				// Thread.sleep(1000);
				// } catch (@SuppressWarnings("unused") InterruptedException e)
				// {
				// Thread.currentThread().interrupt();
				// }
			}
		} else {
			// Report failed login
			adminListeners.forEach(l -> l.onLoginFailed(LoginFailedReason.WRONG_CREDENTIALS,
					"This provider only acepts following credentials:\n" + "username: user\n" + "password: pass\n"
							+ "is demo: checked"));
		}
	}

	protected void simulate() {

		// **************************
		// System.out.println("METHOD simulate");

		// Generating some data for each of the instruments
		synchronized (instruments) {
			// instruments.values().forEach(Instrument::generateData);

			for (Instrument instrument : instruments.values()) {
				instrument.generateData(instrument.alias);
			}
		}
	}

	@Override
	public String getSource() {

		// **************************
		System.out.println("METHOD getSource");

		// String identifying where data came from.
		// For example you can use that later in your indicator.
		return "realtime demo";
	}

	@Override
	public void close() {

		// **************************
		System.out.println("METHOD close");

		// Stop events generation
		connectionThread.interrupt();
	}

}
