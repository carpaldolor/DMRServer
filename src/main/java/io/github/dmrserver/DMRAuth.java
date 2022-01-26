package io.github.dmrserver;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.HashMap;

/*
*
* auth.properties 
*
* 1234567=xxxxxxxx     //standard full DMRid
* 123456701=xxxxxxxx   //standard full DMRid+essid '01'
* 1234567*=xxxxxxxx    //standard full DMRid matching pattern including all essids
* *=xxxxxxxx           //standard full matching pattern anyone
*
*/
public class DMRAuth {
	public static Logger logger = Logger.getLogger() ;
	
	private static DMRAuth _instance = null;
	public static String filename = "auth.properties";

	HashMap<Integer, String> exactMap = new HashMap<>();
	HashMap<String, String> patternMap = new HashMap<>();
	boolean hasWildcards = false;

	private DMRAuth() {
		load();
	}

	public static synchronized DMRAuth getInstance() {
		if (_instance == null) {
			_instance = new DMRAuth();
			_instance.load();
		}
		return _instance;
	}

	public String getAuthEntry(Integer key) {
		String auth = null;
		if (exactMap.size() > 0)
			auth = exactMap.get(key);
		if (auth == null) {
			if (patternMap.size() > 0) {
				String skey = key.toString();
				for (String patternKey : patternMap.keySet()) {
					if (skey.startsWith(patternKey)) {
						return patternMap.get(patternKey);
					}
				}
			}
		}
		return auth;
	}

	public boolean checkAuth(Integer key, int salt, byte[] bar, int pos) {
		try {
			String auth = getAuthEntry(key);
			// System.out.println( "checkAuth auth: "+auth) ;
			if ((salt == -1) || (auth == null) || ((bar.length - pos) < 32))
				return false;

			byte[] authBytes = auth.getBytes();

			byte[] sha = new byte[authBytes.length + 4];
			DMRDecode.intToBytes(salt, sha, 0);
			System.arraycopy(authBytes, 0, sha, 4, authBytes.length);
			if( logger.log(2) ) logger.log("checkAuth sha bytes: " + DMRDecode.hex(sha, 0, sha.length));

			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(sha);

			if( logger.log(2) ) logger.log("checkAuth hash bytes: " + DMRDecode.hex(hash, 0, hash.length));

			for (int i = 0; i < 32; i++)
				if (hash[i] != bar[i + pos])
					return false;
			return true;
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
		return false;
	}

	public void load() {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
			String line = null;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (!line.startsWith("#") && line.length()>0) {
					String[] sar = line.split("=");
					if (sar.length == 2) {
						if (sar[0].endsWith("*")) {
							patternMap.put(sar[0].substring(0, sar[0].length() - 1), sar[1]);
						} else {
							Integer i = Integer.parseInt(sar[0]);
							exactMap.put(i, sar[1]);
						}
					}
				}
			}
			br.close();
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}
}
