package io.github.dmrserver;

import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Encryption {
	public static Logger logger = Logger.getLogger();

	public static final int SHA256_BLOCK_SIZE = 32;
	public static final String algorithm = "AES/CBC/NoPadding";

	SecretKey secretkey;
	IvParameterSpec iv;

	public Encryption() {
	}

	public Encryption(String key) {
		init(key);
	}

	public static byte[] reHash(String str, int iter) {
		byte[] hash = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
			for (int i = 0; i < iter; i++) {
				digest.update(hash);
				hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
			}
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
		return hash;
	}

	public static IvParameterSpec generateIv(String key) {
		byte[] iv = reHash(key, 11000);
		return new IvParameterSpec(iv, 0, 16);
	}

	public static SecretKey createKey(String key) {
		byte[] bar = reHash(key, 10000);
		SecretKey ret = new SecretKeySpec(bar, 0, 32, "AES");
		return ret;
	}

	public void init(String key) {
		secretkey = createKey(key);
		iv = generateIv(key);
	}

	/**
	 * Encrypt/Decrypt all but the first 4 chars. DMRD message will not be touched,
	 * as they are encrypted already by the sender. The server only needs the
	 * ping,pong,login messages
	 */
	public boolean decryptPacket(DatagramPacket ret) {
		byte[] bar = ret.getData();
		int len = ret.getLength();

		if ((len - 4) % 16 != 0) {
			if (logger.log(5))
				logger.log("decryptPacket() REJECTED len:" +ret.getLength()+" " + DMRDecode.hex(bar, 0, len));
			return false;
		}
		else {
			if (logger.log(5))
				logger.log("decryptPacket() in  " + DMRDecode.hex(bar, 0, len));			
		}

		byte[] plain = decrypt(bar, 4, len - 4);
		System.arraycopy(plain, 0, bar, 4, plain.length);

		ret.setData(bar);
		ret.setLength(4 + plain.length);

		if (logger.log(5))
			logger.log("decryptPacket() in  " + DMRDecode.hex(bar, 0, ret.getLength()));

		return true;
	}

	public void encryptPacket(DatagramPacket ret) {
		byte[] bar = ret.getData();
		int len = ret.getLength();
		if (logger.log(2)) {
			logger.log("encryptPacket() in len: " + len);
			if (logger.log(5)) {
				logger.log("encryptPacket() in  " + DMRDecode.hex(bar, 0, len));
			}
		}
		byte[] cipher = encrypt(bar, 4, len - 4);
		byte[] newbar = new byte[cipher.length + 4];
		System.arraycopy(bar, 0, newbar, 0, 4);
		System.arraycopy(cipher, 0, newbar, 4, cipher.length);

		ret.setData(newbar);
		ret.setLength(newbar.length);

		if (logger.log(2)) {
			logger.log("encryptPacket() out len: " + newbar.length);
			if (logger.log(5)) {
				logger.log("encryptPacket() out  " + DMRDecode.hex(newbar, 0, newbar.length));
				logger.log("encryptPacket() out  " + new String(ret.getData(), 0, newbar.length));
			}

		}
	}

	public byte[] decrypt(byte[] cipherText, int start, int len) {

		byte[] plainText = null;
		try {
			Cipher cipher = Cipher.getInstance(algorithm);
			cipher.init(Cipher.DECRYPT_MODE, secretkey, iv);
			plainText = cipher.doFinal(cipherText, start, len);
			int clen = ((plainText[0] & 0xff) << 8) + (plainText[1] & 0xff);
			byte[] ret = new byte[clen];
			System.arraycopy(plainText, 2, ret, 0, clen);
			plainText = ret;
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
		return plainText;
	}

	public byte[] encrypt(byte[] input, int start, int len) {

		int clen = len + 2;
		int cpad = 16 - (clen % 16);

		byte[] cipherText = new byte[clen + cpad];
		System.arraycopy(input, start, cipherText, 2, len);
		cipherText[0] = (byte) ((len >> 8) & 0xff);
		cipherText[1] = (byte) ((len) & 0xff);
		for (int i = 0; i < cpad; i++)
			cipherText[clen + i] = (byte) cpad;

		try {
			Cipher cipher = Cipher.getInstance(algorithm);
			cipher.init(Cipher.ENCRYPT_MODE, secretkey, iv);
			cipherText = cipher.doFinal(cipherText, 0, cipherText.length);
		} catch (Exception ex) {
			Logger.handleException(ex);
		}
		return cipherText;
	}

	public static byte[] hexTOBytes(String s) {
		byte[] bar = new byte[s.length() / 2];
		for (int i = 0; i < bar.length; i++) {
			bar[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 1), 16);
		}
		return bar;
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static void main(String[] args) {
		String key = args[0];
		String oper = args[1];
		String msg = args[2];

		logger.log("string: " + key);
		byte[] iv = reHash(key, 11000);
		byte[] skey = reHash(key, 10000);
		logger.log("iv: " + DMRDecode.hex(iv, 0, iv.length));
		logger.log("key: " + DMRDecode.hex(skey, 0, skey.length));

		Encryption enc = new Encryption(key);
		if (oper.equals("-e")) {
			DatagramPacket dp = new DatagramPacket(msg.getBytes(StandardCharsets.UTF_8), 0, msg.length());
			enc.init(key);
			enc.encryptPacket(dp);
			logger.log("cipher len:" + dp.getLength() + " msg: " + DMRDecode.hex(dp.getData(), 0, dp.getLength()));
		}

		if (oper.equals("-d")) {
			msg = msg.trim().replaceAll("\\s*", "");

			byte[] encoded = hexStringToByteArray(msg);
			DatagramPacket dp = new DatagramPacket(encoded, encoded.length);

			logger.log("encoded: " + DMRDecode.hex(encoded, 0, encoded.length));

			enc.decryptPacket(dp);
			byte[] decoded = dp.getData();
			logger.log("cipher  len: " + dp.getLength() + " msg: " + new String(decoded, 0, dp.getLength()));
		}

	}
}
