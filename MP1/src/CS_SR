import java.net.*;
import java.io.*;

public class SocketTest {
    public static void main(String[] args) throws IOException {
        
        startServer();
        startSender();
        //loop
    }
    
    public static void startSender() {
        (new Thread() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket(address, port);
                  //finish
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
                                                           new InputStreamReader(s.getInputStream()));
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

