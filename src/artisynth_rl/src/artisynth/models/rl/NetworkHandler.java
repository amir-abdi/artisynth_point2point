package artisynth.models.rl;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.hadoop.io.DataOutputOutputStream;
import org.json.JSONObject;

public class NetworkHandler extends Thread {
	private Socket socket = null;
	NetworkReceiveHanlder networkReceiveHandler; 
//	PrintWriter out;
	DataOutputStream out;
	int port;

	public NetworkHandler(int port) {
		this.port = port;
	}

	public NetworkHandler() {
		this(4545);
	}

	public void run() {
		while (true) {
			Log.log("Running the network handlder");
			try {
				ServerSocket listener = new ServerSocket(port);
				while (true) {
					this.socket = listener.accept(); // assuming single client
					if (this.networkReceiveHandler != null)
						this.networkReceiveHandler.stop_thread();
					
					this.networkReceiveHandler = new NetworkReceiveHanlder(socket.getInputStream());
					this.networkReceiveHandler.start();
//					out = new PrintWriter(socket.getOutputStream(), true);
					out = new DataOutputStream(socket.getOutputStream());
					Log.log("New connection with client at " + this.socket);
				}
			} catch (IOException err) {
				Log.log("Error: " + err.getMessage());
				socket = null;
				out = null;
			}
		}
	}

	public JSONObject getMessage() {
		if (socket == null)
			return null;
		if (networkReceiveHandler == null)
			return null;
		//log("NetworkHandler calling networkReceiveHandler.getMessage()");
		return networkReceiveHandler.getMessage();
	}

	public boolean send(JSONObject object) {
		if (socket == null) {
			Log.log("Socket is null, no client connection");
			return false;
		}

		if (!socket.isConnected())
			return false;

		try {
			String objstr = object.toString();
			//System.out.println(objstr.length());
			out.writeUTF(object.toString());
			out.flush();
			Log.log("data sent: " + object.toString());
			return true;
		} catch (Exception e) {
			Log.log("Error handling client" + e);
			return false;
		}
	}

	public void closeConnection() throws IOException {
		if (socket.isConnected())
			socket.close();
	}
	
	public void log(Object obj) {
		System.out.println(obj);
	}
}
