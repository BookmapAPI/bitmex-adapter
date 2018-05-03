package velox.api.layer0.live;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import bitmexAdapter.Message;
import bitmexAdapter.BmInstrument;

/**
 * <p>
 * This a demo provider that generates data instead of actually receiving it.
 * </p>
 */
public class DemoExternalRealtimeProviderTake_2 extends ExternalLiveBaseProvider {

	private BitmexConnector connector;

	protected class Instrument {

		protected final String alias;
		protected final double pips;

		public Instrument(String alias, double pips) {
			this.alias = alias;
			this.pips = pips;
		}

		public void generateData(String symbol) {
			BmInstrument bmInstrument = connector.getActiveInstrumentsMap().get(symbol);

			if (!bmInstrument.isFirstSnapshotParsed()) {
				return;
			}
			BlockingQueue<Message> messages = bmInstrument.getQueue();

			if (!messages.isEmpty()) {
				Message message = messages.poll();

				// if (message == null || message.getAction() == null ||
				// message.getData() == null) {
				// Log.info("***********NULL POINTER AT MESSAGE " + message);
				// }

				List<DataUnit> units = message.data;

				if (message.table.equals("orderBookL2")) {
					for (DataUnit unit : units) {
						for (Layer1ApiDataListener listener : dataListeners) {
							listener.onDepth(symbol, unit.isBid(), unit.getIntPrice(), (int) unit.getSize());
						}
					}
				} else {
					for (DataUnit unit : units) {
						for (Layer1ApiDataListener listener : dataListeners) {
							final boolean isOtc = false;
							listener.onTrade(symbol, unit.getIntPrice(), (int) unit.getSize(),
									new TradeInfo(isOtc, !unit.isBid()));
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

				// This is delivered after REST query response
				HashMap<String, BmInstrument> activeBmInstruments = this.connector.getActiveInstrumentsMap();
				Set<String> set = new HashSet<>();

				synchronized (activeBmInstruments) {
					if(activeBmInstruments.isEmpty()){
						try {
							activeBmInstruments.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}}
					for (String key : activeBmInstruments.keySet()) {
						set.add(key);
					}
				}

				if (set.contains(symbol)) {
					try {
						this.connector.getWebSocketStartingLatch().await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					BmInstrument instr = activeBmInstruments.get(symbol);
					double pips = instr.getTickSize();

					final Instrument newInstrument = new Instrument(alias, pips);
					instruments.put(alias, newInstrument);
					final InstrumentInfo instrumentInfo = new InstrumentInfo(symbol, exchange, type, newInstrument.pips,
							1, "", false);

					instrumentListeners.forEach(l -> l.onInstrumentAdded(alias, instrumentInfo));
					this.connector.subscribe(instr);
				} else {
					instrumentListeners.forEach(l -> l.onInstrumentNotFound(symbol, exchange, type));
				}
			}
		}
	}

	@Override
	public void unsubscribe(String alias) {

		synchronized (instruments) {
			if (instruments.remove(alias) != null) {
				instrumentListeners.forEach(l -> l.onInstrumentRemoved(alias));
			}
		}
		BmInstrument instr = this.connector.getActiveInstrumentsMap().get(alias);
		this.connector.unSubscribe(instr);

	}

	@Override
	public String formatPrice(String alias, double price) {
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
		// This method will not be called because this adapter does not report
		// trading capabilities
		throw new RuntimeException("Not trading capable");
	}

	@Override
	public void updateOrder(OrderUpdateParameters orderUpdateParameters) {
		// This method will not be called because this adapter does not report
		// trading capabilities
		throw new RuntimeException("Not trading capable");
	}

	@Override
	public void login(LoginData loginData) {
		UserPasswordDemoLoginData userPasswordDemoLoginData = (UserPasswordDemoLoginData) loginData;
		// If connection process takes a while then it's better to do it in
		// separate thread
		connectionThread = new Thread(() -> handleLogin(userPasswordDemoLoginData));
		connectionThread.setName("-> INSTRUMENT");
		connectionThread.start();
	}

	private void handleLogin(UserPasswordDemoLoginData userPasswordDemoLoginData) {
		// With real connection provider would attempt establishing connection
		// here.
		
		//there is no need in password check for demo purposes
//		boolean isValid = "pass".equals(userPasswordDemoLoginData.password)
//				&& "user".equals(userPasswordDemoLoginData.user) && userPasswordDemoLoginData.isDemo == true;

//		if (isValid) {
			// Report succesful login
			adminListeners.forEach(Layer1ApiAdminListener::onLoginSuccessful);

			// CONNECTOR
			this.connector = new BitmexConnector();
			Thread thread = new Thread(this.connector);
			thread.setName("->BitmexAdapter: connector");
			thread.start();

			// Generate some events each second
			while (!Thread.interrupted()) {

				// Generate some data changes
				simulate();
			}
//		} else {
//			// Report failed login
//			adminListeners.forEach(l -> l.onLoginFailed(LoginFailedReason.WRONG_CREDENTIALS,
//					"This provider only acepts following credentials:\n" + "username: user\n" + "password: pass\n"
//							+ "is demo: checked"));
//		}
	}

	protected void simulate() {
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
		// String identifying where data came from.
		// For example you can use that later in your indicator.
		return "realtime demo";
	}

	@Override
	public void close() {
		// Stop events generation
		connectionThread.interrupt();
	}

}
