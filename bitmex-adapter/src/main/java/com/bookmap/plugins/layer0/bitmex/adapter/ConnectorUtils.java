package com.bookmap.plugins.layer0.bitmex.adapter;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.http.Header;

import com.google.gson.reflect.TypeToken;

import velox.api.layer1.data.OrderDuration;

public class ConnectorUtils {

	public static final String testnet_Note = "Please note, you are connected to testnet,"
			+ " so the data you see\ndoes not match the actual data in the live environment."
			+ "\nPlease uncheck \"This is a demo account\" to connect to the live data";

	public static final long REQUEST_TIME_TO_LIVE = TimeUnit.SECONDS.convert(1, TimeUnit.HOURS);// 1 hour

	public static enum WebSocketOperation {
		AUTHKEY, SUBSCRIBE, UNSUBSCRIBE;
	}

	public static Map<WebSocketOperation, String> webSocketOperationToString = new EnumMap<>(WebSocketOperation.class);

	static {
		webSocketOperationToString.put(WebSocketOperation.AUTHKEY, "authKey");
		webSocketOperationToString.put(WebSocketOperation.SUBSCRIBE, "subscribe");
		webSocketOperationToString.put(WebSocketOperation.UNSUBSCRIBE, "unsubscribe");
	}

	public static enum GeneralType {
		ORDER, ORDERBULK, ORDERALL, INSTRUMENT, EXECUTION, POSITION, ACTIVE_INSTRUMENTS, BLANK;
	}
	
	public static final EnumSet<OrderDuration> bitmexOrderDurations = EnumSet.of(
			OrderDuration.DAY,
			OrderDuration.FOK,
			OrderDuration.GTC,
			OrderDuration.IOC,
			OrderDuration.GTC_PO);
	
	public static final BidiMap<OrderDuration, String> bitmexOrderDurationsValues = new DualHashBidiMap<>();
	//**BitMEX supports the GTC_PO duration but it is set with the 'execInst=ParticipateDoNotInitiate' query field
	//and not with the duration itself
	static {
		bitmexOrderDurationsValues.put(OrderDuration.DAY, "Day");
		bitmexOrderDurationsValues.put(OrderDuration.GTC, "GoodTillCancel");
		bitmexOrderDurationsValues.put(OrderDuration.IOC, "ImmediateOrCancel");
		bitmexOrderDurationsValues.put(OrderDuration.FOK, "FillOrKill");
	}
	
	public static final String GtcPoExecutionalInstruction = "ParticipateDoNotInitiate";


	public static enum Topic {
		ORDERBOOKL2, TRADE, // non-authenticated
		POSITION, WALLET, ORDER, MARGIN, EXECUTION; // authenticated
	};

	public static Map<String, Topic> stringToTopic = new HashMap<>();
	public static Map<Topic, TopicContainer> containers = new EnumMap<>(Topic.class);

	static {
		stringToTopic.put("orderBookL2", Topic.ORDERBOOKL2);
		stringToTopic.put("trade", Topic.TRADE);
		stringToTopic.put("wallet", Topic.WALLET);
		stringToTopic.put("execution", Topic.EXECUTION);
		stringToTopic.put("margin", Topic.MARGIN);
		stringToTopic.put("position", Topic.POSITION);
		stringToTopic.put("order", Topic.ORDER);

		// non-authenticated
		containers.put(Topic.ORDERBOOKL2,
				new TopicContainer("orderBookL2", false, new TypeToken<MessageGeneric<UnitData>>() {
				}.getType(), UnitData.class));
		containers.put(Topic.TRADE, new TopicContainer("trade", false, new TypeToken<MessageGeneric<UnitTrade>>() {
		}.getType(), UnitTrade.class));
		// authenticated
		containers.put(Topic.WALLET, new TopicContainer("wallet", true, new TypeToken<MessageGeneric<UnitWallet>>() {
		}.getType(), UnitWallet.class));
		containers.put(Topic.EXECUTION,
				new TopicContainer("execution", true, new TypeToken<MessageGeneric<UnitExecution>>() {
				}.getType(), UnitExecution.class));
		containers.put(Topic.MARGIN, new TopicContainer("margin", true, new TypeToken<MessageGeneric<UnitMargin>>() {
		}.getType(), UnitMargin.class));
		containers.put(Topic.POSITION,
				new TopicContainer("position", true, new TypeToken<MessageGeneric<UnitPosition>>() {
				}.getType(), UnitPosition.class));
		containers.put(Topic.ORDER, new TopicContainer("order", true, new TypeToken<MessageGeneric<UnitOrder>>() {
		}.getType(), UnitOrder.class));

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
		subPaths.put(GeneralType.ACTIVE_INSTRUMENTS, Constants.activeInstrSubpath);
		subPaths.put(GeneralType.BLANK, "");
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

	public static String getDateTwentyFourHoursAgoAsUrlEncodedString() {
		long longTimeAgo = System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
		String s = DateTimeFormatter.ISO_LOCAL_DATE_TIME
				.format(Instant.ofEpochMilli(longTimeAgo).atZone(ZoneOffset.UTC)) + "Z";
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
		return Instant.now().getEpochSecond() + REQUEST_TIME_TO_LIVE;
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
		if (data == null) {
			data = "";
		}
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

    public static Header getHeader(Header[] headers, String name) {
        Header rateLimitHeader = Arrays.stream(headers)
                .filter(header -> header.getName().toLowerCase().equals(name.toLowerCase()))
                .findAny()
                .orElse(null);
        return rateLimitHeader;
    }

	public static String[] getAuthenticatedTopicsList() {
		String[] array = { ConnectorUtils.containers.get(ConnectorUtils.Topic.POSITION).name,
				ConnectorUtils.containers.get(ConnectorUtils.Topic.WALLET).name,
				ConnectorUtils.containers.get(ConnectorUtils.Topic.MARGIN).name,
				ConnectorUtils.containers.get(ConnectorUtils.Topic.EXECUTION).name,
				ConnectorUtils.containers.get(ConnectorUtils.Topic.ORDER).name };
		return array;
	}

	public static String[] getNonAuthenticatedTopicsList() {
		String[] array = { ConnectorUtils.containers.get(ConnectorUtils.Topic.ORDERBOOKL2).name,
				ConnectorUtils.containers.get(ConnectorUtils.Topic.TRADE).name };
		return array;
	}
	
	public static Integer getTimeoutFromErrorMessage(String errorMessage) {
        Pattern patcher = Pattern.compile("Rate limit exceeded, retry in \\d+ seconds.");
        Matcher matcher = patcher.matcher(errorMessage);

        if (matcher.find()) {
            String exp = matcher.group();
            patcher = Pattern.compile("\\d+");
            matcher = patcher.matcher(exp);

            if (matcher.find()) {
                String s = matcher.group();
                return Integer.valueOf(s);
            }
        }
        return null;
    }

}
