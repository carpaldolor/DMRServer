package io.github.dmrserver;

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
		byte[] iv = reHash(key, 1000);
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
		byte[] iv = reHash(key, 1000);
		byte[] skey = reHash(key, 10000);
		logger.log("iv: " + DMRDecode.hex(iv, 0, iv.length));
		logger.log("key: " + DMRDecode.hex(skey, 0, skey.length));

		Encryption enc = new Encryption(key);
		if (oper.equals("-e")) {
			enc.init(key);
			byte[] encoded = enc.encrypt(msg.getBytes(StandardCharsets.UTF_8), 0, msg.length());
			logger.log("cipher: " + encoded.length + " " + DMRDecode.hex(encoded, 0, encoded.length));
		}

		if (oper.equals("-d")) {
			msg = msg.trim().replaceAll("\\s*", "");

			byte[] encoded = hexStringToByteArray(msg);
			logger.log("encoded: " + DMRDecode.hex(encoded, 0, encoded.length));

			byte[] decoded = enc.decrypt(encoded, 0, encoded.length);
			logger.log("cipher: " + decoded.length + " " + new String(decoded));
		}

	}
}
