package bitmexAdapter;

import java.awt.List;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import velox.api.layer1.layers.utils.OrderBook;

public class JsonParser {

	// public static BmInstrument[] getInstrumentsFromString(String input) {
	// return new Gson().fromJson(input, BmInstrument[].class);
	// }
	//
	// public static InstrumentForIdx[] getIdxs(String input) {
	// return new Gson().fromJson(input, InstrumentForIdx[].class);
	// }

	@SuppressWarnings("unchecked")
	public static <T> T[] getArrayFromJson(String input, Class<T[]> cls) {
		return (T[]) new Gson().fromJson(input, cls);
	}

	public int strCount = 0;

	// OrderBook book;
	BlockingQueue<Msg> queue = new LinkedBlockingQueue<>();
	Gson gson = new GsonBuilder().create();
	public boolean isFirstSnapshotParsed = false;
	boolean isJustStarted = false;
	private CountDownLatch firstSnapshotlatch = new CountDownLatch(1);
	private HashMap<String, BmInstrument> activeInstrumentsMap = new HashMap<>();
	private HashMap<String, CustomOrderBook> orderBooks = new HashMap<>();

	// private int strCount = 0;

	public HashMap<String, BmInstrument> getActiveInstrumentsMap() {
		return activeInstrumentsMap;
	}

	public HashMap<String, CustomOrderBook> getOrderBooks() {
		return orderBooks;
	}

	public void setActiveInstrumentsMap(HashMap<String, BmInstrument> activeInstrumentsMap) {
		this.activeInstrumentsMap = activeInstrumentsMap;
	}

	public void setOrderBooks(HashMap<String, CustomOrderBook> orderBooks) {
		this.orderBooks = orderBooks;
	}

