package io.github.multiconnect;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public class ServiceConfig {
	public static final String MAIN_SECTION = "Main";

	String filename;
	HashMap<String, ConfigSection> sectionMap;

	public ServiceConfig(String filename) {
		this.filename = filename;
		this.sectionMap = new HashMap<String, ConfigSection>();
	}

	public ConfigSection getSection(String name) {
		return sectionMap.get(name);
	}
	
	public ConfigSection getMain() {
		return sectionMap.get(MAIN_SECTION);
	}
	
	public HashMap<String, ConfigSection> getSectionMap() {
		return sectionMap;
	}

	public void exitOnError(String message) {
		System.err.println("ServiceConfig:Error reading file: " + filename);
		System.err.println(message);
		System.exit(-1);
	}

	public boolean read() {
		boolean mainWasFound = false;
		boolean ret = false;
		int num = 1;
		ConfigSection currSection = null;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
			String line = null;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (!line.startsWith("#") && line.length() > 0) {
					if (line.startsWith("[")) {
						// new section
						int pos = line.indexOf(']');
						if (pos == -1) {
							exitOnError("No ']' char found in section header line " + num + ":" + line);
						}
						String name = line.substring(1, pos);
						if (!mainWasFound) {
							if (!name.equals(MAIN_SECTION)) {
								exitOnError("[Main] section must be the first section " + num + ":" + line);
							}
							mainWasFound = true;
						}
						currSection = new ConfigSection(name);
						sectionMap.put(name, currSection);
					} else {
						String[] sar = line.split("=");
						if (sar.length != 2) {
							exitOnError("Invalid config, lines must contain: name=value on line " + num + ":" + line);
						}
						if (currSection == null) {
							exitOnError("Invalid config, no [header] line found near " + num + ":" + line);
						}
						currSection.add(sar[0], sar[1]);
					}
				}
				num++;
			}
			if (!mainWasFound) {
				exitOnError("No valid config data found, maybe file is empty " + num + ":" + line);
			}

			ConfigSection main = sectionMap.get(MAIN_SECTION);
			for (String key : sectionMap.keySet()) {
				if (!key.equals(MAIN_SECTION)) {
					ConfigSection cs = sectionMap.get(key);
					cs.mergeMain(main);
					String msg = cs.checkForMissing();
					if (msg != null) {
						exitOnError("Invalid config section: " + key + " " + msg);
					}
					msg = cs.getMessage();
					//System.out.println(key + " len:" + msg.length() + "\nmsg:" + msg);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return ret;
	}

	public String toString() {
		return sectionMap.toString();
	}
}
