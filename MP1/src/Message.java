/**
*
* Message Class
*
**/


public class Message implements java.io.Serializable {
    private int timestamp;
    private String message;
    private int source;
    private Data data;
    
    public Message(int time, String message, int id) {
        this.timestamp = time;
        this.message = message;
        this.source = id;
        data = null;
    }
    
    public Message(String message, int time, int source, Data data) {
        this.timestamp = time;
        this.message = message;
        this.source = source;
        this.data = data;
    }
    
    public int getTimestamp() {
        return this.timestamp;
    }
    
    public String getMessage() {
        return this.message;
    }
    
    public int getSource() {
        return this.source;
    }
    
    public Data getData() {
        return this.data;
    }
    
    public void setData(Data d) {
        this.data = d;
    }
}