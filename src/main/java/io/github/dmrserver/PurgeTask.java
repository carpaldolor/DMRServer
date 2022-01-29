package io.github.dmrserver;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimerTask;

public class PurgeTask extends TimerTask {
	public static Logger logger = Logger.getLogger();
	
	DMRServer server;

	public PurgeTask(DMRServer server) {
		this.server = server;
	}

	@Override
	public void run() {
		int cnt =0;
		try {
			Set<DMRSessionKey> keySet = new HashSet<DMRSessionKey>();
			keySet.addAll(server.getSessionMap().keySet());
			for (DMRSessionKey key : keySet) {
				DMRSession session = server.getSessionMap().get(key);
				if (session != null && session.isExpired()) {
					server.removeSession(key);
					cnt++ ;
				}
			}
			if(cnt>0) logger.log("PurgeTask cnt: "+cnt) ;

		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}

}
