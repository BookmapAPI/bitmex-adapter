package bitmexAdapter;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.map.HashedMap;

import com.ibm.icu.text.SimpleDateFormat;

public class ConnectorUtils {
	public static final String bitmex_Wss = "wss://www.bitmex.com/realtime";
	public static final String bitmex_restApi ="https://www.bitmex.com";
	public static final String bitmex_restActiveInstrUrl = "https://www.bitmex.com/api/v1/instrument/active";
	
	public static final String testnet_Wss = "wss://testnet.bitmex.com/realtime";
	public static final String testnet_restApi = "https://testnet.bitmex.com";
//	public static final String testnet_restApi = "https://testnet.bitmex.com";
	public static final String testnet_restActiveInstrUrl = "https://testnet.bitmex.com/api/v1/instrument/active";
	
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
