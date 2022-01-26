package io.github.dmrserver ;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;

public class DMRCapture implements Serializable {
    private static final long serialVersionUID = 0L;

	InetAddress host ;
	int port ;
	ArrayList<DMRRecord> store ;

	public DMRCapture() {
		host=null;
		port=0;
		store = new ArrayList<>() ;
	}

	public ArrayList<DMRRecord> getStore() {
		return store ;
	}

	public InetAddress getHost() {
		return host;
	}

	public int getPort() {
		return port ;
	}

	public int getFrameCount() {
		return store.size() ;
	}

	public void add(DatagramPacket dp) {

		if(port==0){
			port = dp.getPort() ;
			host = dp.getAddress() ;
		}
		store.add( new DMRRecord(dp.getData(), dp.getLength() ) ) ;
	}


	public static DMRCapture read(String fname)  {
		DMRCapture ret = null ;
		try {
			FileInputStream fos = new FileInputStream(fname) ;
	        ObjectInputStream oos = new ObjectInputStream(fos);
	        ret = (DMRCapture) oos.readObject();
	        oos.close();		
	        fos.close() ;
	        System.out.println( "read file: "+fname +" size:"+ret.getFrameCount()) ;	
		}
		catch(Exception ex) {
			Logger.handleException(ex) ;
		}
		return ret ;
	}

	public void log(String fname)  {
		try {
			FileOutputStream fos = new FileOutputStream(fname) ;
	        ObjectOutputStream oos = new ObjectOutputStream(fos);
	        oos.writeObject(this);
	        oos.close();		
	        fos.close() ;
	        System.out.println( "output file: "+fname) ;	
		}
		catch(Exception ex) {
			Logger.handleException(ex) ;
		}
	}
}
