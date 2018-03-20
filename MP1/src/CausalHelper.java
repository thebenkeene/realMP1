import java.util.ArrayList;

public class CausalMessage implements java.io.Serializable {
	private ArrayList<Integer> timestamp;
	private String message;
	private int source;
	private MetaData data;
	
	public CausalMessage(String msg, ArrayList<Integer> time, int source, MetaData data) {
		this.timestamp = time;
		message = msg;
		this.source = source;
		this.data = data;
	}
	
	public ArrayList<Integer> getTimestamp() {
		return this.timestamp;
	}
	
	public void setTimestamp(ArrayList<Integer> arr) {
		this.timestamp = arr;
	}
	
	public String getMessage() {
		return this.message;
	}
	
	public int getSource() {
		return this.source;
	}
	
	public MetaData getMetaData() {
		return this.data;
	}
	
	public void setMetaData(MetaData d) {
		this.data = d;
	}
}
