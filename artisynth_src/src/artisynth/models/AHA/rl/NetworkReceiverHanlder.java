package artisynth.models.AHA.rl;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

public class NetworkReceiverHanlder extends Thread
{
	InputStream in;
	Queue<JSONObject> jsonObjects;
	public ReentrantLock lock = new ReentrantLock();
	public NetworkReceiverHanlder(InputStream socket)
	{
		this.in = socket;
		Queue<JSONObject> jsonObjects = new LinkedList<JSONObject>(); 
	}

	public void run()
	{		
		lock.lock();
		try {		
			JSONObject jo = receiveJsonObject();
			jsonObjects.add(jo);
		} catch(IOException e)
		{
			log("IOException in receiveJsonObject: " + e.getMessage());
		} catch(JSONException e)
		{
			log("JSONException in receiveJsonObject: " + e.getMessage());
		} finally {
			lock.unlock();
		}
		
	}


	public JSONObject receiveJsonObject() throws JSONException, IOException
	{
		byte[] b = new byte[1024];
		int numbytes = in.read (b);
		String s = new String (b);
		JSONObject jo = new JSONObject (s);

		return jo;
	}

	private void log(String message) {
		System.out.println(message);
	}


}
