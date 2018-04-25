package bitmexAdapter;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

import velox.api.layer1.layers.utils.OrderBook;

public class CustomOrderBook extends OrderBook {
	
	@Override
	public void onUpdate(boolean isBid, int price, long size){
		if (isBid){
			TreeMap<Integer, Long> bidmap = getBidMap();
			synchronized (bidmap) {
				bidmap.put(price, size);
			}
		} else {
			TreeMap<Integer, Long> askmap = getAskMap();
			synchronized (askmap) {
				askmap.put(price, size);
			}
		}
	}
}
