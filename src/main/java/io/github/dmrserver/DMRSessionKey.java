package io.github.dmrserver ;
 
import java.net.* ;
import java.io.* ;
import java.util.* ;
import java.text.* ;


public class DMRSessionKey {
	long ts = 0 ;

	byte[] address ;
	int port ;

	public DMRSessionKey(InetAddress ia, int port)  {
		this.address = ia.getAddress() ;
		this.port = port ;
	}

	public void touch() {
		ts = System.currentTimeMillis() ;
	}

    @Override
	public int hashCode() {
		int ret = 0;
		for(int i=0;i<address.length;i++) 
			ret ^= ((address[i]&0xff) << 8*(i%4) ) ;
		return ret ^ port ;
	}

    @Override
    public boolean equals(Object o) {
    	if( ! (o instanceof DMRSessionKey)) return false ;   	
    	DMRSessionKey ds = (DMRSessionKey) o ;
    	if( port!=ds.port  || address.length!=ds.address.length) return false ;
		for(int i=0;i<address.length;i++) 
			if( address[i]!=ds.address[i]) return false;
		return true;
    }

    public String toString() {
    	return "DMRSessionKey " +DMRDecode.hex(address,0,address.length)+":"+port;
    }
}
