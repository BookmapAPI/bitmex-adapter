package bitmexAdapter;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Hex;

import com.google.gson.reflect.TypeToken;
import com.ibm.icu.text.SimpleDateFormat;


public class ConnectorUtils {
	public static final String bitmex_Wss = "wss://www.bitmex.com/realtime";
	public static final String bitmex_restApi ="https://www.bitmex.com";
	public static final String bitmex_restActiveInstrUrl = "https://www.bitmex.com/api/v1/instrument/active";
	
	public static final String testnet_Wss = "wss://testnet.bitmex.com/realtime";
	public static final String testnet_restApi = "https://testnet.bitmex.com";
	public static final String testnet_restActiveInstrUrl = "https://testnet.bitmex.com/api/v1/instrument/active";
	
	public static final long REQUEST_TIME_TO_LIVE = 86400000;
	
	public static enum GeneralType {
		ORDER, ORDERBULK, ORDERALL, INSTRUMENT, EXECUTION, POSITION;
	}
	
	public static enum Topic {
		ORDERBOOKL2, TRADE, //non-authenticated
		POSITION, WALLET, ORDER, MARGIN, EXECUTION; //authenticated 
	};
	
	public static Map <String, Topic> stringToTopic = new HashMap<>();
	public static EnumMap <Topic, TopicContainer> containers = new EnumMap<>(Topic.class);
	
	static {
		stringToTopic.put("orderBookL2", Topic.ORDERBOOKL2);
		stringToTopic.put("trade", Topic.TRADE);
		stringToTopic.put("wallet", Topic.WALLET);
		stringToTopic.put("execution", Topic.EXECUTION);
		stringToTopic.put("margin", Topic.MARGIN);
		stringToTopic.put("position", Topic.POSITION);
		stringToTopic.put("order", Topic.ORDER);
		
//		non-authenticated
		containers.put(Topic.ORDERBOOKL2, new TopicContainer("orderBookL2", false, new TypeToken<MessageGeneric<UnitData>>() {}.getType(), UnitData.class));
		containers.put(Topic.TRADE, new TopicContainer("trade", false, new TypeToken<MessageGeneric<UnitTrade>>() {}.getType(), UnitTrade.class));
//		authenticated
		containers.put(Topic.WALLET, new TopicContainer("wallet", true, new TypeToken<MessageGeneric<UnitWallet>>() {}.getType(), UnitWallet.class));
		containers.put(Topic.EXECUTION, new TopicContainer("execution", true, new TypeToken<MessageGeneric<UnitExecution>>() {}.getType(), UnitExecution.class));
		containers.put(Topic.MARGIN, new TopicContainer("margin", true, new TypeToken<MessageGeneric<UnitMargin>>() {}.getType(), UnitMargin.class));
		containers.put(Topic.POSITION, new TopicContainer("position", true, new TypeToken<MessageGeneric<UnitPosition>>() {}.getType(), UnitPosition.class));
		containers.put(Topic.ORDER, new TopicContainer("order", true, new TypeToken<MessageGeneric<UnitOrder>>() {}.getType(), UnitOrder.class));
		
	}
	
	public static EnumMap<GeneralType, String> subPaths = new EnumMap<GeneralType, String>(GeneralType.class);
	static {
		subPaths.put(GeneralType.ORDER, "/api/v1/order");
		subPaths.put(GeneralType.ORDERBULK, "/api/v1/order/bulk");		
		subPaths.put(GeneralType.INSTRUMENT, "/api/v1/instrument");
		subPaths.put(GeneralType.EXECUTION, "/api/v1/execution");
		subPaths.put(GeneralType.POSITION, "/api/v1/position");
		// for canceling orders only
		subPaths.put(GeneralType.ORDERALL, "/api/v1/order/all"); 
	}
	
	public static enum Method {
		GET, PUT, POST, DELETE;
	}

	public static EnumMap<Method, String> methods = new EnumMap<Method, String>(Method.class);
	static {
		methods.put(Method.GET, "GET");
		methods.put(Method.POST, "POST");
		methods.put(Method.PUT, "PUT");
		methods.put(Method.DELETE, "DELETE");
	}
	

//	public static String getDateTwentyFourHoursAgoAsUrlEncodedString() {
//		Calendar calendar = Calendar.getInstance();
//		calendar.add(Calendar.DAY_OF_WEEK, -1);
//		Date date = calendar.getTime();
//		String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
//		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
//		String s = sdf.format(date);
////		System.out.println(s);
//		StringBuilder sb = new StringBuilder();
//		sb.append(s.substring(0, 10));
//		sb.append("T");
//		sb.append(s.substring(11, 13));
//		sb.append("%3A");
//		sb.append(s.substring(14, 16));
//		sb.append("%3A");
//		sb.append(s.substring(17));
//		sb.append("Z");
//		String z = sb.toString();
//		return z;
//	}
	
	public static String getDateTwentyFourHoursAgoAsUrlEncodedString0() {
		long longTimeAgo = System.currentTimeMillis() - 86400000;
		String s = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(longTimeAgo).atZone(ZoneOffset.UTC)) + "Z";
		return s;
	}
	
	public static long transactTimeToLong(String moment) {
		Calendar calendar = DatatypeConverter.parseDateTime(moment);
		Date date = calendar.getTime();
		long time = date.getTime();
		return time;
	}
	
	public static String longToTransactTime(long moment) {
		ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(moment),
				ZoneId.systemDefault());
		String time = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "Z";
		return time;
	}
	
	public static long getMomentAndTimeToLive() {
		return System.currentTimeMillis() + ConnectorUtils.REQUEST_TIME_TO_LIVE;
	}

	public static String hash256(String data) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(data.getBytes());
		return bytesToHex(md.digest());
	}

	public static String bytesToHex(byte[] bytes) {
		StringBuffer result = new StringBuffer();
		for (byte byt : bytes)
			result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
		return result.toString();
	}

	public static String createMessageBody(String method, String path, String data, long moment) {
		String messageBody = method + path + Long.toString(moment) + data;
		return messageBody;
	}

	public static String generateSignature(String apiSecret, String messageBody) {
		try {
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256");
			sha256_HMAC.init(secretKey);
			byte[] hash = sha256_HMAC.doFinal(messageBody.getBytes());
			String check = Hex.encodeHexString(hash);
			return check;
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String isolateSymbol(String alias) {
		char[] symbData = alias.toCharArray();
		StringBuilder sb = new StringBuilder();
		sb.append("");

		for (int i = 0; i < symbData.length
				&& (symbData[i] >= 'A' && symbData[i] <= 'Z' || symbData[i] >= '0' && symbData[i] <= '9');) {
			sb.append(symbData[i++]);
		}
		return sb.toString();
	}
	
	public static String processRateLimitHeaders(Map<String, List<String>> map){
		int rateLimit = Integer.parseInt(map.get("X-RateLimit-Limit").get(0));
		int rateLimitRemaining = Integer.parseInt(map.get("X-RateLimit-Remaining").get(0));
		int ratio = 100 * rateLimitRemaining / rateLimit;
		System.out.println(ratio);
		if (ratio <= 10){
			return Integer.toString(ratio);
		}
		return null;
	}
}
