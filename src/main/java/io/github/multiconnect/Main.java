package io.github.multiconnect;

public class Main {
	
	public static String getArg(String[] args, String val) {
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equals(val))
				return args[i + 1];
		}
		return null;
	}	
	
	public static void main(String[] args) {
		
		//disable the DNS cache
		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		System.setProperty("sun.net.inetaddr.ttl","0") ; 
		
		String configFile = getArg(args, "-config") ;
		if( configFile==null) configFile = "multi_connect.ini" ;
		
		ServiceConfig config = new ServiceConfig(configFile) ;
		config.read() ;
		
		ConnectionManager cm = new ConnectionManager(config) ;
		cm.start() ;
		
	}
}
