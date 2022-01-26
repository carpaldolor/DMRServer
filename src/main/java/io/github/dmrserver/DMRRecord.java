package io.github.dmrserver;

import java.io.Serializable;

public class DMRRecord implements Serializable {
	private static final long serialVersionUID = 0L;

	long ts;
	byte[] data;
	int len;

	public DMRRecord(byte[] data, int len) {
		ts = System.currentTimeMillis();
		this.data = (byte[]) data.clone();
		;
		this.len = len;
	}

	public int getLength() {
		return len;
	}

	public byte[] getData() {
		return data;
	}

	public long getTime() {
		return ts;
	}
}
