import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;


public class Causal {
   	//delays
    private int minDelay;
    private int maxDelay;
    // map to data
    private static HashMap<Integer, Data> list = new HashMap<Integer, Data>();
    
    // vector timestamp for total ordering
    private static ArrayList<Integer> v_timestamps = new ArrayList<Integer>();
    
    // queue to hold back messages for ordering
    private static HashMap<Integer, ArrayList<Message>> holdBackQueue = new HashMap<Integer, ArrayList<Message>>();
    private final static Object queueLock = new Object();
    /**
     * Print the list of processes/sockets in our list
     
    //here
    public static void printProcesses() {
        System.out.println("minDelay: " + minDelay + " maxDelay: " + maxDelay);
        for (int i = 0; i < list.size(); i++) {
            Data data = list.get(i+1);
            if (data != null) {
                String[] info = data.getProcessInfo();
                System.out.println("========================");
                System.out.println(info[0] + " " + info[1] + " " + info[2]);
                if (data.isOpen())
                    System.out.println("Socket is open");
                System.out.println("========================");
            }
            else
                System.out.println(i + " is null!");
        }
    }
    //here
    

     */
    //return time as a string (hh:mm:ss)
    
    public static String getTime() {
        return new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
    }
    
    // delays from config file
    
    public static void getDelay(String[] line) {
        String s = line[0].substring(line[0].indexOf("(") + 1);
        minDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
        s = line[1].substring(line[1].indexOf("(") + 1);
        maxDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
    }
    
    //gets input string, splits and adds to global process list
    public static void addPtoList(String input, int id) {
        String[] info = input.split(" ");
        Data data = new Data(info, null, null, false);
        list.put(id, data);
        holdBackQueue.put(id, new ArrayList<Message>());
        v_timestamps.add(id-1, 0);
    }
    
