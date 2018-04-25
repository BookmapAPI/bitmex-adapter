package bitmexAdapter;

public class QueryArgument {
	
	public static enum theme {
		instrument, trade;
	}
	
//	private SimpleEntry<String, String> entry;
	private String theme;
	private String symbol;
	
	public QueryArgument(String theme, String symbol) {
		super();
		this.theme = theme;
		this.symbol = symbol;
//		this.entry = new SimpleEntry<String, String>(theme, symbol);
	}

	public String getTheme() {
		return theme;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setTheme(String theme) {
		this.theme = theme;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

//	public SimpleEntry<String, String> getEntry() {
//		return entry;
//	}

	
	
	
	

}
