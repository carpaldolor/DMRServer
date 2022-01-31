package io.github.multiconnect;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Timer;

import io.github.dmrserver.DMRDecode;
import io.github.dmrserver.DMRServer;
import io.github.dmrserver.DMRSession;
import io.github.dmrserver.DMRSessionKey;
import io.github.dmrserver.Logger;

public class ConnectionManager implements Runnable {
	public static Logger logger = Logger.getLogger();

	public static long IDLE_TALKER_TIMEOUT = 1000;

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
	long lastTalk = 0;

	public ConnectionManager(ServiceConfig config) {
		this.config = config;
		this.server = new DMRServer();

	}

	public void markLastTalk() {
		lastTalk = System.currentTimeMillis();
	}

	/**
	 * Called by the Timer Task
	 */
	public void pingAll() {
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

			long pingTime=10;
			String val = config.getSection(ServiceConfig.MAIN_SECTION).getParam(ConfigSection.PING_TIME);
			if( val!=null) {
				pingTime = Long.parseLong(val);
			}
			pingTimer = new Timer(true);
			// 10 sec ping cycle
			pingTimer.schedule(new PingTask(this), pingTime*1000L, pingTime*1000L);

			
			activateServices();
			System.out.println("Opening a listener on port: " + port+"  ping time: "+pingTime+"s");
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
			if (con != null) {
				con.handleDataPacket(packet, decode);
			} else {
				// check for a default route id=0
				con = routeMap.get(DEFAULT_ROUTE);
				if (con != null) {
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

	/**
	 * DMRD Packet outgoing from a service connection
	 */
	public synchronized void handleOutgoing(DatagramPacket packet, ServiceConnection sender) {
		DMRDecode decode = new DMRDecode(packet);
		if (currentTalker == 0) {
			currentTalker = decode.getSrc();

			if ((decode.getType() & 0x40) == 0) {
				int dst = decode.getDst();
				ServiceConnection curRoute = routeMap.get(dst);
				if (curRoute == null || !curRoute.getName().equals(sender.getName())) {
					logger.log(sender.getName() + " Setting return route for tg: " + dst);
					routeMap.put(decode.getDst(), sender);
//					logger.log("type :" + decode.getType());
				}
			}
		}

		// ongoing conv
		if (decode.getSrc() == currentTalker || sender.allowBreaking()) {
			currentTalker = decode.getSrc();
			send(packet);
			if (decode.getFrame() == 1)
				logger.log(sender.getName() + " SENT outgoing: " + decode);
			if (logger.log(2))
				logger.log(sender.getName() + " SENT outgoing: " + decode);
			markLastTalk();
		} else {
			if ((System.currentTimeMillis() - lastTalk) > IDLE_TALKER_TIMEOUT) {
				// we be current talker now!
				currentTalker = decode.getSrc();
				send(packet);
				if (decode.getFrame() == 1)
					logger.log(sender.getName() + " SENT outgoing: " + decode);
				else if (logger.log(2))
					logger.log(sender.getName() + " SENT outgoing: " + decode);
				markLastTalk();
			} else {
				if (decode.getFrame() == 1)
					logger.log(sender.getName() + " BLOCKED outgoing: " + decode);
				else if (logger.log(2))
					logger.log(sender.getName() + " BLOCKED outgoing: " + decode);
			}
		}

		// terminate current conversation
		if (decode.isTerminate()) {
			currentTalker = 0;
			lastTalk = 0;
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
