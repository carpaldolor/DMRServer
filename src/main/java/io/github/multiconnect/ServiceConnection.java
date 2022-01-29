package io.github.multiconnect;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import io.github.dmrserver.DMRAuth;
import io.github.dmrserver.DMRDecode;
import io.github.dmrserver.DMRServer;
import io.github.dmrserver.Encryption;
import io.github.dmrserver.Logger;

public class ServiceConnection implements Runnable {
	public static Logger logger = Logger.getLogger();

	boolean isAuthenticated = false;

	long lastHeard = 0;

	ConnectionManager conMan = null;
	ConfigSection config;
	DatagramSocket remoteSocket;
	InetAddress remoteAddress;
	int remotePort;

	boolean allowBreakin = false;

	int repeaterId = 0;

	Encryption serverEncryption = null;
	Encryption clientEncryption = null;

	public ServiceConnection(ConfigSection config) {
		this.config = config;
		String val = config.getParam(ConfigSection.REPEATER_ID);
		repeaterId = Integer.parseInt(val);

		try {
			remoteAddress = InetAddress.getByName(config.getParam(ConfigSection.REMOTE_IP));
		} catch (UnknownHostException ex) {
			Logger.handleException(ex);
		}
		remotePort = config.getIntParam(ConfigSection.REMOTE_PORT);
		markTime();

		allowBreakin = config.checkVal("Breakin", "1");

		String serverKey = config.getParam(ConfigSection.SERVER_KEY);
		String clientKey = config.getParam(ConfigSection.CLIENT_KEY);
		if (serverKey != null && clientKey != null) {
			serverEncryption = new Encryption(serverKey.trim());
			clientEncryption = new Encryption(clientKey.trim());
		}
	}

	public boolean isSecure() {
		return clientEncryption != null;
	}

	public Encryption getServerEncryption() {
		return serverEncryption;
	}

	public Encryption getClientEncryption() {
		return clientEncryption;
	}

	public String getName() {
		return config.getName();
	}

	public boolean allowBreaking() {
		return allowBreakin;
	}

	public HashMap<Integer, ServiceConnection> getRoutes() {
		HashMap<Integer, ServiceConnection> ret = new HashMap<Integer, ServiceConnection>();
		String val = config.getParam(ConfigSection.TGLIST);
		String[] sar = val.split(",");
		for (int i = 0; i < sar.length; i++) {
			try {
				int key;
				if (sar[i].trim().equals("*"))
					key = 0;
				else
					key = Integer.parseInt(sar[i].trim());
				ret.put(key, this);
				if (key == 0)
					logger.log(config.getName() + " adding DEFAULT");
				else
					logger.log(config.getName() + " adding TG " + key);
			} catch (Exception ex) {
			}
		}
		return ret;
	}

	public void markTime() {
		lastHeard = System.currentTimeMillis();
	}

	public void setConnectionManager(ConnectionManager conMan) {
		this.conMan = conMan;
	}

