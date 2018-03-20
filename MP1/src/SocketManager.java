/**
 * Created by BigBen on 3/19/18.
 */

    // A Java program for a Client
import java.net.*;
import java.io.*;

    public class SocketManager
    {
        // initialize socket and input output streams
    	private List<Socket> list;
    	//private List<SocketAddress> addresses;			//Not neaded

        // constructor to put ip address and port
        public Socketmanager()
        {
        	
        	list = new ArrayList<>();
        	
        }
        
        public void add(Socket socket){
        	
        	//Adds a connection from all the existings sockets to the new socket both ways
        	for(Socket s: list){
        		s.connect(socket.getLocalSocketAddress());		//connection from existing to new
        		socket.connect(s.getLocalSocketAddress());		//connection from new to existing
        	}
        	
        	list.add(socket);	//adds the socket to the list

        }
    }
}

