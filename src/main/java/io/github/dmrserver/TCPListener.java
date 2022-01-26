package io.github.dmrserver;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPListener {
	DMRServer dmrServer = null;
	int serverPort;
	ServerSocket ss;

	public TCPListener(int serverPort) {
		this.serverPort = serverPort;
		start();
	}

	public void setDMRServer(DMRServer dmrServer) {
		this.dmrServer = dmrServer;
	}

	// Start the Server Socket Listener
	private void start() {
		System.out.println("Starting TCP Listener on port: " + serverPort);
		Runnable tcpServer = new Runnable() {
			public void run() {
				while (true) {
					try {
						ss = new ServerSocket(serverPort);
						Socket s = null;
						while ((s = ss.accept()) != null) {
							spawn(s);
						}
					} catch (Exception ex) {
						Logger.handleException(ex);
					}
					try {
						Thread.sleep(10000);
					} catch (Exception ex) {
					}
				}
			}
		};

		Thread th = new Thread(tcpServer);
		th.setName("TcpServer-" + serverPort);
		th.start();
	}

	public void handlePacket(DatagramPacket packet, Socket s) {
		if (dmrServer != null) {
			dmrServer.handlePacket(packet, s);
		}
	}

	// spawn a thread for a TCP connection
	private void spawn(Socket s) {
		System.out.println("Received connection from: " + s.getInetAddress().getHostAddress() + ":" + s.getPort());

		Runnable srunner = new Runnable() {
			public void run() {
				// byte[] bar = new byte[2048];
				try {
					InputStream is = s.getInputStream();
					while (true) {

						// TODO may be moved outside
						byte[] bar = new byte[2048];
						int num = is.read(bar);
						if (num > 0) {
							System.out.println("TCP Received: " + new String(bar, 0, num));
							// byte[] payload = new byte[num] ;
							// System.arraycopy(bar,0,payload,0,num) ;
							DatagramPacket packet = new DatagramPacket(bar, 0, num);
							packet.setPort(s.getPort());
							packet.setAddress(s.getInetAddress());
							handlePacket(packet, s);
						} else {
							try {
								Thread.sleep(100);
							} catch (Exception ex) {
							}

						}
					}
				} catch (Exception ex) {
					Logger.handleException(ex);
				} finally {
					try {
						s.close();
					} catch (Exception ex) {
					}
				}
			}
		};
		Thread th = new Thread(srunner);
		th.setName("Socket-" + s.getPort());
		th.start();
	}

	public static void main(String[] args) {
		TCPListener _instance = new TCPListener(Integer.parseInt(args[0]));
	}

}
