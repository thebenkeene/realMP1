import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Samir Chaudhry
 *
 */
public class CausalProcess {
    private static int minDelay;
    private static int maxDelay;
    
    private HashMap<Integer, Data> list = new HashMap<Integer, Data>();//holds process data info
    
    
    private ArrayList<Integer> v_times = new ArrayList<Integer>();//get time
    
    
    private HashMap<Integer, ArrayList<CausalMessage>> holdBackQueue = new HashMap<Integer, ArrayList CausalMessage>>();//queue to hold messages and ensure Causal ordering
    private final Object qLock = new Object();
    
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
    public String getTime() { //gets time for message
        return new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
    }
    
    
    public static void getMinMaxDelay(String[] line) {//incorporates delays from config file
        String s = line[0].substring(line[0].indexOf("(") + 1);
        minDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
        
        s = line[1].substring(line[1].indexOf("(") + 1);
        maxDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
    }
    
   //compare here
    public static void addProcessToList(String input, int id) {//adding info to global data
        String[] info = input.split(" ");
        Data data = new Data(info, null, null, false);
        list.put(id, data);
        holdBackQueue.put(id, new ArrayList<CausalMessage>());
        v_times.add(id-1, 0);
    }
    
   //compare here
    public static void scanConfigFile(int id) {
        File file = new File("../config_file.txt");
        try {
            Scanner scanner = new Scanner(file);
            
            // Get the min and max delay
            String[] line = scanner.nextLine().split(" ");
            getMinMaxDelay(line);
            
            // Scan through config file adding
            Data to list for every process
            String input = "";
            boolean found = false;
            int num = 1;
            while (scanner.hasNext()) {
                if (num == id) {
                    input = scanner.nextLine();
                    addProcessToList(input, id);
                    found = true;
                }
                else {
                    input = scanner.nextLine();
                    addProcessToList(input, Integer.parseInt(input.substring(0, 1)));
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
    
    /**
     * Starts up the client
     * @param id
     * @param serverName
     * @param port
     */
    public static void startClient(final int id, final String serverName, final int port) {
        //		System.out.println("Starting client " + id + " at " + serverName + " on port " + port);
        (new Thread() {
            @Override
            public void run() {
                readAndSendMessages(id);
            }
        }).start();
    }
    
    /**
     * Reads messages in from stdIn and sends them to the correct process
     * @param id
     */
    public static void readAndSendMessages(int id) {
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
                            sendMessage(m);
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
    
    /**
     * Increments the vector timestamp of process ID by one
     * @param id
     * @return
     */
    private static ArrayList<Integer> incrementTimestamp(int id) {
        synchronized (qLock) {
            int time = v_times.get(id-1)+1;
            v_times.set(id-1, time);
            //			System.out.println("Printing new timestamp");
            //			printVectorTimes(v_timestamps);
            
            return v_times;
        }
    }
    
    private static void printVectorTimes(ArrayList<Integer> arr) {
        synchronized (qLock) {
            for (int i = 0; i < arr.size(); i++) {
                System.out.print(arr.get(i));
            }
            System.out.println("");
        }
    }
    
    /**
     * Given a Message m, sets the socket and writer for the data if first time opening
     * Writes the seralized object to the writer
     * @param m
     */
    public static void sendMessage(CausalMessage m) {
        try {
            String[] destinationInfo = m.getData().getProcessInfo();
            int destination = Integer.parseInt(destinationInfo[0]);
            Data data = list.get(destination);
            
            if (!data.isOpen()) {
                Socket s = new Socket(destinationInfo[1], Integer.parseInt(destinationInfo[2]));
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
            
            System.out.println("Sent " + m.getMessage() + " to process " + destination  + ", system time is " + getTime());
        } catch (IOException e) {
            System.err.println("ERROR:");
            e.printStackTrace();
        }
    }
    
    /**
     * Multicasts a message to every process
     * i is the id of each process ID
     * @param message
     */
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
    
    /**
     * Checks whether the message input is a valid unicast input
     * A valid input is of the form: send <#> <message>
     * @param input
     * @return
     */
    public static boolean checkUnicastInput(String input) {
        if (input.length() > 6 && input.substring(0, 4).equals("send")) {
            input = input.substring(5);
            return Character.isDigit(input.charAt(0)) && Character.isWhitespace(input.charAt(1));
        }
        else
            return false;
    }
    
    /**
     * Checks whether the message input is a valid multicast input
     * A valid input is of the form: msend <message>
     * @param input
     * @return
     */
    public static boolean checkMulticastInput(String input) {
        if (input.length() >= 6 && input.substring(0, 5).equals("msend")) {
            input = input.substring(5);
            return Character.isWhitespace(input.charAt(0));
        }
        else
            return false;
    }
    
    /**
     * Starts the server in a new thread
     * Loop until every process has been connected to ---- removed for now
     * @param serverName
     * @param port
     */
    public static void startServer(String serverName, final int port) {
        (new Thread() {
            @Override
            public void run() {
                ServerSocket ss;
                try {
                    ss = new ServerSocket(port);
                    
                    // Keep looping until every Data is open
                    while (true) {
                        final Socket s = ss.accept();
                        
                        // Create a new thread for each connection
                        (new Thread() {
                            @Override
                            public void run() {
                                receiveMessages(s);
                            }
                        }).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    /**
     * When a client connects, create an input stream and keep reading messages in
     * @param s
     */
    public static void receiveMessages(final Socket s) {
        try {
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            CausalMessage msg;
            while (true && (msg = (CausalMessage)in.readObject()) != null) {
                final CausalMessage m = msg;
                // Create a new thread for each message
                (new Thread() {
                    @Override
                    public void run() {
                        unicastReceive(m, s);
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Receives the message, adds delay if necessary, and delivers it if ready
     * @param source
     * @param message
     */
    public static void unicastReceive(CausalMessage m, Socket s) {
        Data data = m.getData();
        int source = m.getSource();
        int id = Integer.parseInt(data.getProcessInfo()[0]);
        
        if (list.get(source).getSocket() == null)
            list.get(source).setSocket(s);
        
        if (delayMessage(m, source)) {
            deliverMessage(m, source, s);
        }
    }
    
    /**
     * Delays a message: adds in network delay
     * Calls checkTimeStamps to determine if should be delivered
     * @param time
     */
    public static boolean delayMessage(CausalMessage m, int source) {
        // Sleep for a random time to simulate network delay
        sleepRandomTime();
        
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
            
            // If Vj[i] = Vi[i] + 1
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
                    //					System.err.println("Concurrent");
                    return true;
                }
                else if (greater == 0) {
                    return true;
                }
            }
            
            System.out.println("Adding to queue at " + source);
            
            holdBackQueue.get(source).add(m);
            return false;
        }
    }
    
    /**
     * Acknowledges delivering of the message, sets its vector timestamp
     * Gets the socket info if first time receiving info from this process
     * Checks the holdback queue to see if any new messages can be delivered
     * @param m
     * @param source
     * @param s
     */
    public static void deliverMessage(CausalMessage m, int source, Socket s) {
        String message = m.getMessage();
        
        v_times.set(source - 1, m.getTimestamp().get(source-1));
        
        System.out.println("Delivered \"" + message + "\" from process " + source + ", system time is " + getTime());
        
        checkHoldbackQueue(source);
    }
    
    /**
     * Scans through the holdback queue to determine whether any messages can be delivered
     * Determines if a message can be delivered by checkTimeStamps()
     * If so, it delivers them
     * @param source
     */
    public static void checkHoldbackQueue(int id) {
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
                        
                        // Deliver this message
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
    
    /**
     * Sleeps the current thread for a random amount of time bounded my min and max delay
     */
    public static void sleepRandomTime() {
        int random = minDelay + (int)(Math.random() * (maxDelay - minDelay + 1));
        
        try {
            Thread.sleep(random);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }		
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("./process <id>");
            return;
        }
        
        // Get the process ID number
        int id = Integer.parseInt(args[0]);
        
        // Read in the config file
        scanConfigFile(id);
        
        // Get the current process information from id; if not found, return
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

    
