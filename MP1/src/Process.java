import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * MP1: Sara Roth, Courtney Severance, R. Sinclair Jones, and Ben Keene
 *
 */
public class Process {

	// Min and max delays for the delay
	private static int minDelay;
	private static int maxDelay;
	// A mapping of processId's to their corresponding Data information
	private static HashMap<Integer, Data> list = new HashMap<Integer, Data>();

	// Whether the process has closed yet 
	private static boolean closed = false;
	
	private static HashMap<Integer, ArrayList<	Message>> holdBackQueue = new HashMap<Integer, ArrayList<Message>>();
	private static final Object queueLock = new Object();
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("./process <id>");
		}
		
		// Get the process ID number
		int process = Integer.parseInt(args[0]);
		
		// Read in the config file
		scanConfigFile(process);
		
		// Get the current process information from id; if not found, return
		String[] info = list.get(process).getPInfo();
		if (info == null)
			return;
		
		// Get the port of this process
		int port = Integer.parseInt(info[2]);
		
		// Start up the server; start up the clients as they connect
		startServer(info[1], port);
		startClient(process, info[1], Integer.parseInt(info[2]));
	}
	
	/**
	 * Reads in from the config file and gets all the processes' info
	 * @param id
	 * @return
	 */
	public static void scanConfigFile(int process) {
		File file = new File("./config.txt");
		try {
			Scanner scanner = new Scanner(file);
			
			// Get the min and max delay
			String[] line = scanner.nextLine().split(" ");
			getMinMaxDelay(line);

			// Scan through config file adding Data to list for every process
			String input = "";
			boolean found = false;
			int num = 1;
			while (scanner.hasNext()) {
				// If the current process, set found to true
				if (num == process) {
					input = scanner.nextLine();
					
					String[] info = input.split(" ");
					Data data = new Data(info, null, null, false);
					list.put(process, data);
					holdBackQueue.put(process, new ArrayList<Message>());
					
					found = true;
				}
				else {
					input = scanner.nextLine();
					
					int id = Integer.parseInt(input.substring(0, 1));
					String[] info = input.split(" ");
					Data data = new Data(info, null, null, false);
					list.put(id, data);
					holdBackQueue.put(id, new ArrayList<Message>());
					
				}
				num++;
			}
			scanner.close();
			if (!found)
				System.err.println("Process does not exist!");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Client set up on a new thread
	 * @param process
	 * @param server
	 * @param port
	 */
	public static void startClient(int process, String server, int port) {
		System.out.println("On server: " + server + ". With ID: " + process + ". On port: " + port);
        (new Thread() {
            @Override
            public void run() {
            	readAndSendMessages(process);
            }
        }).start();
	}
	
	/**
	 * Server set up on a new thread
	 * Loops until every process has been connected to
	 * @param server
	 * @param port
	 */
	public static void startServer(String server, int port) {
        (new Thread() {
            @Override
            public void run() {
                ServerSocket serverSocket;
                try {
                    serverSocket = new ServerSocket(port);
                    
                    // Keep looping until everything is open 
                    while (!closed) {
	                    Socket socket = serverSocket.accept();
	                    
	                    // Create a new thread for each connection
	                    (new Thread() {
	                    	@Override
	                    	public void run() {
	                    		receiveMessages(socket);
	                    	}
	                    }).start();
                    }
                    System.err.println("Server is closing.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
	}

	/**
	 * Reads messages in from stdIn and sends them to the correct process
	 * @param id
	 */
	public static void readAndSendMessages(int id) {
    	try {
    		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        	String input;
//        	boolean exit = false;
			while ((input = bufferedReader.readLine()) != null) {
//				/*
				final String message = input;
				final int clientId = id;
		        (new Thread() {
		            @Override
		            public void run() {
						if (checkUnicastInput(message)) {
							// send 2 Hey
							int destination = Integer.parseInt(message.substring(5, 6));
							Message m = new Message(message.substring(7), 1, clientId, list.get(destination));
							sendMessage(m);
						}
						else if (checkMulticastInput(message)) {
							// msend Hey
							String msg = message.substring(6);
							multicast(msg, clientId);
						}
						else if (!message.isEmpty()) {
							System.err.println("send <#> <message>");
						}
		            }
		        }).start();
			}
			System.err.println("Closing client " + id);
			bufferedReader.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
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
	
	
	/*
	private static void printVectorTimes(ArrayList<Integer> arr) {
		synchronized (queueLock) {
			for (int i = 0; i < arr.size(); i++) {
				System.out.print(arr.get(i));
			}
			System.out.println("");
		}
	}
	*/
	
	/**
	 * Given a Message m, sets the socket and writer for the Data if first time opening
	 * Writes the seralized object to the writer
	 * @param m
	 */
	public static void sendMessage(Message m) {
		try {
			String[] destinationInfo = m.getData().getPInfo();
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
		for (int i = 0; i < list.size(); i++) {
			Data data = list.get(i+1);
			if (data != null && source != (i+1)) {
				Message m = new Message(message, 1, source, data);
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
	 * Once a client connects to the server, keep reading messages from the client
	 * If first time connecting, gets the socket's info into the Data
	 * @param s
	 */
	public static void receiveMessages(final Socket socket) {
		try {
			ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
	        Message msg;
			while (!closed && (msg = (Message)inputStream.readObject()) != null) {
				final Message m = msg;
                // Create a new thread for each message
                (new Thread() {
                	@Override
                	public void run() {
                		unicastReceive(m, socket);
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
	public static void unicastReceive(Message message, Socket socket) {
		Data data = message.getData();
		int source = message.getSource();
		int id = Integer.parseInt(data.getPInfo()[0]);
		
		if (list.get(source).getSocket() == null)
			list.get(source).setSocket(socket);
		
		if (delayMessage(message, id)) {
			deliverMessage(message, source, socket);
		}	
	}
	
	/**
	 * Delays a message: adds in network delay and places elements in holdback queue 
	 * @param time
	 */
	public static boolean delayMessage(Message m, int id) {
		// Sleep for a random time to simulate network delay
		sleepTime();
		
		System.out.println("Recieved message: " + m.getMessage());
		
		return true;
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
		
		System.out.println("Delivered \"" + message + "\" from process " + source + ", system time is " + getTime());
	}
	
	/**
	 * Sleeps the current thread for a random amount of time bounded my min and max delay
	 */
	public static void sleepTime() {
		int random = minDelay + (int)(Math.random() * (maxDelay - minDelay + 1));
		
		try {
			Thread.sleep(random);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}		
	}
	
	
}