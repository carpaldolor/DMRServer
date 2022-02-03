package io.github.multiconnect;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Timer;

import io.github.dmrserver.DMRDecode;
import io.github.dmrserver.DMRServer;
import io.github.dmrserver.DMRSession;
import io.github.dmrserver.DMRSessionKey;
import io.github.dmrserver.Logger;

public class ConnectionManager implements Runnable {
	public static Logger logger = Logger.getLogger();

	public static long IDLE_TALKER_TIMEOUT = 1000L;
	public static long CHANNEL_RESERVE_TIMEOUT = 15000L;

	public static Integer DEFAULT_ROUTE = 0;

	DatagramSocket hotspotSocket;
	ServiceConfig config;

	DMRSession clientSession = null;
	DMRServer server = null;
	Timer pingTimer;

	HashMap<String, ServiceConnection> conMap = new HashMap<String, ServiceConnection>();
	HashMap<Integer, ServiceConnection> routeMap = new HashMap<Integer, ServiceConnection>();
	HashMap<Integer, ServiceConnection> selectorMap = new HashMap<Integer, ServiceConnection>();

	int currentTalker = 0;
	long lastTalkFromNetwork = 0;
	long lastTalkFromReservedChannel = 0;
	int lastDestId = 0;

	long lastPingMsg = 0L;
	long startTalk = 0;

	DecimalFormat df = new DecimalFormat();

	public ConnectionManager(ServiceConfig config) {
		this.config = config;
		this.server = new DMRServer();

		df.setMaximumFractionDigits(1);

		lastPingMsg = System.currentTimeMillis();

	}

	public void markChannelReserve(DMRDecode decode) {
		if (logger.log(2))
			logger.log("###### mark channel reserve " + lastDestId);
		lastTalkFromReservedChannel = System.currentTimeMillis();
	}

	public void markLastTalk() {
		lastTalkFromNetwork = System.currentTimeMillis();
	}

	/**
	 * Called by the Timer Task
	 */
	public void pingAll() {
		if  ((System.currentTimeMillis() - lastPingMsg) > 59000L) {
			StringBuffer sb = new StringBuffer();
			sb.append("Service [ping:pong] ");
			String sep = "";
			for (String key : conMap.keySet()) {
				ServiceConnection con = conMap.get(key);
				sb.append(sep);
				sb.append(con.getName());
				sb.append(" ");
				sb.append(con.getPingPong());
				con.pingPongReset();
				sep = ", ";
			}
			lastPingMsg = System.currentTimeMillis();
			logger.log(sb.toString());
		}
		for (String key : conMap.keySet()) {
			ServiceConnection con = conMap.get(key);
			con.handlePing();
		}
	}

	public void start() {
		try {
			String localPort = config.getSection(ServiceConfig.MAIN_SECTION).getParam(ConfigSection.LOCAL_PORT);
			int port = 62031;
			if (localPort != null) {
				port = Integer.parseInt(localPort);
			}
			hotspotSocket = new DatagramSocket(port);

			Thread th = new Thread(this);
			th.setName("ClientListener-" + port);
			th.start();

			long pingTime = (long) config.getMain().getIntParam(ConfigSection.PING_TIME, 10);

			pingTimer = new Timer(true);
			// 10 sec ping cycle
			pingTimer.schedule(new PingTask(this), pingTime * 1000L, pingTime * 1000L);

			IDLE_TALKER_TIMEOUT = 1000L * (long) config.getMain().getIntParam(ConfigSection.IDLE_TALKER_TIMEOUT, 1);
			CHANNEL_RESERVE_TIMEOUT = 1000L
					* (long) config.getMain().getIntParam(ConfigSection.CHANNEL_RESERVE_TIMEOUT, 15);

			activateServices();
			System.out.println("Opening a listener on port: " + port + "  ping time: " + pingTime + "s");
		} catch (Exception ex) {
			Logger.handleException(ex);
		}

	}

	public void activateServices() {
		HashMap<String, ConfigSection> map = config.getSectionMap();
		for (String key : map.keySet()) {
			ConfigSection section = map.get(key);
			if (!section.isMain() && section.isEnabled()) {
				System.out.println("Starting Section : " + key);
				ServiceConnection con = new ServiceConnection(section);
				con.setConnectionManager(this);
				conMap.put(key, con);
				routeMap.putAll(con.getRoutes());
				int selector = con.getSelector();
				if (selector > 0) {
					selectorMap.put(selector, con);
				}
				con.start();
			}
		}
	}

	/**
	 * Handle a data Packet from the Hotspot/Radio
	 */
	public void handleDataPacket(DatagramPacket packet) {
		byte[] bar = packet.getData();
		int len = packet.getLength();
		DMRDecode decode = new DMRDecode(bar, len);
		int dst = decode.getDst();
		ServiceConnection con = selectorMap.get(dst);
		if (con != null) {
			// dst id is a Selector
			ServiceConnection defaultCon = routeMap.get(DEFAULT_ROUTE);
			if (defaultCon == null || !defaultCon.getName().equals(con.getName())) {
				// select this connection as the default route
				routeMap.put(DEFAULT_ROUTE, con);
				logger.log("selector: " + dst + " Default Service has been changed to: " + con.getName());
			}
		} else {
			// route to appropriate service
			con = routeMap.get(dst);
			lastDestId = decode.getDst();
			if (con != null) {
				markChannelReserve(decode);
				con.handleDataPacket(packet, decode);
			} else {
				// check for a default route id=0
				con = routeMap.get(DEFAULT_ROUTE);
				if (con != null) {
					markChannelReserve(decode);
					con.handleDataPacket(packet, decode);
				}
			}
		}
	}

