/**
 * Created by BigBen on 3/19/18.
 */
import java.io.ObjectOutputStream;
import java.net.Socket;


public class Data implements java.io.Serializable {
    private String[] process;
    private transient Socket socket;
    private transient ObjectOutputStream out;
    private boolean open;

    public Data(String[] process, Socket socket, ObjectOutputStream out, boolean open) {
        this.process = process;
        this.socket = socket;
        this.out = out;
        this.open = open;
    }

    public String[] getProcessInfo() {
        return this.process;
    }

    public void setProcessInfo(String[] info) {
        this.process = info;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public void setSocket(Socket sock) {
        this.socket = sock;
    }

    public ObjectOutputStream getWriter() {
        return this.out;
    }

    public void setWriter(ObjectOutputStream writer) {
        this.out = writer;
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean bool) {
        this.open = bool;
    }
}
