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

public class NetworkServer extends Thread
{
   private Socket socket = null;
   NetworkReceiverHanlder networkReceiveHandler;
   //   private int clientNumber;

   
   
   public NetworkServer() {
   }

   private void log(String message) {
      System.out.println(message);
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
               this.networkReceiveHandler = new NetworkReceiverHanlder(socket);
               this.networkReceiveHandler.start();
               log("New connection with client at " + this.socket);
            }
         }
         catch (IOException err) 
         {      
            log("Error: " + err.getMessage ()) ;
            socket = null;
         }
      }
   }


   public JSONObject receive() throws JSONException, IOException
   {
      //BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));      
      //char[] cbuf = new char[1024];
      //in.read (cbuf);
      //JSONObject jo = new JSONObject (cbuf);
      
      byte[] b = new byte[1024];
      int numbytes = socket.getInputStream().read (b);
      String s = new String (b);
      JSONObject jo = new JSONObject (s);
            
      return jo;
   }
   
   public boolean send(JSONObject object)
   {
      if (socket == null)
      {
         log("Socket is null, no client connection");
         return false;         
      }
      
      if (!socket.isConnected ())
         return false;
      
      try {         
         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
         out.println(object.toString ());
         log("data sent: " + object.toString ());
         return true;
      } catch (IOException e) {
         log("Error handling client" + e);
         return false;
      } 
   }
}