	public void handlePacket(DatagramPacket packet) throws IOException {

		InetAddress srcAddr = packet.getAddress();
		int port = packet.getPort();
		// logger.log("handlePacket() " + srcAddr.getHostAddress() + ":" + port);

		// replace the session if the IP is the same
		if (clientSession != null) {
			if (srcAddr.equals(clientSession.getAddress())) {
				if (port != clientSession.getPort()) {
					clientSession = null;
				}
			} else {
				// different IP, only accept if the old session is not good
				if (!clientSession.isAuthenticated()) {
					clientSession = null;
				} else {
					// ignore
					return;
				}
			}
		}

		if (clientSession == null) {
			DMRSessionKey key = new DMRSessionKey(srcAddr, port);
			clientSession = new DMRSession(key, srcAddr, port);
			clientSession.setUdpSocket(hotspotSocket);
			logger.log("Creating Session " + key);
		}

		String tag = new String(packet.getData(), 0, 4);
		if (tag.equals("DMRD")) {
			handleDataPacket(packet);
		} else {
			// handle login packets
			DatagramPacket ret = server.getResponse(clientSession, packet);
			if (ret != null) {
				clientSession.sendPacket(ret);
			}
		}
	}

	/*
	 * Calculate the conversation talk time
	 */
	public String getTalkTime() {
		String ret = " 0.0s";
		if (startTalk != 0) {
			float tt = (float) (System.currentTimeMillis() - startTalk) / 1000.0f;
			ret = " " + (df.format(tt)) + "s";
		}
		startTalk = 0;
		return ret;
	}

	/*
	 * Wait an amount of time if the last conversation did not terminate, but has
	 * not been heard.
	 */
	public boolean allowNextTalker() {
		return (System.currentTimeMillis() - lastTalkFromNetwork) > IDLE_TALKER_TIMEOUT;
	}

	/**
	 * Return true if there is no reservation, or the talker is allowed
	 */
	public boolean isChannelReserveLocked(DMRDecode decode) {
		boolean ret = false;
		if ((System.currentTimeMillis() - lastTalkFromReservedChannel) < CHANNEL_RESERVE_TIMEOUT) {
			// only allow the last destination to talk if it matches the dst or src
			// to handle groups or person ids
			if (lastDestId == decode.getDst() || lastDestId == decode.getSrc()) {
				markChannelReserve(decode);
				ret = false;
			} else {
				ret = true;
			}
		} else {
			// reset if the timer is up
			lastDestId = 0;
		}
		return ret;
	}

	/*
	 * Mark the channel reserved for break in traffic
	 */
	public boolean allowBreaking(ServiceConnection sender, DMRDecode decode) {
		boolean ret = sender.allowBreaking();
		if (ret) {
			lastDestId = decode.getDst();
			markChannelReserve(decode);
		}
		return ret;
	}

	/**
	 * DMRD Packet for a service connection to be tramsmitted by the hotspot
	 */
	public synchronized void handleOutgoing(DatagramPacket packet, ServiceConnection sender) {
		DMRDecode decode = new DMRDecode(packet);
		sender.handleOutgoingMapping(packet, decode) ;
		
		if (currentTalker == 0 && !decode.isTerminate()) {

			if (!isChannelReserveLocked(decode)) {
				currentTalker = decode.getSrc();
				startTalk = System.currentTimeMillis();

				logger.log(sender.getName() + " SENT outgoing: " + decode);

				if ((decode.getType() & 0x40) == 0) {
					int dst = decode.getDst();
					ServiceConnection curRoute = routeMap.get(dst);
					if (curRoute == null || !curRoute.getName().equals(sender.getName())) {
						logger.log(sender.getName() + " Setting return route for tg: " + dst);
						routeMap.put(decode.getDst(), sender);
					}
				}
			}
		}

		// ongoing conv
		if (decode.getSrc() == currentTalker || allowBreaking(sender, decode)) {
			// mark it
			isChannelReserveLocked(decode);
			currentTalker = decode.getSrc();
			send(packet);
			markLastTalk();
			if (logger.log(2))
				logger.log(sender.getName() + " SENT outgoing: " + decode);
			else if (decode.isTerminate())
				logger.log(sender.getName() + " SENT outgoing: " + decode + " " + getTalkTime());
		} else {
			if (allowNextTalker() && !isChannelReserveLocked(decode)) {
				// we be current talker now!
				currentTalker = decode.getSrc();
				startTalk = System.currentTimeMillis();
				send(packet);
				markLastTalk();
				if (!decode.isTerminate())
					logger.log(sender.getName() + " SENT outgoing: " + decode);
			} else {
				if (logger.log(2))
					logger.log(sender.getName() + " BLOCKED outgoing: " + decode);
				else if (decode.getFrame() == 1)
					logger.log(sender.getName() + " BLOCKED outgoing: " + decode);
			}
		}

		// terminate current conversation
		if (decode.isTerminate()) {
			currentTalker = 0;
			lastTalkFromNetwork = 0;
		}
	}

	/**
	 * Send a packet to the MMDVM client
	 */
	public void send(DatagramPacket packet) {
		try {
			DMRSession currentSession = clientSession;
			if (currentSession != null) {
				packet.setAddress(currentSession.getAddress());
				packet.setPort(currentSession.getPort());
				hotspotSocket.send(packet);
			}
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}

	// listen on local socket
	public void run() {
		byte[] bar = new byte[2048];
		DatagramPacket packet = new DatagramPacket(bar, bar.length);
		while (true) {
			try {
				packet.setData(bar);
				hotspotSocket.receive(packet);
				handlePacket(packet);
			} catch (Exception ex) {
				Logger.handleException(ex);
			}
		}
	}

}
