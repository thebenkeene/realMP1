public class totalM implements java.io.Serializable {
	private String message;
	private int source;
	private Data data;
	private int id;
	
	public totalM(String msg, int source, Data data, int id) {
		message = msg;
		this.source = source;
		this.data = data;
		this.id = id;
	}
	
	public int getId() {
		return this.id;
	}
	
	public void setId(int num) {
		this.id = num;
	}
	
	public String getMsg() {
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
