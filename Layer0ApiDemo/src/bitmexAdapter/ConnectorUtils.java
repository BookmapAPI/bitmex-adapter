package bitmexAdapter;

import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.map.HashedMap;

import com.google.gson.reflect.TypeToken;
import com.ibm.icu.text.SimpleDateFormat;

import bookmap.adapter.Trade;

public class ConnectorUtils {
	public static final String bitmex_Wss = "wss://www.bitmex.com/realtime";
	public static final String bitmex_restApi ="https://www.bitmex.com";
	public static final String bitmex_restActiveInstrUrl = "https://www.bitmex.com/api/v1/instrument/active";
	
	public static final String testnet_Wss = "wss://testnet.bitmex.com/realtime";
	public static final String testnet_restApi = "https://testnet.bitmex.com";
//	public static final String testnet_restApi = "https://testnet.bitmex.com";
	public static final String testnet_restActiveInstrUrl = "https://testnet.bitmex.com/api/v1/instrument/active";
	
	public static enum TOPIC {
		orderBookL2, trade, //non-authenticated
		position, wallet, order, margin, execution; //authenticated 
	};
	
	
	
	public static Map <String, TOPIC> stringToTopic = new HashMap<>();
	public static EnumMap <TOPIC, TopicContainer> containers = new EnumMap<>(TOPIC.class);
	
	static {
		stringToTopic.put("orderBookL2", TOPIC.orderBookL2);
		stringToTopic.put("trade", TOPIC.trade);
		stringToTopic.put("wallet", TOPIC.wallet);
		stringToTopic.put("execution", TOPIC.execution);
		stringToTopic.put("margin", TOPIC.margin);
		stringToTopic.put("position", TOPIC.position);
		stringToTopic.put("order", TOPIC.order);
		
//		non-authenticated
		containers.put(TOPIC.orderBookL2, new TopicContainer("orderBookL2", false, new TypeToken<MessageGeneric<DataUnit>>() {}.getType(), DataUnit.class));
		containers.put(TOPIC.trade, new TopicContainer("trade", false, new TypeToken<MessageGeneric<BmTrade>>() {}.getType(), BmTrade.class));
//		authenticated
		containers.put(TOPIC.wallet, new TopicContainer("wallet", true, new TypeToken<MessageGeneric<Wallet>>() {}.getType(), Wallet.class));
		containers.put(TOPIC.execution, new TopicContainer("execution", true, new TypeToken<MessageGeneric<Execution>>() {}.getType(), Execution.class));
		containers.put(TOPIC.margin, new TopicContainer("margin", true, new TypeToken<MessageGeneric<Margin>>() {}.getType(), Margin.class));
		containers.put(TOPIC.position, new TopicContainer("position", true, new TypeToken<MessageGeneric<Position>>() {}.getType(), Position.class));
		containers.put(TOPIC.order, new TopicContainer("order", true, new TypeToken<MessageGeneric<BmOrder>>() {}.getType(), BmOrder.class));
		
	}
	

	public static String getDateTwentyFourHoursAgoAsUrlEncodedString() {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_WEEK, -1);
		Date date = calendar.getTime();
		String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		String s = sdf.format(date);
		System.out.println(s);
		StringBuilder sb = new StringBuilder();
		sb.append(s.substring(0, 10));
		sb.append("T");
		sb.append(s.substring(11, 13));
		sb.append("%3A");
		sb.append(s.substring(14, 16));
		sb.append("%3A");
		sb.append(s.substring(17));
		sb.append("Z");
		String z = sb.toString();
		return z;
	}
}
