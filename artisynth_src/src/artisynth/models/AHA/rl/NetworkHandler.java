package artisynth.models.AHA.rl;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import com.sun.org.apache.xpath.internal.operations.Bool;
import artisynth.models.AHA.rl.*;

public class NetworkHandler extends Thread
{
   private Socket socket = null;
   NetworkReceiveHanlder networkReceiveHandler;
   //   private int clientNumber;
   PrintWriter out;
   
   
   public NetworkHandler() {
   }

   public void run() 
   {
      while(true)
      {
         try 
         {
            ServerSocket listener = new ServerSocket(6611);
            while(true)
            {         
               this.socket = listener.accept(); // assuming single client
               this.networkReceiveHandler = 
            		   new NetworkReceiveHanlder(socket.getInputStream());
               this.networkReceiveHandler.start();
               out = new PrintWriter(socket.getOutputStream(), true);
               Log.log("New connection with client at " + this.socket);
            }
         }
         catch (IOException err) 
         {      
            Log.log("Error: " + err.getMessage ()) ;
            socket = null;
            out = null;
         }
      }
   }


	public JSONObject getMessage()
	{		
		if (socket == null)
			return null;
		if (networkReceiveHandler == null)
			return null;
		return networkReceiveHandler.getMessage();		
	}
	
   
   public boolean send(JSONObject object)
   {
      if (socket == null)
      {
         Log.log("Socket is null, no client connection");
         return false;         
      }
      
      if (!socket.isConnected ())
         return false;
      
      try {                  
         out.println(object.toString ());
         out.flush();
         Log.log("data sent: " + object.toString ());         
         return true;
      } catch (Exception e) {
         Log.log("Error handling client" + e);
         return false;
      } 
   }

   public void closeConnection() throws IOException 
   {
	   if (socket.isConnected())
		   socket.close();
   }
}
