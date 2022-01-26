package io.github.dmrserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPProxy implements Runnable {
	public static String[] frameName = new String[] { "voice", "voice_sync", "data_sync", "unknown" };
	public static String[] voiceSubType = new String[] { "A", "B", "C", "D", "E", "F", " ", " " };
	public static String[] dataSubType = new String[] { "unknown", "voice_header", "voice_term", "unknown" };

	DatagramSocket local_socket;
	DatagramSocket remote_socket;

	InetAddress local_host;
	InetAddress remote_host;
	InetAddress saved_host;

	int local_port;
	int remote_port;
	int saved_port;

	public UDPProxy(int local_port, InetAddress local_host, int remote_port, InetAddress remote_host)
			throws IOException {
		this.local_host = local_host;
		this.local_port = local_port;
		this.remote_host = remote_host;
		this.remote_port = remote_port;

		this.local_socket = new DatagramSocket(local_port, local_host);
		this.remote_socket = new DatagramSocket();
	}

	// listen on local socket
	public void run() {
		byte[] bar = new byte[2048];
		DatagramPacket packet = new DatagramPacket(bar, bar.length);
		try {
			while (true) {
				local_socket.receive(packet);
				DMRDecode dec = new DMRDecode(packet.getData(), packet.getLength());
				System.out
						.println("local from: " + packet.getAddress() + ":" + packet.getPort() + " " + dec.toString());
				// System.out.println( hex(packet.getData(), packet.getLength()) );
				saved_host = packet.getAddress();
				saved_port = packet.getPort();

				packet.setAddress(remote_host);
				packet.setPort(remote_port);
				remote_socket.send(packet);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	// outgoing
	public void incoming() {
		byte[] bar = new byte[2048];
		DatagramPacket packet = new DatagramPacket(bar, bar.length);
		try {
			while (true) {
				remote_socket.receive(packet);
				DMRDecode dec = new DMRDecode(packet.getData(), packet.getLength());
				System.out
						.println("remote from: " + packet.getAddress() + ":" + packet.getPort() + " " + dec.toString());
				// System.out.println( hex(packet.getData(), packet.getLength()) );
				packet.setAddress(saved_host);
				packet.setPort(saved_port);
				local_socket.send(packet);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try {
			if (args.length < 4) {
				System.out.println("Usage: UDPProxy <local_port> <local_ip> <remote_port>  <remote_ip>");
			} else {
				UDPProxy proxy = new UDPProxy(Integer.parseInt(args[0]), InetAddress.getByName(args[1]),
						Integer.parseInt(args[2]), InetAddress.getByName(args[3]));
				Thread th = new Thread(proxy);
				th.start();
				proxy.incoming();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
