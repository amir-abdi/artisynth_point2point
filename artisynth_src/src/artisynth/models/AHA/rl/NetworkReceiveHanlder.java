package artisynth.models.AHA.rl;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

import com.sun.xml.internal.ws.api.pipe.ThrowableContainerPropertySet;

public class NetworkReceiveHanlder extends Thread
{
	InputStream in;
	Queue<JSONObject> jsonObjects;
	public ReentrantLock lock = new ReentrantLock();
	public NetworkReceiveHanlder(InputStream socket)
	{
		this.in = socket;
		jsonObjects = new LinkedList<JSONObject>(); 
	}

	public void run()
	{		
		while(true)
		{
			try {
				JSONObject jo = receiveJsonObject();

				if (jo != null)
				{
					log("Obj received: " + jo.toString());
					try {	
						lock.lock();
						jsonObjects.add(jo);
						log("Queue size after add: " + jsonObjects.size());	
					}
					catch (Exception e)
					{
						log("Error in NetowkrReceive run: " + e.getMessage());
					} finally {
						//lock.notify();
						lock.unlock();					
					}
				}

			}catch(SocketException e)
			{
				log("SocketException in receiveJsonObject: " + e.getMessage());
				in = null;
				log("Closing the receive thread");
				break;
			} catch(IOException e)
			{
				log("IOException in receiveJsonObject: " + e.getMessage());
			} catch(JSONException e)
			{
				log("JSONException in receiveJsonObject: " + e.getMessage());
			}
		}
	}

	protected JSONObject getMessage()
	{

		JSONObject jo = null;

		if (jsonObjects.size() > 0)
		{
			try
			{
				lock.lock();
				jo = jsonObjects.remove();
				log("Queue size after remove: " + jsonObjects.size());				
			} catch(Exception e) {
				log("Exception in getMessage: " + e.getMessage());
			} finally {
				//lock.notify();
				lock.unlock();			
			}
		}
		return jo;		
	}


	private JSONObject receiveJsonObject() throws JSONException, IOException, SocketException
	{
		byte[] b = new byte[1024];
		if (in == null)		
			throw new SocketException("Socket is closed");
		
		int numbytes = in.read (b);
		if (numbytes <= 0)
			return null;
		String s = new String (b);
		JSONObject jo = null;
		try {
			jo = new JSONObject (s);
		} catch(JSONException e) {		
			log("Error in receiveJsonObject: " + e.getMessage());
			throw new JSONException(s);
		}
		return jo;		
	}

	private void log(String message) {
		System.out.println(message);
	}


}