    //read in from config and gets process info
    public static void scanConfigFile(int id) {
        File file = new File("../config_file.txt");
        try {
            Scanner scanner = new Scanner(file);
            
            // get delays
            String[] line = scanner.nextLine().split(" ");
            getDelay(line);
            
            // add data to list for each process
            String input = "";
            boolean found = false;
            int num = 1;
            while (scanner.hasNext()) {
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
    
    
     // Starts up the client
  
    public static void startClient(final int id, final String serverName, final int port) {
        //		System.out.println("Starting client " + id + " at " + serverName + " on port " + port);
        (new Thread() {
            @Override
            public void run() {
                readAndSendMessages(id);
            }
        }).start();
    }
    
    //read messages in and sends to process
    public static void readAndSendMsg(int id) {
        try {
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
            String input;
            while ((input = stdIn.readLine()) != null) {
                final String message = input;
                final int clientId = id;
                (new Thread() {
                    @Override
                    public void run() {
                        if (checkUniInput(message)) {
                            int dest = Integer.parseInt(message.substring(5, 6));
                            int time = v_timestamps.get(clientId-1)+1;
                            System.out.println(time);
                            Message m = new Message(message.substring(7), time, clientId, list.get(dest));
                            sendMsg(m, true);
                        }
                        else if (checkMultiInput(message)) {
                            // if another process is multicast, send to leader (process 1)
                            // leader send messages in fifo
                            String msg = message.substring(6);
                            if (clientId != 1) {
                                int dest = 1;
                                int time = v_timestamps.get(clientId-1)+1;
                                Message m = new Message(msg, time, clientId, list.get(dest));
                                sendMsg(m, false);
                                for (int i = 1; i <= list.size(); i++) {
                                    if (i != clientId) {
                                        System.out.println("Sent " + m.getMessage() + " to process " + i + ", system time is " + getTime());
                                    }
                                }
                            }
                            // else process 1 multicast
                            else {
                                multicast(msg, clientId);
                            }
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
 		synchronized (queueLock) {
			int time = v_timestamps.get(id-1)+1;
			v_timestamps.set(id-1, time);			
			return v_timestamps;
		}
	}
    
    private static void printVTimes(ArrayList<Integer> arr) {
        synchronized (queueLock) {
            for (int i = 0; i < arr.size(); i++) {
                System.out.print(arr.get(i));
            }
            System.out.println("");
        }
    }
    
    //if get a message m, set socket and writer for data if 1st time opening
    //write the numbered object to the writer
    public static void sendMsg(CausalMessage m, boolean print) {
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
            
            
            if (print)
                System.out.println("Sent " + m.getMessage() + " to process " + dest  + ", system time is " + getTime());
        } catch (IOException e) {
            System.err.println("ERROR:");
            e.printStackTrace();
        }
    }
    
//multicast to all
    public static void multicast(String message, int source) {
        ArrayList<Integer> times = incrementTimestamp(source);
        for (int i = 0; i < list.size(); i++) {
            Data data = list.get(i+1);
            if (data != null && source != (i+1)) {
                CausalMessage m = new CausalMessage(message, times, source, data);
                sendMessage(m);
            }
        }
    }
    
 //valid?
    public static boolean checkUnicastInput(String input) {
        if (input.length() > 6 && input.substring(0, 4).equals("send")) {
            input = input.substring(5);
            return Character.isDigit(input.charAt(0)) && Character.isWhitespace(input.charAt(1));
        }
        else
            return false;
    }
    
    //valid?
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
            Message msg;
            while ((msg = (Message)in.readObject()) != null) {
                final Message m = msg;
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
        if (delayMsg(m, id)) {
            deliverMsg(m, source, s, id);
        }	
    }
    
    /**
     * Delays a message: adds in network delay
     * Calls checkTimeStamps to determine if should be delivered
     * @param time
     */
    public static boolean delayMessage(CausalMessage m, int source) {
        // Sleep for a random time to simulate network delay
        sleepTime();
        
        // FOR TESTING CAUSAL
        /*
         try {
         int destination = Integer.parseInt(m.getData().getProcessInfo()[0]);
         if (source == 1 && destination == 3) {
         System.out.println("Sleeping for 10");
         Thread.sleep(10000);
         }
         else if (source == 1 && destination == 2) {
         //				System.out.println("Sleeping for 5");
         //				Thread.sleep(5000);
         }
         else if (source == 2 && destination == 3) {
         System.out.println("Sleeping for 5");
         Thread.sleep(5000);
         }
         } catch (InterruptedException e) {
         
         }
         */
        
        
        //		System.out.println("Recieved message: " + m.getMessage());
        
        return checkTimeStamps(m, source);
    }
    
    /**
     * Compares the message M with the process vector time to determine if it should be delivered
     * If sent from process source, check if V[source] + 1 = mesg[source]
     * If true, determine if all elements in V are ≥ to elements in mesg
     * Return true if this holds, false otherwise
     * @param m
     * @param source
     * @return True if should be delivered, false if placed in queue
     */
    public static boolean checkTimeStamps(CausalMessage m, int source) {
        ArrayList<Integer> mesgTimes = m.getTimestamp();
        
        synchronized (qLock) {
            int v_time = v_times.get(source - 1);
            int mesgTime = mesgTimes.get(source - 1);
            
            printVectorTimes(v_times);
            printVectorTimes(mesgTimes);
            
        
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
            return false;
        }
    }
    
    //ack that delivered message
    //get socket info if 1st time getting info from this process
    //check holdback queue for new messages
    public static void deliverMsg(CausalMessage m, int source, Socket s) {
        String message = m.getMessage();
        
        v_times.set(source - 1, m.getTimestamp().get(source-1));
        
        System.out.println("Delivered \"" + message + "\" from process " + source + ", system time is " + getTime());
        
        checkHQueue(source);
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
            deliverMessage(msg, msg.getSource(), msg.getData().getSocket());
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
        String[] info = list.get(id).getProcessInfo();
        if (info == null)
            return;
        
        // Get the port of this process
        int port = Integer.parseInt(info[2]);
        
        // Start up the server; start up the clients as they connect
        startServer(info[1], port);
        startClient(id, info[1], Integer.parseInt(info[2]));
    }
}

    
