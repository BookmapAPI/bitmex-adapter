package bitmexAdapter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import velox.api.layer1.common.Log;
import velox.api.layer1.layers.utils.OrderBook;

public class JsonParser {

	public static <T> T[] getArrayFromJson(String input, Class<T[]> cls) {
		return (T[]) new Gson().fromJson(input, cls);
	}

	private static final Gson gson = new GsonBuilder().create();
	private Map<String, BmInstrument> activeInstrumentsMap = new HashMap<>();

	public void setActiveInstrumentsMap(Map<String, BmInstrument> activeInstrumentsMap) {
		this.activeInstrumentsMap = activeInstrumentsMap;
	}

	public void parse(String str) {
		Message msg = (Message) gson.fromJson(str, Message.class);
		
		// skip messages if the action is not defined
		if (msg == null || msg.action == null) {
//			Log.info(str);
			return;
		}

		BmInstrument instr = activeInstrumentsMap.get(msg.getData().get(0).getSymbol());
		if (!instr.isSubscribed()) {
			return;
		}

		if (!instr.isFirstSnapshotParsed()) {
			if (msg.action.equals("partial")) {
				// action is partial so let's fill in the orderBook
				if (!instr.getOrderBook().getAskMap().isEmpty()) {
					// reset the book after reconnect
					resetBookMapOrderBook(instr);
					resetBmInstrumentOrderBook(instr);
				}
				processOrderMessage(msg);
				msg.setData(putBestAskToTheHeadOfList(msg.getData()));
				instr.getQueue().add(msg);

				// this is the signal for parser to start
				// processing every message
				instr.setFirstSnapshotParsed(true);
			} else {
				return; // otherwise wait for partial
			}
		} else {
			if (msg.getTable().equals("trade")) {
				processTradeMessage(msg);
			} else {
				processOrderMessage(msg);
			}
			instr.getQueue().add(msg);
		}
	}

	/**
	 * data units in the snapshot are sorted from highest price to lowest This
	 * may result in a huge red bestAsk peak on the screen because for a couple
	 * of milliseconds the bestAsk is actually the most expensive ask What is
	 * even worse, to fit this peak to the screen the picture gets zoomed out
	 * which looks pretty weird To avoid this bestAsk is moved to the beginning
	 * of the list
	 **/
	private ArrayList<DataUnit> putBestAskToTheHeadOfList(ArrayList<DataUnit> units) {
		int firstAskIndex = 0;

		for (int i = 0; i < units.size(); i++) {
			if (units.get(i + 1).isBid()) {
				firstAskIndex = i;
				break;
			}
		}
		if (firstAskIndex != 0) {
			Collections.swap(units, 0, firstAskIndex);
		}
		return units;
	}

	/*
	 * setting missing values for dataunits' fields adding missing prices to
	 * <id,intPrice> map updating the orderBook This refers to order book
	 * updates only Trade orders are processed in processTradeMsg method
	 */
	private void processOrderMessage(Message msg) {
		BmInstrument instr = activeInstrumentsMap.get(msg.data.get(0).getSymbol());
		OrderBook book = instr.getOrderBook();

		for (DataUnit unit : msg.data) {
			unit.setBid(unit.getSide().equals("Buy"));
			HashMap<Long, Integer> pricesMap = instr.getPricesMap();
			int intPrice;

			if (msg.getAction().equals("delete")) {
				intPrice = pricesMap.get(unit.getId());
				unit.setSize(0);
			} else {
				if (msg.getAction().equals("update")) {
					intPrice = pricesMap.get(unit.getId());
				} else {// action is partial or insert
					intPrice = createIntPrice(unit.getPrice(), instr.getTickSize());
					pricesMap.put(unit.getId(), intPrice);
				}
			}
			unit.setIntPrice(intPrice);
			book.onUpdate(unit.isBid(), intPrice, unit.getSize());
		}
	}

	private int createIntPrice(double price, double tickSize){
//		BigDecimal pr = new BigDecimal(price, MathContext.DECIMAL32);
//		BigDecimal ts = new BigDecimal(tickSize, MathContext.DECIMAL32);
//		BigDecimal res = pr.divide(ts, 0, BigDecimal.ROUND_HALF_UP);
//		int intPrice = res.intValue();
		
		int intPrice = (int) Math.round(price/tickSize);
//		Log.info(price + "=>" + intPrice);
		return intPrice;
	}
	
	private void processTradeMessage(Message msg) {
		BmInstrument instr = activeInstrumentsMap.get(msg.data.get(0).getSymbol());

		for (DataUnit unit : msg.data) {
			unit.setBid(unit.getSide().equals("Buy"));
			int intPrice = createIntPrice(unit.getPrice(), instr.getTickSize());
			unit.setIntPrice(intPrice);
		}
	}

/**	 resets orderBooks (both for Bookmap and for BmInstrument) after
	 disconnect and reconnect
	 For better visualization purposes besAsk and bestBid will go last
	 in this method and come first in putBestAskToTheHeadOfList method
	 (see the description for putBestAskToTheHeadOfList method)**/
	private void resetBookMapOrderBook(BmInstrument instr) {
		// Extracting lists of levels from ask and Bid maps
		String symbol = instr.getSymbol();
		ArrayList<DataUnit> units = new ArrayList<>();

		TreeMap<Integer, Long> askMap = instr.getOrderBook().getAskMap();
		ArrayList<Integer> askList = new ArrayList<>(askMap.keySet());
		Collections.sort(askList, Collections.reverseOrder());
		int i = askList.size() - 1;
		int bestAsk = askList.get(i);
		askList.remove(i);

		for (Integer intPrice : askList) {
			units.add(new DataUnit(symbol, intPrice, false));
		}

		TreeMap<Integer, Long> bidMap = instr.getOrderBook().getBidMap();
		ArrayList<Integer> bidList = new ArrayList<>(bidMap.keySet());
		Collections.sort(bidList);
		i = bidList.size() - 1;
		int bestBid = bidList.get(i);
		bidList.remove(bidList.get(i));

		for (Integer intPrice : bidList) {
			units.add(new DataUnit(symbol, intPrice, true));
		}

		units.add(new DataUnit(symbol, bestAsk, false));
		units.add(new DataUnit(symbol, bestBid, true));

		Message mess = new Message("orderBookL2", "delete", units);
		instr.getQueue().add(mess);
	}

	private void resetBmInstrumentOrderBook(BmInstrument instr) {
		OrderBook book = instr.getOrderBook();
		TreeMap<Integer, Long> askMap = instr.getOrderBook().getAskMap();
		Set<Integer> askSet = new HashSet<>(askMap.keySet());
		for (Integer intPrice : askSet) {
			book.onUpdate(false, intPrice, 0);
		}

		TreeMap<Integer, Long> bidMap = instr.getOrderBook().getAskMap();
		Set<Integer> bidSet = new HashSet<>(bidMap.keySet());
		for (Integer intPrice : bidSet) {
			book.onUpdate(true, intPrice, 0);
		}
	}
}
