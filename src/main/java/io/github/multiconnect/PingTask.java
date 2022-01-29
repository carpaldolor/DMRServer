package io.github.multiconnect;

import java.util.TimerTask;

import io.github.dmrserver.Logger;

public class PingTask extends TimerTask {
	ConnectionManager conMan;

	public PingTask(ConnectionManager conMan) {
		this.conMan = conMan;
	}

	@Override
	public void run() {
		try {
			conMan.pingAll();
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}

}
