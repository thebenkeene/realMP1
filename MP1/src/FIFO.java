import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;





public class FIFO {

    
    //delays
    private static int minDelay;
    private static int maxDelay;
    // map to data
    private static HashMap<Integer, Data> list = new HashMap<Integer, Data>();
    
    // vector timestamp for total ordering
    private static ArrayList<Integer> v_timestamps = new ArrayList<Integer>();
    
    // queue to hold back messages for ordering
    private static HashMap<Integer, ArrayList<Message>> holdBackQueue = new HashMap<Integer, ArrayList<Message>>();
    private static final Object queueLock = new Object();
    
    // printlist of processes/sockets
     
    public static void printP() {
        System.out.println("minDelay: " + minDelay + " maxDelay: " + maxDelay);
        for (int i = 0; i < list.size(); i++) {
            Data data = list.get(i+1);
            if (data != null) {
                String[] info = data.getPInfo();
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
        File file = new File("./config.txt");
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

    //starts up the client
    public static void startClient(final int id, final String serverName, final int port) {
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

    
    private static int incTimestamp(int id) {
        int time = v_timestamps.get(id-1)+1;
        v_timestamps.set(id-1, time);
        return time;
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
    public static void sendMessage(Message m) {
        try {
            String[] destinationInfo = m.getData().getPInfo();
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


    //from in class algorithm: If a correct node issues multicast(g,m) and then multicast(g,m’), 
    // then every correct node that delivers m’ will have already delivered m.
    public static void multicast(String message, int source) {
        // leader multicast
        // increment 1st process timestamp
        int time = incTimestamp(source);
        for (int i = 0; i < list.size(); i++) {
            Data data = list.get(i+1);
            if (data != null && source != (i+1)) {
                Message m = new Message(message, time, source, data);
                sendMessage(m);
            }
        }
    }

    
    //check if message input is valid (send <#> <message>)
    public static boolean checkUnicastInput(String input) {
        if (input.length() > 6 && input.substring(0, 4).equals("send")) {
            input = input.substring(5);
            return Character.isDigit(input.charAt(0)) && Character.isWhitespace(input.charAt(1));
        }
        else
            return false;
    }

    //checks if message input is valid (msend <message>)
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

                    // Keep looping until every MetaData is open
                    while (true) {
                        final Socket s = ss.accept();

                        // Create a new thread for each connection
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
    public static void uniReceive(Message m, Socket s) {
        Data data = m.getData();
        int source = m.getSource();
        int id = Integer.parseInt(data.getPInfo()[0]);

        if (list.get(source).getSocket() == null)
            list.get(source).setSocket(s);

        if (delayMsg(m, id)) {
            deliverMsg(m, source, s);
        }
    }

    //delay message and put elements in holdback queue 
    public static boolean delayMsg(Message m, int id) {
        // sleep for random time for delay
        sleepRTime();

        System.out.println("Recieved message: " + m.getMessage());


        int v_time = v_timestamps.get(m.getSource() - 1);
        int mesgTime = m.getTimestamp();



        // For unicast
        if (mesgTime == v_time) {
            return true;
        }

        // If correct time, deliver
        if (mesgTime == v_time + 1) {
            return true;
        }
        // or add to queue
        else if (mesgTime > (v_time + 1)){
            synchronized (queueLock) {

                holdBackQueue.get(m.getSource()).add(m);
            }
        }
        return false;
    }

   //ack that delivered message
    //get socket info if 1st time getting info from this process
    //check holdback queue for new messages

    public static void deliverMsg(Message m, int source, Socket s) {
        String message = m.getMessage();

        v_timestamps.set(m.getSource() - 1, m.getTimestamp());

        System.out.println("Delivered \"" + message + "\" from process " + source + ", system time is " + getTime());

        checkHoldbackQueue(source);
    }

   // check holdback queue for messages to deliver

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
//
                // Deliver this message it
                if (msg.getTimestamp() == v_time + 1) {
                    deliver = true;
                    msgs.remove(i);
                    break;
                }
            }
        }

        if (deliver) {
            deliverMsg(msg, source, msg.getData().getSocket());
        }

    }

    //sleep thread for random time bounded delays
    public static void sleepRTime() {
        int random = minDelay + (int)(Math.random() * (maxDelay - minDelay + 1));

        try {
            Thread.sleep(random);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //check if received message is valid 
    //invalid if = a string with a colon in it
    public static boolean invalidInput(String input) {
        int index = input.indexOf(':');
        return (index != -1 && index >= 0);
    }

    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("./process <id>");
        }

        // get the process ID
        int id = Integer.parseInt(args[0]);

       // get config file
        scanConfigFile(id);

        // get current process info from id or return
        String[] info = list.get(id).getPInfo();
        if (info == null)
            return;

        // get port of process
        int port = Integer.parseInt(info[2]);

       // start up server and clients
        startServer(info[1], port);
        startClient(id, info[1], Integer.parseInt(info[2]));
    }
}