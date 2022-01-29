package io.github.dmrserver;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;

/**
 * Main Class for DMRServer
 */
public class DMRServer implements Runnable {
	public static Logger logger = Logger.getLogger();

	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-KKmmss");

	public static boolean enableCapture = false;
	public static boolean enableReplay = false;

	public static long REPLAY_TIMEOUT = 5000L;

	DatagramSocket local_socket;

	InetAddress local_host;
	InetAddress saved_host;

	int local_port;
	int saved_port;

	HashMap<DMRSessionKey, DMRSession> sessionMap;

	Timer replayTimer;

	Encryption encryption = null;

	public DMRServer(int local_port, InetAddress local_host) throws IOException {
		this.local_host = local_host;
		this.local_port = local_port;

		this.local_socket = new DatagramSocket(local_port, local_host);

		replayTimer = new Timer(true);

		// 10 sec purge cycle for expired sessions
		replayTimer.schedule(new PurgeTask(this), 10000, 10000);

		sessionMap = new HashMap<DMRSessionKey, DMRSession>();
	}

	public DMRServer() {
		sessionMap = new HashMap<DMRSessionKey, DMRSession>();
	}

	public boolean isSecure() {
		return encryption != null;
	}

	public HashMap<DMRSessionKey, DMRSession> getSessionMap() {
		return sessionMap;
	}

	public synchronized void putSession(DMRSessionKey key, DMRSession session) {
		sessionMap.put(key, session);
	}

	public synchronized void removeSession(DMRSessionKey key) {
		sessionMap.remove(key);
	}

	public void setEncryption(Encryption encryption) {
		this.encryption = encryption;
	}

	public boolean isSameAddr(InetAddress a1, InetAddress a2) {
		boolean ret = false;
		if (a1 != null && a2 != null) {
			return a1.getHostAddress().equals(a2.getHostAddress());
		}
		return ret;
	}

	public void forward(DatagramPacket packet) {
		InetAddress srcHost = packet.getAddress();
		int srcPort = packet.getPort();
		ArrayList<DMRSessionKey> purgeList = null;
		Set<DMRSessionKey> keySet = new HashSet<DMRSessionKey>();
		keySet.addAll(sessionMap.keySet());
		for (DMRSessionKey key : keySet) {
			try {
				DMRSession session = sessionMap.get(key);
				if (session != null) {
					if (!session.isExpired()) {
						// filter self
						if (srcPort != session.getPort() || !isSameAddr(srcHost, session.getAddress())) {
							packet.setPort(session.getPort());
							packet.setAddress(session.getAddress());

							session.sendPacket(packet);
						}
					} else {
						if (purgeList == null)
							purgeList = new ArrayList<DMRSessionKey>();
						purgeList.add(key);
					}
				}
			} catch (Exception ex) {
				Logger.handleException(ex);
			}
		}

		if (purgeList != null) {
			for (DMRSessionKey key : purgeList)
				removeSession(key);
		}
	}

	public static void addToBytes(byte[] bar, int pos, String msg) {
		byte[] b = msg.getBytes();
		System.arraycopy(b, 0, bar, pos, b.length);
	}

	public byte[] getLoginAck(byte[] bar, int salt) {
		byte[] ret = new byte[10];
		System.arraycopy(bar, 4, ret, 6, 4);
		addToBytes(ret, 0, "RPTACK");
		DMRDecode.intToBytes(salt, ret, 6);
		return ret;
	}

	public byte[] getAck(byte[] bar) {
		byte[] ret = new byte[10];
		System.arraycopy(bar, 4, ret, 6, 4);
		addToBytes(ret, 0, "RPTACK");
		return ret;
	}

	public byte[] getNack(byte[] bar) {
		byte[] ret = new byte[10]; // 18 bytes in cap
		Arrays.fill(ret, (byte) 0);
		System.arraycopy(bar, 4, ret, 6, 4);
		addToBytes(ret, 0, "MSTNAK");
		return ret;
	}

