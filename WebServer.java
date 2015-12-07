import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public class WebServer{

	private static ServerSocket serverSocket;
	public static void main(String[] args) {
		int port;
		if(args.length < 1){
			System.out.println("Usage is: java WebServer <port number>");
			return;
		}else{
			try{
				port = Integer.parseInt(args[0]);
			}catch(NumberFormatException nfe){
				port = 80; //default port
			}
		}

		try{
			System.out.println("Binding port "+port+" to server...");
			serverSocket = new ServerSocket(port);
			System.out.println("Succesfully created server!");

			while(true){
				System.out.println("Server Ready! Waiting for connections...");
				Socket s = serverSocket.accept();
				new HTTPRequestHandler(s).start();
			}
		} catch (IOException ioe){
			System.out.println("Error: "+ioe.getMessage());
		}
	}
}

class HTTPRequestHandler extends Thread{
	private Socket socket;

	public HTTPRequestHandler(Socket s){
		socket = s;
	}

	@Override
	public void run(){
		//initialize streams
		
		BufferedReader input = null;
		PrintStream output = null;
		int method = 0;
		String filename = null;
		String file_name = "default";
		String[] parameters = new String[]{};
		
		try{
			// Open socket connections
			input = new BufferedReader(
				new InputStreamReader(socket.getInputStream()));
			output = new PrintStream(
			new BufferedOutputStream(socket.getOutputStream()));

			// Read REQUEST_URI
			String s = input.readLine();

			// Check REQUEST_METHOD
			if(s.startsWith("GET")){
				method = 1;
			}else if(s.startsWith("POST")){
				method = 2;
			}

			String params = "";
			// Process REQUEST
			switch(method){
				case 0: // Invalid REQUEST method
					output.print(constructHTTPHeader(501)+"\r\n");
					output.close();
					return;
				case 1: // GET
					//parse filename and request parameters
					filename = s.split("\\s")[1];
					if(filename.contains("?")){
						params = filename.substring(filename.indexOf('?')+1);
						filename = filename.substring(0,filename.indexOf('?'));
					}
					break;
				case 2: //POST
					filename = s.split("\\s")[1]; //filename
					//read other headers
					while(input.ready() && input.readLine() != "\r\n");
					params = input.ready()?input.readLine():"";
					break;
			}

			//Storage for REQUEST_PARAMETERS
			parameters = params.equals("")?new String[]{}:params.split("&");

			//Process filename
			//Append trailing / with index.html
			if(filename.endsWith("/")){
				filename += "index.html";
			}

			// Remove leading / from filename
	        while (filename.indexOf("/")==0)
	          filename = filename.substring(1);

			// Check for illegal characters to prevent access to superdirectories
        	if (filename.indexOf("..")>=0 || filename.indexOf(':')>=0 || filename.indexOf('|')>=0){
        		throw new FileNotFoundException();
        	}

        	// If a directory is requested and the trailing / is missing,
	        // send the client an HTTP request to append it.  (This is
	        // necessary for relative links to work correctly in the client).
	        if (new File(filename).isDirectory()) {
	          filename=filename.replace('\\', '/');
	          output.print(constructHTTPHeader(301)+
	            "Location: /"+filename+"/\r\n\r\n");
	          output.close();
	          return;
	        }

	        // Open the file (may throw FileNotFoundException)
	        System.out.println("Requested file is "+filename);
	        System.out.println("Parameters: "+ Arrays.toString(parameters));
        	InputStream f=new FileInputStream(filename);
        	f.close();

        	file_name = new File(filename).getName(); 
        	// copy this file to server directory
        	Files.copy(new File(filename).toPath(),
        		new File(file_name).toPath(),
        		StandardCopyOption.REPLACE_EXISTING);
        	
        	//send response headers
        	output.print(constructHTTPHeader(200));
        	output.print("Content-type: text/html\r\n");
        	output.print("\r\n");

        	//send response body
        	output.print("<!DOCTYPE html>"+"\r\n");
			output.print("<html>"+"\r\n");
			output.print("<head><title>Mini HTTP Web Server</title></head>"+"\r\n");
			output.print("<body>"+"\r\n");
			output.print("<p>"+filename+" was found and saved to server directory!</p>");

		}catch(FileNotFoundException fnfe){
			System.out.println("Requested file not found!");
			output.print(constructHTTPHeader(404)+
				"Content-type: text/html\r\n\r\n");
			//send response body
        	output.print("<!DOCTYPE html>"+"\r\n");
			output.print("<html>"+"\r\n");
			output.print("<head><title>Mini HTTP Web Server</title></head>"+"\r\n");
			output.print("<body>"+"\r\n");
			output.print("<p>"+filename+" was not found!</p>");
          	try{
          	PrintWriter out = new PrintWriter(filename);
          	out.println("AUTO GENERATED FILE");
          	out.close();
          	output.print("<p>"+filename+" generated on server directory!</p>");
          	}catch(FileNotFoundException fnfe2){
          		
          	}
		}catch(IOException ioe){
			System.out.println("Error: "+ioe.getMessage());
		}finally{
			//display the request parameters
			output.print("<p> "+ ((method==1)?"GET":"POST") +" Request Parameters </p>"+"\r\n");
			output.print("<table border ='1'>"+"\r\n");
			output.print("<tr>"+"\r\n");
			output.print("<th>Key</th>"+"\r\n");
			output.print("<th>Value</th>"+"\r\n");
			output.print("</tr>"+"\r\n");
			
			//display request parameters
			if(parameters.length != 0){
				for(int i=0; i<parameters.length; i++){
					String[] tokens = parameters[i].split("=");
					output.print("<tr>"+"\r\n");
					output.print("<td>"+tokens[0]+"</td>"+"\r\n");
					output.print("<td>"+tokens[1]+"</td>"+"\r\n");
					output.print("</tr>"+"\r\n");
				}
			}else{
				output.print("<tr>"+"\r\n");
				output.print("<td colspan='2'>EMPTY</td>"+"\r\n");
				output.print("</tr>"+"\r\n");
			}
			
			output.print("</table>"+"\r\n");
			
			output.print("</body>"+"\r\n");
			output.print("</html>"+"\r\n");

			output.close();
		}
	}
	static final String HTTP_VERSION = "HTTP/1.1";
	public String constructHTTPHeader(int statusCode){
		String header = HTTP_VERSION + " ";
		switch(statusCode){
			case 200: header += "200 OK"; break;
			case 301: header += "301 Moved Permanently"; break;
			case 403: header += "403 Forbidden"; break;
			case 404: header += "404 Not Found"; break;
			case 501: header += "501 Not Implemented"; break;
		}

		header += "\r\n";
		header += "Server: PoserioMiniWebServerV1.1\r\n";
		header += "Connection: close\r\n";
		return header;
	}
}