import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;


public class Causal {
   	//delays
    private static int minDelay;
    private static int maxDelay;
    // map to data
    private static HashMap<Integer, Data> list = new HashMap<Integer, Data>();
    
    // vector timestamp for total ordering
    private static ArrayList<Integer> v_times = new ArrayList<Integer>();
    
    // queue to hold back messages for ordering
    private static HashMap<Integer, ArrayList<CausalMessage>> holdBackQueue = new HashMap<Integer, ArrayList<CausalMessage>>();
    private final static Object qLock = new Object();
    
    
    //Gets the current time
    public static String getTime() {
        return new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
    }
    
    
    
    //Sets the delays from the config.txt file
    public static void getDelay(String[] line) {
        String s = line[0].substring(line[0].indexOf("(") + 1);
        minDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
        s = line[1].substring(line[1].indexOf("(") + 1);
        maxDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
    }

     // Sets the input string given a spefic id and adds it to the process list
    public static void addPtoList(String input, int id) {
        String[] info = input.split(" ");
        Data data = new Data(info, null, null, false);
        list.put(id, data);
        holdBackQueue.put(id, new ArrayList<CausalMessage>());
        v_times.add(id-1, 0);
    }
    
//scans the config file
    public static void scanConfigFile(int id) {
        File file = new File("./config.txt");
        try {
            Scanner scanner = new Scanner(file);
            
            // get delays
            String[] line = scanner.nextLine().split(" ");  //first line is delays
            getDelay(line);
            
            // add data to list for each process
            String input = "";
            boolean found = false;
            int num = 1;
            while (scanner.hasNext()) {
                //while there is next line
                if (num == id) {
                    input = scanner.nextLine();
                    addPtoList(input, id);
                    found = true;
                }
                else {
                    input = scanner.nextLine();
                    addPtoList(input, Integer.parseInt(input.substring(0, 1)));
                }
                num++;
            }
            scanner.close();
            if (!found)
                System.err.println("Invalid process ID!");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
    


     //Starts the client from server with id and port
 
    public static void startClient(final int id, final String serverName, final int port) {
        //		System.out.println("Starting client " + id + " at " + serverName + " on port " + port);
        (new Thread() {
            @Override
            public void run() {
                readAndSendMsg(id);
            }
        }).start();
    }
    
 
     //Reads message and is sent to process
     
    public static void readAndSendMsg(int id) {
        try {
    		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        	String input;
//        	boolean exit = false;
			while ((input = stdIn.readLine()) != null) {
//				/*
				final String message = input;
				final int clientId = id;
		        (new Thread() {
		            @Override
		            public void run() {
						if (checkUnicastInput(message)) {
							int destination = Integer.parseInt(message.substring(5, 6));
							ArrayList<Integer> time = v_times;
							CausalMessage m = new CausalMessage(message.substring(7), time, clientId, list.get(destination));
							sendMsg(m);
						}
						else if (checkMulticastInput(message)) {
							String msg = message.substring(6);
							multicast(msg, clientId);
						}
						else if (!message.isEmpty()) {
							System.err.println("msend <message>");
						}
		            }
		        }).start();
			}
			System.err.println("Closing client " + id);
			stdIn.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    
    // increment timestamp of process ID
    
    private static  ArrayList<Integer> incTimestamp(int id) {
 		synchronized (qLock) {
			int time = v_times.get(id-1)+1;
			v_times.set(id-1, time);			
			return v_times;
		}
	}
    
    private static void printVTimes(ArrayList<Integer> arr) {
        synchronized (qLock) {
            for (int i = 0; i < arr.size(); i++) {
                System.out.print(arr.get(i));
            }
            System.out.println("");
        }
    }
    
    //if get a message m, set socket and writer for data if 1st time opening
    //write the numbered object to the writer
    public static void sendMsg(CausalMessage m) {
        try {
            String[] destInfo = m.getData().getPInfo();
            int dest = Integer.parseInt(destInfo[0]);
            Data data = list.get(dest);
            
            if (!data.isOpen()) {
                Socket s = new Socket(destInfo[1], Integer.parseInt(destInfo[2]));
                data.setSocket(s);
                data.setWriter(new ObjectOutputStream(data.getSocket().getOutputStream()));
                data.setOpen(true);
            }
            
            if (data.getWriter() == null) {
                System.out.println("Data writer is null");
            }
            
            data.getWriter().reset();
            data.getWriter().writeObject(m);
            data.getWriter().flush();
            
            
            
            System.out.println("Sent " + m.getMessage() + " to process " + dest  + ", system time is " + getTime());
        } catch (IOException e) {
            System.err.println("ERROR:");
            e.printStackTrace();
        }
    }
    
//multicast to all
    public static void multicast(String m, int source) {
        ArrayList<Integer> times = incTimestamp(source);
        for (int i = 0; i < list.size(); i++) {
            Data data = list.get(i+1);
            if (data != null && source != (i+1)) {
                CausalMessage message = new CausalMessage(m, times, source, data);
                sendMsg(message);
            }
        }
    }
    
 //checks if valid
    public static boolean checkUnicastInput(String input) {
        if (input.length() > 6 && input.substring(0, 4).equals("send")) {
            input = input.substring(5);
            return Character.isDigit(input.charAt(0)) && Character.isWhitespace(input.charAt(1));
        }
        else
            return false;
    }
    
    //checks if valid
    public static boolean checkMulticastInput(String input) {
        if (input.length() >= 6 && input.substring(0, 5).equals("msend")) {
            input = input.substring(5);
            return Character.isWhitespace(input.charAt(0));
        }
        else
            return false;
    }
    
    
    
    //start server in a new thread and loop until every process connected
    public static void startServer(String serverName, final int port) {
        (new Thread() {
            @Override
            public void run() {
                ServerSocket ss;
                try {
                    ss = new ServerSocket(port);
                    
                    // loop until all data is open
                    while (true) {
                        final Socket s = ss.accept();
                        
                        // new thread for each connection
                        (new Thread() {
                            @Override
                            public void run() {
                                receiveMsg(s);
                            }
                        }).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    //client connects to server, read messages from client
    // if 1st time connecting, get socket's info into data
    public static void receiveMsg(final Socket s) {
        try {
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            CausalMessage msg;
            while ((msg = (CausalMessage)in.readObject()) != null) {
                final CausalMessage m = msg;
                //new thread for each message
                (new Thread() {
                    @Override
                    public void run() {
                        uniReceive(m, s);
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
  //print out message, add delay if needed
    public static void uniReceive(CausalMessage m, Socket s) {
        Data data = m.getData();
        int source = m.getSource();
        int id = Integer.parseInt(data.getPInfo()[0]);
        if (list.get(source).getSocket() == null)
            list.get(source).setSocket(s);
        if (delayMessage(m, id)) {
            deliverMsg(m, source, s);
        }	
    }
    
  
     //Delays a message
     //Calls checkTimeStamps to see if it should deliver
    public static boolean delayMessage(CausalMessage m, int source) {
        // Sleep for a random time to simulate network delay
        sleepTime();
        return checkTimeStamps(m, source);
    }
    
   //compares with vector time to see if it should be delivered
    public static boolean checkTimeStamps(CausalMessage m, int source) {
        ArrayList<Integer> mesgTimes = m.getTimestamp();
        
        synchronized (qLock) {
            int v_time = v_times.get(source - 1);
            int mesgTime = mesgTimes.get(source - 1);
            
            printVTimes(v_times);
            printVTimes(mesgTimes);
            
        
            int greater = 0;
            int less = 0;
            if (mesgTime == (v_time + 1)) {
                for (int i = 0; i < mesgTimes.size(); i++) {
                    if (i != source - 1) {
                        if (v_times.get(i) < mesgTimes.get(i))
                            greater++;
                        else if (v_times.get(i) > mesgTimes.get(i))
                            less++;
                    }
                }
                if (greater >= 1 && less >= 1) {
             
                    return true;
                }
                else if (greater == 0) {
                    return true;
                }
            }
            
            //System.out.println("Adding to queue at " + source);
            
            holdBackQueue.get(source).add(m);
            checkHQueue(source);
            return false;
        }
    }
    
    //ack that delivered message
    //get socket info if 1st time getting info from this process
    //check holdback queue for new messages
    public static void deliverMsg(CausalMessage m, int src, Socket s) {
        String message = m.getMessage();
        
        v_times.set(src - 1, m.getTimestamp().get(src-1));
        
        System.out.println("Delivered \"" + message + "\" from process " + src + ", system time is " + getTime());
        
        checkHQueue(src);
    }
    
   	// check holdback queue for messages to deliver
    public static void checkHQueue(int id) {
        CausalMessage msg = null;
        boolean deliver = false;
        synchronized (qLock) {
            for (int i = 0; i < holdBackQueue.size(); i++) {
                ArrayList<CausalMessage> msgs = holdBackQueue.get(i+1);
                if (msgs != null && msgs.size() > 0) {
                    for (int j = 0; j < msgs.size(); j++) {
                        msg = msgs.get(j);
                        int v_time = v_times.get(msg.getSource() - 1);
                        System.out.println("Queue: v_time = " + v_time + "; msgTime = " + msg.getTimestamp());
                        
                       
                        if (msg.getTimestamp().get(i) == (v_time + 1) && checkTimeStamps(msg, msg.getSource())) {
                            deliver = true;
                            msgs.remove(j);
                            break;
                        }
                    }
                }
            }
        }
        
        if (deliver) {
            System.out.println("I'm here");
            deliverMsg(msg, msg.getSource(), msg.getData().getSocket());
        }
        
    }
    
   
    public static void sleepTime() {
        int random = minDelay + (int)(Math.random() * (maxDelay - minDelay + 1));
        
        try {
            Thread.sleep(random);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }		
    }
    
    
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("./process <id>");
            return;
        }
        
        // Get the process ID number
        int id = Integer.parseInt(args[0]);
        
        // Read in the config file
        scanConfigFile(id);
        
        // Get the process info
        String[] info = list.get(id).getPInfo();
        if (info == null)
            return;
        
        // Get the port of this process
        int port = Integer.parseInt(info[2]);
        
        // Start up the server; start up the clients as they connect
        startServer(info[1], port);
        startClient(id, info[1], Integer.parseInt(info[2]));
    }
}

    
