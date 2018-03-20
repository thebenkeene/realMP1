import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;





public class FIFO {

    // Delay integers

    private static int minDelay;
    private static int maxDelay;
    // A mapping of processId's to their corresponding metadata information
    private static HashMap<Integer, Data> list = new HashMap<Integer, Data>();

    // A vector timestamp for the current process
    private static ArrayList<Integer> v_timestamps = new ArrayList<Integer>();

    // holdBackQueue that holds the elements to be buffered and a lock for the queue
    private static HashMap<Integer, ArrayList<Message>> holdBackQueue = new HashMap<Integer, ArrayList<Message>>();
    private static final Object queueLock = new Object();

    /**
     * Print the list of processes/sockets in our list
     */
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

    /**
     * Returns the time as a string formatted as hour:minutes:seconds
     * @return
     */
    public static String getTime() {
        return new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
    }

    /**
     * Gets/sets the min and max delay given first line of the config file
     * @param line
     */
    public static void getMinMaxDelay(String[] line) {
        String s = line[0].substring(line[0].indexOf("(") + 1);
        minDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
        s = line[1].substring(line[1].indexOf("(") + 1);
        maxDelay = Integer.parseInt(s.substring(0, s.indexOf(")")));
    }

    /**
     * Given an input string, splits it into a string array and adds it to the global process list
     * @param input
     */
    public static void addProcessToList(String input, int id) {
//		System.out.println("Adding process " + id + " to list: " + input);
        String[] info = input.split(" ");
        Data data = new Data(info, null, null, false);
        list.put(id, data);
        holdBackQueue.put(id, new ArrayList<Message>());
        v_timestamps.add(id-1, 0);
    }

    /**
     * Reads in from the config file and gets all the processes' info
     * @param id
     * @return
     */
    public static void scanConfigFile(int id) {
        File file = new File("../config_file.txt");
        try {
            Scanner scanner = new Scanner(file);

            // Get the min and max delay
            String[] line = scanner.nextLine().split(" ");
            getMinMaxDelay(line);

            // Scan through config file adding MetaData to list for every process
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
                            // send 2 Hey
                            int destination = Integer.parseInt(message.substring(5, 6));
                            int time = v_timestamps.get(clientId-1)+1;
                            Message m = new Message(message.substring(7), time, clientId, list.get(destination));
                            sendMessage(m);
                        }
                        else if (checkMulticastInput(message)) {
                            // msend Hey
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
    private static int incrementTimestamp(int id) {
        int time = v_timestamps.get(id-1)+1;
        v_timestamps.set(id-1, time);
        return time;
    }

    private static void printVectorTimes(ArrayList<Integer> arr) {
        synchronized (queueLock) {
            for (int i = 0; i < arr.size(); i++) {
                System.out.print(arr.get(i));
            }
            System.out.println("");
        }
    }

    /**
     * Given a Message m, sets the socket and writer for the metadata if first time opening
     * Writes the seralized object to the writer
     * @param m
     */
    public static void sendMessage(Message m) {
        try {
            String[] destinationInfo = m.getData().getProcessInfo();
            int destination = Integer.parseInt(destinationInfo[0]);
            Data data = list.get(destination);

            if (!data.isOpen()) {
//				System.out.println("Data is not open for " + destination);
//				System.out.println("Creating socket to " + destinationInfo[1] + ", " + destinationInfo[2]);
                Socket s = new Socket(destinationInfo[1], Integer.parseInt(destinationInfo[2]));
                data.setSocket(s);
                data.setWriter(new ObjectOutputStream(data.getSocket().getOutputStream()));
                data.setOpen(true);
            }

            if (data.getWriter() == null) {
                System.out.println("Data writer is null");
//				data.setWriter(new ObjectOutputStream(data.getSocket().getOutputStream()));
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
        int time = incrementTimestamp(source);
        for (int i = 0; i < list.size(); i++) {
            Data data = list.get(i+1);
            if (data != null && source != (i+1)) {
                Message m = new Message(message, time, source, data);
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
     * Loops until every process has been connected to
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

                    // Keep looping until every MetaData is open
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
     * Once a client connects to the server, keep reading messages from the client
     * If first time connecting, gets the socket's info into the MetaData
     * @param s
     */
    public static void receiveMessages(final Socket s) {
        try {
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            Message msg;
            while ((msg = (Message)in.readObject()) != null) {
                final Message m = msg;
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
     * Prints out the message, implementing a delay if necessary
     * @param source
     * @param message
     */
    public static void unicastReceive(Message m, Socket s) {
        Data data = m.getData();
        int source = m.getSource();
        int id = Integer.parseInt(data.getProcessInfo()[0]);

        if (list.get(source).getSocket() == null)
            list.get(source).setSocket(s);

        if (delayMessage(m, id)) {
            deliverMessage(m, source, s);
        }
    }

    /**
     * Delays a message: adds in network delay and places elements in holdback queue
     * @param time
     */
    public static boolean delayMessage(Message m, int id) {
        // Sleep for a random time to simulate network delay
        sleepRandomTime();

        System.out.println("Recieved message: " + m.getMessage());

//		System.out.println("m-ID = " + m.getSource() + ", source = " + id);
        int v_time = v_timestamps.get(m.getSource() - 1);
        int mesgTime = m.getTimestamp();

//		System.out.println("v_time = " + v_time + "; mesgTime = " + mesgTime);
//		printVectorTimes(v_timestamps);

        // Implemented for unicast
        if (mesgTime == v_time) {
            return true;
        }

        // If the vector_time has come, deliver the message
        if (mesgTime == v_time + 1) {
            return true;
        }
        // Else add it to the queue
        else if (mesgTime > (v_time + 1)){
            synchronized (queueLock) {
//				System.out.println("Adding " + m.getMessage() + " to queue w/ timestamp " + mesgTime);
                holdBackQueue.get(m.getSource()).add(m);
            }
        }
        return false;
    }

    /**
     * Acknowledges delivering of the message
     * Gets the socket info if first time receiving info from this process
     * Checks the holdback queue to see if any new messages can be delivered
     * @param m
     * @param source
     * @param s
     */
    public static void deliverMessage(Message m, int source, Socket s) {
        String message = m.getMessage();

        v_timestamps.set(m.getSource() - 1, m.getTimestamp());

        System.out.println("Delivered \"" + message + "\" from process " + source + ", system time is " + getTime());

        checkHoldbackQueue(source);
    }

    /**
     * Scans through the holdback queue to determine whether any messages can be delivered
     * If so, it delivers them
     * @param source
     */
    public static void checkHoldbackQueue(int source) {
        Message msg = null;
        boolean deliver = false;

        synchronized (queueLock) {
            ArrayList<Message> msgs = holdBackQueue.get(source);
            if (msgs.size() == 0)
                return;
            for (int i = 0; i < msgs.size(); i++) {
                msg = msgs.get(i);
                int v_time = v_timestamps.get(msg.getSource() - 1);
//				System.out.println("Queue: v_time = " + v_time + "; msgTime = " + msg.getTimestamp());
                // Deliver this message
                if (msg.getTimestamp() == v_time + 1) {
                    deliver = true;
                    msgs.remove(i);
                    break;
                }
            }
        }

        if (deliver) {
            deliverMessage(msg, source, msg.getData().getSocket());
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
     * Determines whether the received message is a valid input
     * Invalid input = a string with a colon in it
     * @param input
     * @return
     */
    public static boolean invalidInput(String input) {
        int index = input.indexOf(':');
        return (index != -1 && index >= 0);
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("./process <id>");
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