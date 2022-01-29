package io.github.dmrserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;

public class DMRSession {
	static Random rand = new Random();

	int salt = -1;
	boolean isAuthenticated = false;

	long expiration = 60000L;
	long ts = 0L;

	DMRSessionKey key;
	InetAddress address;
	int port;

	DMRCapture capture = null;

	Socket tcpSocket = null;
	DatagramSocket udpSocket = null;

	public DMRSession(DMRSessionKey key, InetAddress address, int port) {
		this.key = key;
		this.address = address;
		this.port = port;
		touch();
		isAuthenticated = false;
		salt = -1;
	}


	public boolean isTcp() {
		return (tcpSocket != null);
	}

	public void setTcpSocket(Socket tcpSocket) {
		this.tcpSocket = tcpSocket;
	}

	public void setUdpSocket(DatagramSocket udpSocket) {
		this.udpSocket = udpSocket;
	}

	public void sendPacket(DatagramPacket packet) throws IOException {
		if (isTcp()) {
			System.out.println("TCP sendPacket() " + new String(packet.getData(), 0, packet.getLength()));
			tcpSocket.getOutputStream().write(packet.getData(), 0, packet.getLength());
			tcpSocket.getOutputStream().flush();
		} else {
			udpSocket.send(packet);
		}
	}

	public DMRSessionKey getKey() {
		return key;
	}

	public int getPort() {
		return port;
	}

	public InetAddress getAddress() {
		return address;
	}

	public int initLogin() {
		isAuthenticated = false;
		salt = rand.nextInt(2000000000);
		touch();
		return salt;
	}

	public boolean checkAuth(byte[] bar) {
		int rptr = ((((int) bar[4]) & 0xff) << 24) | ((((int) bar[5]) & 0xff) << 16) | ((((int) bar[6]) & 0xff) << 8)
				| (((int) bar[7]) & 0xff);
		isAuthenticated = DMRAuth.getInstance().checkAuth(rptr, salt, bar, 8);
		salt = -1;
		touch();
		return isAuthenticated;
	}

	public void touch() {
		ts = System.currentTimeMillis();
	}

	public boolean isAuthenticated() {
		return isAuthenticated && !isExpired();
	}

	public boolean isExpired() {
		return ((System.currentTimeMillis() - ts) > expiration);
	}

	public DMRCapture getCapture() {
		if (capture == null) {
			capture = new DMRCapture();
		}
		return capture;
	}

	public void resetCapture() {
		capture = null;
	}

	public String toString() {
		return address + ":" + port + " exp:" + isExpired() + " auth:" + isAuthenticated;
	}
}