	public void parse(String str) {
		Msg msg = (Msg) gson.fromJson(str, Msg.class);

		if (msg == null || msg.action == null) {// skip messages if the action
												// is not
			// defined
			return;
		}

		if (!isFirstSnapshotParsed) {// if the first snapshot is not parsed yet

			if (!msg.action.equals("partial")) {
				// do nothing while haven't come across the partial
				return;
			} else {
				// action is partial so let's fill in the book
				fillTheOrderBook(msg);
				isFirstSnapshotParsed = true;
				firstSnapshotlatch.countDown();

				if (!isFirstSnapshotParsed) {
					isFirstSnapshotParsed = true;
				}
				BmInstrument instrum = activeInstrumentsMap.get(msg.getData().get(0).getSymbol());
				printInstrBook(instrum.getOrderBook().getAskMap(), instrum.getOrderBook().getBidMap());

				return;
			}
		} else {// the first snapshot is parsed
			try {
				if (!msg.action.equals("partial")) {
					// so now we can fill the queue with messages

					// CHANGED
					BmInstrument instr = activeInstrumentsMap.get(msg.getData().get(0).getSymbol());
					if (instr.isSubscribed()) {

						processBook(msg);

						instr.getQueue().add(msg);

					}

					// CHANGED
					// queue.add(msg);
					// System.out.println("QUEUE SIZE" + queue.size());
					// if (msg.getTable().equals("trade"))
					// System.out.println(str);
				} else {// or parse orderbooks for other instruments
					fillTheOrderBook(msg);
				}
			} catch (Exception e) {

				System.out.println("*********************MESSAGE***************");
				System.out.println(str);
				System.out.println(msg);
				e.printStackTrace();
			}
		}

		// messages.add(msg);

		// processBook();
		// strCount++;

		// if (strCount % 100 == 0) {
		//
		// System.out.println(strCount);
		// }
		//
		if (strCount % 500 == 0) {
			BmInstrument instr = activeInstrumentsMap.get(msg.getData().get(0).getSymbol());
			printInstrBook(instr.getOrderBook().getAskMap(), instr.getOrderBook().getBidMap());

			File file = new File("Map" + System.currentTimeMillis() + ".txt");

			try (PrintWriter pWriter = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {
				pWriter.append("HELLO WORLD");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		strCount++;
		//
		// if(strCount==2000)
		// return;

		// System.out.println(msg);
		//
		// for(DataUnit unit : msg.data){
		// dataUnits.add(unit);
		//// System.out.println(unit);
		// }

	}

	private void fillTheOrderBook(Msg msg) {
		if (msg.data == null || msg.data.size() == 0) {
			return;
		}

		BmInstrument instrum = activeInstrumentsMap.get(msg.data.get(0).getSymbol());
		// double tickSize = instrument.getTickSize();
		CustomOrderBook book = instrum.getOrderBook();

		synchronized (book) {

			for (DataUnit unit : msg.data) {
				BmInstrument instrument = activeInstrumentsMap.get(unit.getSymbol());
				double tickSize = instrument.getTickSize();

				int unitPriceInInteger = (int) (((double) unit.getPrice()) / tickSize);
				unit.setIntPrice(unitPriceInInteger);
				// System.out.println(String.format("%.12f", unit.getPrice()) +
				// "\t"
				// + unit.getSize());

				boolean isBid = unit.getSide().equals("Buy") ? true : false;
				// CustomOrderBook book = instrument.getOrderBook();

				book.onUpdate(isBid, unit.getIntPrice(), unit.getSize());
			}
		}
		// printInstrBook(instrum.getOrderBook().getAskMap(),
		// instrum.getOrderBook().getBidMap());
		System.out.println("IS FILLED");

	}

	private void processBook(Msg message) {
		ArrayList<DataUnit> units = message.data;
		BmInstrument instrum = activeInstrumentsMap.get(units.get(0).getSymbol());
		CustomOrderBook book = instrum.getOrderBook();

		for (DataUnit nextUnit : units) {
			boolean isBid = nextUnit.getSide().equals("Buy") ? true : false;

			long diff = instrum.getSymbolIdx() - nextUnit.getId();
			int price = (int) (diff);
			int size = (int) (nextUnit.getSize());

			if (message.action.equals("delete")) {
				if (isBid) {
					book.getBidMap().remove(price);
				} else {
					book.getAskMap().remove(price);
				}
				continue;
			}

			update(book, isBid, price, size);

		}
	}

	private void printInstrBook(TreeMap<Integer, Long> askMap, TreeMap<Integer, Long> bidMap) {

		System.out.println("printInstrBook********************");
		//
		// TreeMap<Integer, Long> askMap = book.getAskMap();
		// TreeMap<Integer, Long> bidMap = book.getBidMap();

		File file = new File("Map" + System.currentTimeMillis() + ".txt");

		try (PrintWriter pWriter = new PrintWriter(new BufferedWriter(new FileWriter(file, true)))) {
			pWriter.append("HELLO WORLD");
			// pWriter.append(message);
			// pWriter.append(System.lineSeparator());

			long keys = 0;
			long values = 0;

			// System.out.println("BIDS********************");

			for (Map.Entry<Integer, Long> entry : askMap.entrySet()) {
				Integer key = entry.getKey();
				int value = Math.toIntExact(entry.getValue());

				keys += key;
				values += value;
				// System.out.println(entry.getKey() + " => " +
				// entry.getValue());

				// pWriter.append(key + " => " + value);
				// pWriter.append(System.lineSeparator());
			}

			// System.out.println("ASKS********************");
			for (Map.Entry<Integer, Long> entry : bidMap.entrySet()) {
				Integer key = entry.getKey();
				int value = Math.toIntExact(entry.getValue());

				keys += key;
				values += value;

				// System.out.println(entry.getKey() + " => " +
				// entry.getValue());

				// pWriter.append(key + " => " + value);
				// pWriter.append(System.lineSeparator());
			}

			System.out.println(keys + " => " + values);
			pWriter.append(keys + " => " + values);
			pWriter.append(System.lineSeparator());
		} catch (IOException e) {
			e.printStackTrace();

		}
	}

	public CountDownLatch firstSnapshotlatch() {
		return firstSnapshotlatch;
	}

	public void update(CustomOrderBook book, boolean isBid, int price, long size) {
		if (isBid) {
			book.getBidMap().put(price, size);
		} else {
			book.getAskMap().put(price, size);
		}

	}

}