	public void start() {
		try {
			remoteSocket = new DatagramSocket();

			Thread th = new Thread(this);
			th.setName(config.getName());
			th.start();
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}

	public void sendLoginInit(DatagramPacket packet) {
		byte[] bar = packet.getData();
		DMRServer.addToBytes(bar, 0, "RPTL");
		DMRDecode.intToBytes(repeaterId, bar, 4);
		packet.setLength(8);
		send(packet, false);
	}

	public void sendLoginAuth(DatagramPacket packet, int salt) throws NoSuchAlgorithmException {
		byte[] bar = packet.getData();
		DMRServer.addToBytes(bar, 0, "RPTK");
		DMRDecode.intToBytes(repeaterId, bar, 4);

		String auth = config.getParam(ConfigSection.PASSWORD);
		byte[] hash = DMRAuth.getHash(auth, salt);
		System.arraycopy(hash, 0, bar, 8, hash.length);
		packet.setLength(8 + hash.length);
		send(packet, false);
	}

	public void sendLoginConfig(DatagramPacket packet) {
		byte[] bar = packet.getData();
		DMRServer.addToBytes(bar, 0, "RPTC");
		DMRDecode.intToBytes(repeaterId, bar, 4);
		String confMsg = config.getMessage();
		DMRServer.addToBytes(bar, 8, confMsg);
		packet.setLength(8 + confMsg.length());
		send(packet, false);
	}

	/**
	 * Send a packet to the remote server
	 */
	public void send(DatagramPacket packet, boolean isData) {
		try {
			if (isSecure()) {
				if (isData) {
					clientEncryption.encryptPacket(packet);
				} else {
					serverEncryption.encryptPacket(packet);
				}
			}
			packet.setAddress(remoteAddress);
			packet.setPort(remotePort);
			remoteSocket.send(packet);
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}

	public void handleDataPacket(DatagramPacket packet, DMRDecode decode) {
		logger.log(config.getName() + " Data: " + decode);
		send(packet, true);
	}

	public DatagramPacket waitForResponse(DatagramPacket packet) {
		try {
			byte[] bar = new byte[2048];
			packet.setData(bar);
			remoteSocket.setSoTimeout(2000);
			remoteSocket.receive(packet);
			if (isSecure()) {
				serverEncryption.decryptPacket(packet);
			}
			return packet;
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
		return null;
	}

	public int getAckSalt(DatagramPacket packet) {
		int ret = -1;
		byte[] bar = packet.getData();
		String tag = new String(bar, 0, 6);
		if (tag.equals("RPTACK")) {
			ret = DMRDecode.ti(bar, 6, 4);
		}
		return ret;
	}

	public boolean isAck(DatagramPacket packet) {
		byte[] bar = packet.getData();
		String tag = new String(bar, 0, 6);
		return tag.equals("RPTACK");
	}

	public boolean login(DatagramPacket packet) {
		logger.log("Starting login to Service: " + config.getName());

		long waitTime = 1000;
		while (!isAuthenticated) {

			try {
				// send init
				sendLoginInit(packet);
				if ((packet = waitForResponse(packet)) != null) {

					int salt = getAckSalt(packet);
					if (salt == -1)
						return false;

					// send auth
					sendLoginAuth(packet, salt);
					if ((packet = waitForResponse(packet)) == null)
						return false;
					if (!isAck(packet))
						return false;

					// send config
					sendLoginConfig(packet);
					if ((packet = waitForResponse(packet)) == null)
						return false;
					if (!isAck(packet))
						return false;

					isAuthenticated = true;
				}
			} catch (Exception ex) {
				Logger.handleException(ex);
			}

			// wait before trying again
			if (!isAuthenticated) {
				try {
					logger.log(getName() + " Server is not responding.");
					Thread.sleep(waitTime);
					waitTime = waitTime * 2L;
					if (waitTime > 300000L)
						waitTime = 300000L;
				} catch (Exception ex) {
				}
			}

		}

		return isAuthenticated;
	}

	/**
	 * Called by ServiceManager Timer
	 */
	public void handlePing() {
		logger.log(config.getName() + " RPTPING");
		try {
			byte[] bar = new byte[11];
			DMRServer.addToBytes(bar, 0, "RPTPING");
			DMRDecode.intToBytes(repeaterId, bar, 7);
			DatagramPacket packet = new DatagramPacket(bar, 11);
			send(packet, false);
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}

	public void handlePong() {
		logger.log(config.getName() + " MSTPONG");
		markTime();
	}

	public void handleNAK() {
		logger.log(config.getName() + " MSTNAK  Connect is reset");
		isAuthenticated = false;
		byte[] bar = new byte[2048];
		DatagramPacket packet = new DatagramPacket(bar, bar.length);
		login(packet);
	}

	public void handlePacket(DatagramPacket packet) throws IOException {

		byte[] bar = packet.getData();
		int len = packet.getLength();

		String tag = new String(bar, 0, 4);
		if (logger.log(2))
			logger.log("handlePacket() " + tag + " len: " + packet.getLength());

		if (isSecure()) {
			if (tag.equals("DMRD")) {
				clientEncryption.decryptPacket(packet);
			} else {
				serverEncryption.decryptPacket(packet);
			}
		}

		if (logger.log(2))
			logger.log(config.getName() + " handlePacket() " + tag + " " + packet.getAddress().getHostAddress() + ":"
					+ packet.getPort());

		if (tag.equals("MSTP")) {
			handlePong();
		} else if (tag.equals("MSTN")) {
			handleNAK();
		} else if (conMan != null && tag.equals("DMRD")) {
			conMan.handleOutgoing(packet, this);
		}

	}

	public void run() {
		byte[] bar = new byte[2048];
		DatagramPacket packet = new DatagramPacket(bar, bar.length);
		try {
			InetAddress addr = InetAddress.getByName(config.getParam(ConfigSection.REMOTE_IP));
			packet.setAddress(addr);
			int port = config.getIntParam(ConfigSection.LOCAL_PORT);
			packet.setPort(port);

			isAuthenticated = login(packet);
			logger.log("login status: " + isAuthenticated);

			packet = new DatagramPacket(bar, bar.length);
		} catch (Exception ex) {
			Logger.handleException(ex);
		}

		while (true) {
			try {
				remoteSocket.setSoTimeout(0);
				packet.setAddress(remoteAddress);
				packet.setPort(remotePort);
				packet.setData(bar);
				remoteSocket.receive(packet);
				if (isAuthenticated) {
					handlePacket(packet);
				}
			} catch (Exception ex) {
				Logger.handleException(ex);
			}
		}
	}

}
