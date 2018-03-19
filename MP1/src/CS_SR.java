import java.net.*;
import java.io.*;
import java.util.*;

public class SocketTest {
	private String min_delay;
	private String max_delay;
		
    public static void main(String[] args) throws IOException {
        
        startServer();
        startSender();
        
        //loop
        Scanner scanner = new Scanner(args);
        if (scanner.hasNextLine()){
        	String[] delays = scanner.nextLine().split("\\s");
        	min_delay = delays[0];
        	max_delay = delays[1];
        }
        	
        
        while(scanner.hasNextLine()){
        	String line = scanner.nextLine();
        	String[] tokens = line.split("\\s");
        	
        	String id = token[0];
        	String ip = token[1];
        	String port = token[2];
        	
        	//create new object with info
        }
    }
    
    public static void startSender() {
        (new Thread() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket(address, port);
                    
                    //to do inputstream?
                }
            }
        }).start();
    }
    
    public static void startServer() {
        (new Thread() {
            @Override
            public void run() {
                ServerSocket ss;
                try {
                
                    ss = new ServerSocket(port);
                    
                    Socket s = ss.accept();
                    
                    BufferedReader in = new BufferedReader(
                                                           new InputStreamReader(s.getInputStream()) );
                    String line = null;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        //need s.close();
    }
}

