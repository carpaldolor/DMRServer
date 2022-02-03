package io.github.dmrserver;

import java.net.DatagramPacket;

public class DMRDecode {
	public static String[] frameName = new String[] { "voice", "voice_sync", "data_sync", "unknown" };
	public static String[] voiceSubType = new String[] { "A", "B", "C", "D", "E", "F", " ", " " };
	public static String[] dataSubType = new String[] { "unknown", "voice_header", "voice_term", "unknown" };

	byte[] bar;
	int len;

	String tag;
	int type;
	int src;
	int dst;
	int rpt;
	int stream;
	int slot;
	int frame;
	int subType;

	boolean terminate = false;
	
	String decodeString ;

	public DMRDecode(DatagramPacket packet) {
		parsePacket(packet) ;
	}
		
	public DMRDecode(byte[] bar, int len) {
		this.bar = bar;
		this.len = len;
		decodeString = decode();
	}

	public void parsePacket(DatagramPacket packet) {
		this.bar = packet.getData();
		this.len = packet.getLength();
		decodeString = decode();		
	}
	
	public String getTag() {
		return tag;
	}

	public boolean isTerminate() {
		return terminate;
	}

	public String decode() {
		String ret;
		tag = new String(bar, 0, 4);
		if (tag.equals("DMRD")) {
			type = ti(bar[15]);
			slot = type >> 7 + 1;
			frame = (type >> 4) & 0x03;
			subType = type & 0x0F;
			String st = " ";
			if (frame == 0 || frame == 1) {
				st = voiceSubType[subType & 0x07];
			} else if (frame == 2) {
				st = dataSubType[subType & 0x03];

				terminate = ((subType & 0x03) == 2);
			}
			src = ti(bar, 5, 3) ;
			dst = ti(bar, 8, 3) ;
			rpt = ti(bar, 11, 4) ;
			stream = ti(bar, 16, 4) ;
			
			ret = "len: "+len+" DMRD seq:" + ti_pad(bar[4]) + " src:" + src + " dst:" +dst+ " rpt:"
					+ rpt + " slot:" + slot + " strm:" + stream + " data:" + (len - 20) + " frame:"
					+ frameName[frame] + " " + st;
		} else if (tag.equals("RPTP")) {
			ret = "RPTPING " + ti(bar, 7, 4);
		} else if (tag.equals("RPTL")) {
			ret = "RPTL(login init) rptr:" + ti(bar, 4, 4);
		} else if (tag.equals("RPTA")) {
			ret = "RPTACK  rptr:" + ti(bar, 6, 4);
		} else if (tag.equals("RPTK")) {
			ret = "RPTK (auth) rptr:" + ti(bar, 4, 4) + " hash: " + hex(bar, 8, len - 8);
		} else if (tag.equals("RPTC")) {
			ret = "RPTC (auth) rptr:" + ti(bar, 4, 4) + " " + new String(bar, 0, len);
		} else if (tag.equals("MSTN")) {
			ret = "MSTNAK " + ti(bar, 6, 4);
		} else if (tag.equals("MSTP")) {
			ret = "MSTPONG " + ti(bar, 7, 4);
		} else {
			ret = hex(bar, 0, len);
		}
		return ret;
	}

	public static String ti_pad(byte b) {
		String ret = Integer.toString((int) b & 0xFF);
		if (ret.length() < 3)
			ret = new String("000").substring(0, 3 - ret.length()) + ret;
		return ret;
	}

	public static int ti(byte b) {
		return (int) b & 0xFF;
	}

	public static int ti(byte[] bar, int off, int len) {
		int ret = 0;
		for (int i = 0; i < len; i++) {
			ret = ret << 8;
			ret += (((int) bar[off + i]) & 0xFF);
		}
		return ret;
	}

	public static String hex(byte[] bar, int start, int len) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < len; i++) {
			String s = Integer.toString(bar[i] & 0xFF, 16);
			if (s.length() == 1)
				sb.append("0");
			sb.append(s);
			sb.append(" ");
		}
		return sb.toString();
	}

	public static void intToBytes(int i, byte[] bar, int pos) {
		bar[pos] = (byte) ((i >> 24) & 0xff);
		bar[pos + 1] = (byte) ((i >> 16) & 0xff);
		bar[pos + 2] = (byte) ((i >> 8) & 0xff);
		bar[pos + 3] = (byte) ((i) & 0xff);
	}

	public static void intTo3Bytes(int i, byte[] bar, int pos) {
		bar[pos] = (byte) ((i >> 16) & 0xff);
		bar[pos + 1] = (byte) ((i >> 8) & 0xff);
		bar[pos + 2] = (byte) ((i) & 0xff);
	}

	public String toString() {
		return decodeString;
	}
	public static String[] getFrameName() {
		return frameName;
	}

	public static String[] getVoiceSubType() {
		return voiceSubType;
	}

	public static String[] getDataSubType() {
		return dataSubType;
	}

	public byte[] getBar() {
		return bar;
	}

	public int getLen() {
		return len;
	}

	public int getType() {
		return type;
	}

	public int getSrc() {
		return src;
	}

	public int getDst() {
		return dst;
	}

	public int getRpt() {
		return rpt;
	}

	public int getStream() {
		return stream;
	}

	public int getSlot() {
		return slot;
	}

	public int getFrame() {
		return frame;
	}

	public int getSubType() {
		return subType;
	}

}
