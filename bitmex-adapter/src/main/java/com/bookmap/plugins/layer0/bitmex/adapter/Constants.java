package com.bookmap.plugins.layer0.bitmex.adapter;

public class Constants {

	public static final String version = "v.0.7.0.20";
	public static final String bitmex_Wss = "wss://www.bitmex.com/realtime";
	public static final String bitmex_restApi = "https://www.bitmex.com";

	public static final String testnet_Wss = "wss://testnet.bitmex.com/realtime";
	public static final String testnet_restApi = "https://testnet.bitmex.com";

	public static final String activeInstrSubpath = "/api/v1/instrument/active";
	
	public static final String realHistoricalServerUrl = "http://bitmex-real.hist.bookmap.com/";
	public static final String demoHistoricalServerUrl = "http://bitmex-demo.hist.bookmap.com/";
	
	public static final String user_agent = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.130 Safari/537.36";

	/**
	 * Repeating position request is done for keeping persistent connection.
	 */
	public static final int positionRequestDelaySeconds = 150;
	public static final String programmaticName = "EXT:" + "com.bookmap.plugins.layer0.bitmex.Provider";
	public static final String rateLimitHeaderName = "X-RateLimit-Limit";
	public static final String rateLimitRemainingHeaderName = "X-RateLimit-Remaining";
	public static final int maxSize = 1_000_000_000;
}
