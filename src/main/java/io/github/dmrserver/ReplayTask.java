package io.github.dmrserver;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

public class ReplayTask extends TimerTask {
	public static Logger logger = Logger.getLogger();
	
	DatagramSocket local_socket;
	DMRCapture capture;
	DMRServer server;

	public ReplayTask(DMRServer server, DMRCapture capture, DatagramSocket local_socket) {
		this.server = server;
		this.capture = capture;
		this.local_socket = local_socket;
	}

	@Override
	public void run() {
		logger.log("ReplayTask: " + new Date());

		DatagramPacket packet = new DatagramPacket(new byte[8], 8);
		// packet.setPort( capture.getPort() ) ;
		// packet.setAddress( capture.getHost() ) ;

		ArrayList<DMRRecord> list = capture.getStore();
		int pos = 0;

		DMRRecord rec = list.get(pos);
		long diff = System.currentTimeMillis() - rec.getTime();
		long t;
		while (true) {
			t = System.currentTimeMillis();
			// System.out.println("ReplayTask loop : "+(rec.getTime()+diff)+ " "+t);
			if (t >= (rec.getTime() + diff)) {
				// play
				byte[] bar = rec.getData();
				// DMRDecode.intTo3Bytes(12345, bar, 5) ; //rewrite source
				// DMRDecode.intTo3Bytes(91, bar, 8) ; //rewrite source
				// DMRDecode.intToBytes(12345, bar, 11) ; //rewrite source
				packet.setData(bar);
				// packet.setData( rec.getData()) ;
				packet.setLength(rec.getLength());
				// System.out.println(DMRDecode.hex(bar,0,rec.getLength()) );

				DMRDecode dec = new DMRDecode(packet.getData(), packet.getLength());
				logger.log("write replay: " + dec.toString() + " " + rec.getTime());

				try {
					server.forward(packet, true);
					// local_socket.send( packet) ;
				} catch (Exception ex) {
					Logger.handleException(ex);
				}

				pos++;
				if (pos >= list.size())
					break;
				rec = list.get(pos);
			}
			try {
				Thread.sleep(1);
			} catch (Exception ex) {
			}
		}
		logger.log("ReplayTask Complete: " + new Date());

	}

}
