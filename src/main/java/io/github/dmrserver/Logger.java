package io.github.dmrserver;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd KKmmss");
	
	public static int level = 0;
	
	private static  Logger _instance=null;
	
	static {
		String slevel = System.getProperty("dmr.logger.level") ;
		if( slevel!=null) {
			try {
				level = Integer.parseInt(slevel);
			}
			catch(Exception ex) {}
		}
	}
	
	public static Logger getLogger() {
		if( _instance==null) {
			_instance = new Logger() ;
		}
		return _instance ;
	}
	
	public Logger() {	
	}
	
	
	public boolean log(int lev) {
		return lev >= level ;
	}
	
	public String join(Object[] sar) {
		StringBuffer sb = new StringBuffer() ;
		for(int i=0;i<sar.length;i++)
			sb.append(sar[i]) ;
		return sb.toString();
	}
	
	public boolean log(Object... messages) {
		writeMessage(join(messages)) ;
		return true ;
	}
	public boolean log(String message) {
		writeMessage(message) ;
		return true ;
	}
	
	public boolean log(int lev, String message) {
		writeMessage(message) ;		
		return true ;
	}
	
	
	public void writeMessage(String message) {
		System.out.println( sdf.format(new Date())+" " +  message) ;
	}
	
}