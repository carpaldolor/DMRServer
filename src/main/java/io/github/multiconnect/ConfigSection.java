package io.github.multiconnect;

import java.util.*;

public class ConfigSection {
	public static String[] INT_FIELDS = new String[] { "RemotePort" };

	public static String LOCAL_PORT = "LocalPort";
	public static String REPEATER_ID = "repeaterid";
	public static String REMOTE_IP = "RemoteAddress";
	public static String REMOTE_PORT = "RemotePort";
	public static String PASSWORD = "Password";
	public static String TGLIST = "tglist";

	public static HashMap<String, Integer> LENGTH_CHECK = new HashMap<String, Integer>();
	static {
		LENGTH_CHECK.put("callsign", 8);
		LENGTH_CHECK.put("txfreq", 9);
		LENGTH_CHECK.put("rxfreq", 9);
		LENGTH_CHECK.put("txpower", 2);
		LENGTH_CHECK.put("colorcode", 2);
		LENGTH_CHECK.put("height", 3);
		LENGTH_CHECK.put("lat", 8);
		LENGTH_CHECK.put("lon", 9);
		LENGTH_CHECK.put("location", 20);
		LENGTH_CHECK.put("description", 19);
		LENGTH_CHECK.put("mode", 1);
		LENGTH_CHECK.put("url", 124);
		LENGTH_CHECK.put("softwareid", 40);
		LENGTH_CHECK.put("packageid", 40);
	}
	public static Set<String> OTHER_REQUIRED = Set.of(REPEATER_ID, REMOTE_IP, REMOTE_PORT, PASSWORD, TGLIST);

	public static Set<String> PAD_ZERO = Set.of("lat", "lon", "txfreq","rxfreq");
	
	
	String name;

	HashMap<String, String> params;

	ConfigSection(String name) {
		this.name = name;
		this.params = new HashMap<String, String>();
	}

	public String getName() {
		return name;
	}

	public boolean isMain() {
		return name.equals(ServiceConfig.MAIN_SECTION);
	}

	public boolean isEnabled() {
		String val = params.get("Enable");
		return val == null || val.equals("1");
	}

	public boolean checkVal( String field, String value) {
		String val = params.get("Breakin");
		return val != null && val.equals(value);
	}
	
	public HashMap<String, String> getParams() {
		return params;
	}

	public String getParam(String name) {
		return params.get(name);
	}

	public int getIntParam(String name) {
		String val = params.get(name);
		return Integer.parseInt(val);
	}

	/*
	 * Merge in the default values form main
	 */
	public void mergeMain(ConfigSection main) {
		HashMap<String, String> mainParams = main.getParams();
		for (String key : mainParams.keySet()) {
			if (!params.containsKey(key))
				params.put(key, mainParams.get(key));
		}
	}

	public String checkForMissing() {
		for (String key : LENGTH_CHECK.keySet()) {
			if (!params.containsKey(key)) {
				return "missing field: " + key;
			}
		}
		for (String key : OTHER_REQUIRED) {
			if (!params.containsKey(key)) {
				return "missing field: " + key;
			}
		}
		return null;
	}

	public void appendString(StringBuffer sb, String field) {
		String val = params.get(field);
		sb.append(val);
	}

	public void appendInt(StringBuffer sb, String field) {
		String val = params.get(field);
		int i = Integer.parseInt(val);

		sb.append((char) ((i >> 24) & 0xff));
		sb.append((char) ((i >> 16) & 0xff));
		sb.append((char) ((i >> 8) & 0xff));
		sb.append((char) ((i) & 0xff));
	}

	public String getMessage() {
		StringBuffer sb = new StringBuffer();
		appendString(sb, "callsign");
		appendString(sb, "rxfreq");
		appendString(sb, "txfreq");
		appendString(sb, "txpower");
		appendString(sb, "colorcode");
		appendString(sb, "lat");
		appendString(sb, "lon");
		appendString(sb, "height");
		appendString(sb, "location");
		appendString(sb, "description");

		//mode
		appendString(sb, "mode");
		
		appendString(sb, "url");
		appendString(sb, "softwareid");
		appendString(sb, "packageid");
		return sb.toString();
	}

	public String formatVal(String field, String val, int len) {
		boolean pad_zero = PAD_ZERO.contains(field) ;
		if (val.length() >= len) {
			return val.substring(0, len);
		} else {
			// pad
			StringBuffer sb = new StringBuffer(len);
			sb.append(val);
			for (int i = 0; i < (len - val.length()); i++) {
				if(pad_zero)
					sb.append("0");
				else
					sb.append(" ");
			}
			return sb.toString();
		}
	}

	public void add(String name, String val) {
		Integer len = LENGTH_CHECK.get(name);
		if (len != null) {
			val = formatVal(name, val, len);
		}
		params.put(name, val);
	}

	public String toString() {
		return params.toString();
	}

}