	public DatagramPacket getResponse(DMRSession session, DatagramPacket packet) {
		DatagramPacket ret = null;
		byte[] bar = packet.getData();
		int len = packet.getLength();

		String tag = new String(bar, 0, 4);

		// handle encrypted packets, decrypt all but DMRD
		// those are encrypted with a different key at the source
		if (isSecure() && !tag.equals("DMRD")) {
			if (!encryption.decryptPacket(packet)) {
				// decrypt error
				return null;
			}

			bar = packet.getData();
			len = packet.getLength();
		}

		DMRDecode dec = new DMRDecode(bar, len);
		if (logger.log(2))
			logger.log(1, "received: " + len + " " + dec.toString());

		tag = dec.getTag();

		if (tag.equals("RPTL")) { // login init
			int salt = session.initLogin();
			byte[] resp = getLoginAck(bar, salt);
			packet.setData(resp);
			packet.setLength(resp.length);
			ret = packet;
		} else if (tag.equals("RPTK")) { // auth
			byte[] resp;
			if (!session.isExpired() && session.checkAuth(bar)) {
				resp = getAck(bar);
			} else {
				resp = getNack(bar);
			}
			packet.setData(resp);
			packet.setLength(resp.length);
			ret = packet;
		} else if (tag.equals("RPTC")) { // config or //close
			if (bar[4] == 'L') {
				// close
				if (logger.log(1))
					logger.log("Closing Session: " + session.getKey());
				removeSession(session.getKey());
				byte[] resp = getAck(bar);
				packet.setData(resp);
				packet.setLength(resp.length);
				ret = packet;
			} else {
				byte[] resp;
				if (!session.isExpired() && session.isAuthenticated()) {
					resp = getAck(bar);
				} else {
					resp = getNack(bar);
				}
				packet.setData(resp);
				packet.setLength(resp.length);
				ret = packet;
			}
		} else {
			if (session.isAuthenticated()) {

				if (tag.equals("RPTP")) { // RPTPING
					addToBytes(bar, 0, "MSTPONG");
					ret = packet;
					session.touch();
				} else if (tag.equals("DMRD")) { // config
					session.touch();

					if (enableReplay || enableCapture) {
						DMRCapture capture = session.getCapture();
						capture.add(packet);
						if (dec.isTerminate()) {
							if (enableCapture) {
								String fname = "msg-" + sdf.format(new Date()) + ".dat";
								capture.log(fname);
							}
							if (enableReplay) {
								if (logger.log(1))
									logger.log("Schedule replay in 5s");
								scheduleReplay(capture, REPLAY_TIMEOUT);
							}
							session.resetCapture();
						}
					}
					// forward
					forward(packet);
				}
			} else {
				// expired session
				byte[] resp = getNack(bar);
				packet.setData(resp);
				packet.setLength(resp.length);
				ret = packet;
			}
		}

		if (ret != null) {
			dec = new DMRDecode(ret.getData(), ret.getLength());
			if (logger.log(1))
				logger.log("sent: " + dec.toString());

			if (isSecure()) {
				encryption.encryptPacket(ret);
			}
		}

		return ret;
	}

	public void scheduleReplay(DMRCapture session, long delay) {
		ReplayTask task = new ReplayTask(this, session, local_socket);
		replayTimer.schedule(task, delay);
	}

	public DMRSession getSession(InetAddress address, int port) {
		DMRSessionKey key = new DMRSessionKey(address, port);
		DMRSession session = sessionMap.get(key);
		if (session == null) {
			if (logger.log(1))
				logger.log("Creating Session " + key);
			session = new DMRSession(key, address, port);
			putSession(key, session);
		}
		return session;
	}

	public void printSessions() {
		for (DMRSessionKey key : sessionMap.keySet()) {
			System.out.println("key:" + key + "  " + sessionMap.get(key));
		}
	}

