/**
 * Created by BigBen on 3/19/18.
 */

public class SocketManager
{
    // initialize socket and input output streams
    private Socket socket            = null;
    private DataInputStream  input   = null;
    private DataOutputStream out     = null;
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