	public void handlePacket(DatagramPacket packet, Socket tcpSocket) {
		try {
			DMRSession session = getSession(packet.getAddress(), packet.getPort());
			if (tcpSocket != null)
				session.setTcpSocket(tcpSocket);
			else
				session.setUdpSocket(local_socket);

			DatagramPacket ret = getResponse(session, packet);
			if (ret != null) {
				session.sendPacket(ret);
				// local_socket.send(ret) ;
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
				local_socket.receive(packet);
				handlePacket(packet, null);
			} catch (Exception ex) {
				Logger.handleException(ex);
			}
		}
	}

	public static void monitor(DMRServer server) {
		HashMap<String, String> filesMap = new HashMap<>();
		Console console = System.console();
		
		if (console == null) {
			// wait here forever, may not be one
			synchronized (DMRServer.class) {
				try {
					DMRServer.class.wait();
				} catch (InterruptedException e) {
				}
			}
		}

		while (true) {
			String line = console.readLine(">").trim();
			if (line.equals("ls")) {
				filesMap = new HashMap<>();
				File f = new File(".");
				File[] flist = f.listFiles();
				Arrays.sort(flist);
				int cnt = 0;
				for (int i = 0; i < flist.length; i++) {
					if (flist[i].getName().startsWith("msg-") || flist[i].getName().endsWith(".dat")) {
						System.out.println(cnt + " " + flist[i].getName());
						filesMap.put(Integer.toString(cnt++), flist[i].getName());
					}
				}
			} else if (line.equals("s")) {
				server.printSessions();
			} else {
				String fname;
				if ((fname = filesMap.get(line)) != null) {
					DMRCapture s = DMRCapture.read(fname);
					server.scheduleReplay(s, 500);
				}
			}
		}
	}

	public static String getArg(String[] args, String val) {
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equals(val))
				return args[i + 1];
		}
		return null;
	}

	public static int getIntArg(String[] args, String val) {
		int ret = -1;
		try {
			for (int i = 0; i < args.length - 1; i++) {
				if (args[i].equals(val))
					ret = Integer.parseInt(args[i + 1]);
			}
		} catch (Exception ex) {
		}
		return ret;
	}

	public static void usage() {
		System.out.println("Usage: DMRServer -port 62031 [-ip 0.0.0.0] ");
	}

	public static void exitOnError(String reason) {
		System.out.println(reason);
		System.out.println();
		usage();
		System.exit(-1);
	}

	public static void checkFiles() {
		File f = new File(DMRAuth.filename);
		if (!f.exists()) {
			exitOnError("Missing required file: auth.properties, see documentation");
		}
	}

	public static void main(String[] args) {
		try {

			int port = getIntArg(args, "-port");
			if (port == -1) {
				exitOnError("Missing required parameter: -port");
			}

			if (Arrays.asList(args).contains("-capture"))
				enableCapture = true;

			if (Arrays.asList(args).contains("-replay")) {
				int replayTime = getIntArg(args, "-replay");
				if (replayTime < 1) {
					exitOnError("invalid value in -replay, must be a time in seconds");
				}
				REPLAY_TIMEOUT = ((long) replayTime) * 1000L;
				enableReplay = true;
			}

			String ip = "0.0.0.0";
			if (Arrays.asList(args).contains("-ip")) {
				String sip = getArg(args, "-ip");
				if (sip != null)
					ip = sip;
			}

			checkFiles();

			DMRServer server = new DMRServer(port, InetAddress.getByName(ip));
			Thread th = new Thread(server);
			th.start();

			String key = getArg(args, "-serverkey");
			if (key != null) {
				server.setEncryption(new Encryption(key.trim()));
			}

			if (Arrays.asList(args).contains("-tcp")) {
				TCPListener tcpServer = new TCPListener(port);
				tcpServer.setDMRServer(server);
			}

			// start console
			monitor(server);
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
	}
}
