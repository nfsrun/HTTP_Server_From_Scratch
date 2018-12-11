/*
Kevin Tran
Assignment 5 - HTTP Web Server
December 1, 2018
William Mortl
 */

//References: 
//Get Java's current directory: 
//https://stackoverflow.com/questions/4871051/getting-the-current-working-directory-in-java
//Response time: 
//https://stackoverflow.com/questions/5236052/get-gmt-time-in-java
//String: Contents of a file
//https://stackoverflow.com/questions/326390/how-do-i-create-a-java-string-from-the-contents-of-a-file
//MIME Types
//https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

class WebServer 
{
    // this is the port the web server listens on
    private static final int PORT_NUMBER = 8080;

    // main entry point for the application
    public static void main(String args[]) 
    {
    	
        try 
        {
            // open socket
            ServerSocket serverSocket = new ServerSocket(PORT_NUMBER);

            // start listener thread
            Thread listener = new Thread(new SocketListener(serverSocket));
            listener.start();

            // message explaining how to connect
            System.out.println("To connect to this server via a web browser, try \"http://127.0.0.1:8080/{url to retrieve}\"");

            // wait until finished
            System.out.println("Press enter to shutdown the web server...");
            Console cons = System.console(); 
            String enterString = cons.readLine();

            // kill listener thread
            listener.interrupt();

            // close the socket
            serverSocket.close();
        } 
        catch (Exception e) 
        {
            System.err.println("WebServer::main - " + e.toString());
        }
    }
}

class SocketListener implements Runnable 
{
    private ServerSocket serverSocket;

    public SocketListener(ServerSocket serverSocket)   
    {
        this.serverSocket = serverSocket;
    }

    // this thread listens for connections, launches a separate socket connection
    //  thread to interact with them
    public void run() 
    {
        while(!this.serverSocket.isClosed())
        {
            try
            {
                Socket clientSocket = serverSocket.accept();
                Thread connection = new Thread(new SocketConnection(clientSocket));
                connection.start();
                Thread.yield();
            }
            catch(IOException e)
            {
                if (!this.serverSocket.isClosed())
                {
                    System.err.println("SocketListener::run - " + e.toString());
                }
            }
        }
    }
}

class SocketConnection implements Runnable 
{
    private final String HTTP_LINE_BREAK = "\r\n";

    private Socket clientSocket;

    public SocketConnection(Socket clientSocket)   
    {
        this.clientSocket = clientSocket;
    }

    // one of these threads is spawned and used to talk to each connection
    public void run() 
    {       
        try
        {
            BufferedReader request = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            PrintWriter response = new PrintWriter(this.clientSocket.getOutputStream(), true);
            this.handleConnection(request, response);
        }
        catch(IOException e)
        {
            System.err.println("SocketConnection::run - " + e.toString());
        }
    }

    /**
     * Method will be ran per thread of a request and will process each request accordingly. . 
     * @param request: BufferedReader to read in a HTTP request. 
     * @param response: PrintWriter to output back to the requester. 
     */
    private void handleConnection(BufferedReader request, PrintWriter response)
    {
        try
        {
            
            /*
             * Tasks:
             * ------
             * 1.) Figure out how to parse the HTTP request header
             * 2.) Figure out how to open files
             * 3.) Figure out how to create an HTTP response header
             * 4.) Figure out how to return resources (i.e. files)
             * 
             */
            // EXAMPLE: code prints the web request
            
        	
            String[] headerLines = this.readHTTPHeader(request).split("\n");
            String[] firstLine = headerLines[0].split(" ");
        	
            //removes any return characters at the end of a string
            String version = firstLine[2].replace("\r", "").replace("\n", "");
            if(version.equals("HTTP/1.1") || version.equals("HTTP/1.0")){
            	
            	//Convert all delimiters to back slashes
            	firstLine[1] = firstLine[1].replace('/', '\\');
            	Map<String, String> header_fields = new HashMap<String, String>();
                
            	//Map only lines that are actually header fields
            	for(String line : headerLines)
                	if(line.contains(": ")) {
                		String[] input = line.split(": ");
                		header_fields.put(input[0], input[1].replace("\r", ""));
                	}else if(line.equals("\r\n"))
                		break;
            	
                if(firstLine[0].equals("POST")){
                	//cannot post a directory page (non-item)..., output error. 
                    if(isDirOnly(firstLine[1]))
            			response.println(notAllowed405(version, header_fields.get("Connection")));
                    //throw in file information only if file exists
                    else if(fileExists(firstLine[1]))
                    	response.println(printLastModifiedHeader(firstLine[1], version, header_fields.get("Connection")));
                    //else that item doesn't exist, output error. 
                    else
                    	response.print(badRequest404(version, header_fields.get("Connection")));
                }else if(firstLine[0].equals("GET")){
                	//if file exists then output the file
                    if(fileExists(firstLine[1]))
                    	response.println(getItem(firstLine[1], version, header_fields.get("Connection")));
                   //else if its a directory 
                    else if(isDirOnly(firstLine[1]))
                    	//if index.html exists for it, output index.html
                    	if(fileExists(firstLine[1] + "\\index.html"))
                    		response.println(getItem(firstLine[1] + "\\index.html", version, header_fields.get("Connection")));
                    	//if only directory exists, output directory
                    	else if(directoryExists(firstLine[1]))
                    		response.println(printDirectory(firstLine[1], version, header_fields.get("Connection")));
                    	//else that item doesn't exist, output error. 
                    	else
                    		response.println(badRequest404(version, header_fields.get("Connection")));
                    //else whatever that is doesn't exist, output error. 
                    else
                    	response.println(badRequest404(version, header_fields.get("Connection")));
                //else that operation isn't supported, output error. 
                }else{
                	response.println(badRequest505(header_fields.get("Connection")));
                } 
            }
            
            // close the socket, no keep alives
            this.clientSocket.close();
        }
        catch(IOException e)
        {
            System.err.println("SocketConnection::handleConnection: " + e.toString());
        }
    }

    /**
     * Prints the directory of a file_path in an HTML and outputs that in a string. 
     * @param path: String that shows request for a relative path 
     * @param httpType: String representation of requestor's HTTP type. 
     * @param connectionType: String of whether the connection is keep-alive 
     * @return Returns the string of the whole HTML of the current file directory. 
     */
    private String printDirectory(String path, String httpType, String connectionType) {
    	path = System.getProperty("user.dir") + "\\webroot" + path;
		try {
			
			StringBuilder page = new StringBuilder();
			page.append("<html>\n");
			page.append("<head>\n");
			page.append("\t<title>"+ path +"</title>\n");
			page.append("\t<link rel=\"stylesheet\" href=\"style.css\">");
			page.append("</head>\n");
			page.append("<body>\n");
			page.append("\t<h1>Directory</h1>\n");
			page.append("\t<ul>\n");
			if(!path.endsWith("\\")) {
				String up1 = path.split("\\webroot")[1];
				up1 = up1.substring(0, up1.lastIndexOf("\\"));
				if(up1.equals(""))
					page.append("<li><a href=\"\\" + up1 + "\">" + "\\</a></li>");
				else
					page.append("<li><a href=\"\\" + up1 + "\">" + up1 +"</a></li>");
			}
			
			File[] selected = new File(path).listFiles();
			for(File current : selected)
			{
				String[] output = current.getPath().split("\\\\webroot\\\\");
				page.append("<li><a href=\""+ output[1].replace("/", "\\") +"\">" + output[1].replace("/", "\\") + "</a></li>\n");
			}
			page.append("</body>\n");
			page.append("</html>");
			
			StringBuilder respond = new StringBuilder();
			respond.append(httpType + " 200 OK Valid\r\n");
	        SimpleDateFormat sdf =
	        new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
	        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
	        String s = sdf.format(new Date());
	        respond.append("Date: " + s + "\r\n");
	        respond.append("Server: WebServerCustom/0.9.0 (Win32/64)\r\n");
	        int size = page.toString().getBytes().length;
	        respond.append("Content-Length: " + size + "\r\n");
	        respond.append("Connection: " + connectionType + "\r\n");
	        respond.append("Content-Type: text/html; charset=iso-8859-1\r\n");
	        respond.append("\r\n");
	        respond.append(page.toString());
	        return respond.toString();
		}catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
		return "";
	}

    /**
     * Returns the item in a HTTP response format as a string. 
     * @param file_path: String that shows request for a relative path (and file)
     * @param httpType: String representation of requestor's HTTP type. 
     * @param connectionType: String of whether the connection is keep-alive 
     * @return Output a get message with file message. 
     */
    private String getItem(String file_path, String httpType, String connectionType) {
    	file_path = System.getProperty("user.dir") + "\\webroot" + file_path;
		try {
			String[] split = file_path.split("\\\\");
			String contentType = contentType(split[split.length - 1].split("\\.")[1]);
			StringBuilder respond = new StringBuilder();
			StringBuilder outputFile = new StringBuilder();
			
			
				BufferedReader file = new BufferedReader(new FileReader(new File(file_path)));
		        String line;
				while((line = file.readLine()) != null)
					if(contentType.startsWith("image"))
						outputFile.append(line.getBytes());
					else
						outputFile.append(line);
		        file.close();
			
	        File selected = new File(file_path);
			respond.append(httpType + " 200 OK Valid\r\n");
	        SimpleDateFormat sdf =
	        new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
	        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
	        
			String s = sdf.format(new Date());
	        respond.append("Date: " + s + "\r\n");
	        respond.append("Server: WebServerCustom/0.9.0 (Win32/64)\r\n");
	        respond.append("Last-Modified: " + sdf.format(selected.lastModified()));
	        int size = file.toString().getBytes().length;
	        respond.append("Content-Length: " + size + "\r\n");
	        respond.append("Connection: " + connectionType + "\r\n");
	        respond.append("Content-Type: " + contentType + "; charset=iso-8859-1\r\n");
	        respond.append("\r\n");
	        respond.append(outputFile.toString());
	        return respond.toString();
		}catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
		return "";
	}

    /**
     * Method returns MIME type of a given file extension
     * @param extension: String of file extension. 
     * @return text MIME representation of file extension
     */
	private static String contentType(String extension) {
		switch(extension.toLowerCase()) {
			case "html":
				return "text/html";
			case "css":
				return "text/css";
			case "txt":
				return "text/plain";
			case "js":
				return "application/javascript";
			case "ico":
				return "image/ico";
			case "jpeg":
				return "image/jpeg";
			case "jpg":
				return "image/jpeg";
			case "json":
				return "application/json";
			case "pdf":
				return "application/pdf";
		}
		return "text/plain";
	}
	
	/**
	 * Checks if the extension string given has an applicable MIME type and 
	 * outputs that type in a string format. 
	 * @param file_path: String that shows request for a relative path (and file)
	 * @param httpType: String representation of requestor's HTTP type. 
	 * @param connectionType: String of whether the connection is keep-alive 
	 * @return returns the POST response of a file. 
	 */
	private String printLastModifiedHeader(String file_path, 
    		String httpType, String connectionType) {
    	String filePath = System.getProperty("user.dir") + "\\webroot" + file_path;
		try {
			File input = new File(filePath);
			StringBuilder respond = new StringBuilder();
			respond.append(httpType + " 200 OK Valid\r\n");
	        SimpleDateFormat sdf =
	        new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
	        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
	        String s = sdf.format(new Date());
	        respond.append("Date: " + s + "\r\n");
	        respond.append("Server: WebServerCustom/0.9.0 (Win32/64)\r\n");
	        respond.append("Last-Modified: " + sdf.format(input.lastModified()));
	        respond.append(connectionType + "\r\n");
	        respond.append("Content-Type: text/html; charset=iso-8859-1\r\n");
	        respond.append("\r\n");
	        return respond.toString();
		}catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
		return "";
	}

	/**
	 * Checks if the file exists. 
	 * @param file_path: String that shows request for a relative path (and file)
	 * @return boolean: if the file exists in a requested file_path
	 */
	private boolean fileExists(String file_path) {
		try{
			File test = new File(System.getProperty("user.dir") + "\\webroot" 
		+ file_path);
			return test.exists() && test.isFile();
		}catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Checks if the path exists. 
	 * @param file_path: String that shows request for a relative path (and file)
	 * @return boolean: if the directory exists in a requested file_path
	 */
	private boolean directoryExists(String path) {
		try{
			File test = new File(System.getProperty("user.dir") + "\\webroot" 
		+ path);
			return test.exists() && test.isDirectory();
		}catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Checks if the path is actually only a directory and is not requesting a 
	 * file. 
	 * @param path: String that shows request for a relative path (and file)
	 * @return boolean: if the directory exists and is only that in a requested
	 * file_path. 
	 */
	private boolean isDirOnly(String path) {
		String[] split = path.split("/");
		return !split[split.length - 1].contains(".");
	}
	
	/**
	 * Outputs 404 error HTML
	 * @param httpType: String representation of requestor's HTML
	 * @param connectionType: String of whether the connection is keep-alive 
	 * @return String of error page's HTML
	 */
	private String badRequest404(String httpType, String 
			connectionType){
		StringBuilder respond = new StringBuilder();
        respond.append(httpType + " 404 Not Found\r\n");
        SimpleDateFormat sdf =
        new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String s = sdf.format(new Date());
        respond.append("Date: " + s + "\r\n");
        respond.append("Server: WebServerCustom/0.9.0 (Win32/64)\r\n");
        respond.append("Content-Length: " + 181 + "\r\n");
        respond.append(connectionType + "\r\n");
        respond.append("Content-Type: text/html; charset=iso-8859-1\r\n");
        respond.append("\r\n");
        respond.append("<html>\n");
        respond.append("<head>\n");
        respond.append("\t<title>404 File Not Found</title>\n");
        respond.append("</head>\n");
        respond.append("<body>\n");
        respond.append("\t<h1>404 File Not Found</h1>\n");
        respond.append("\t<p>The file requested was not found on the server. </p>\n");
        respond.append("</body>\n");
        respond.append("</html>");
        return respond.toString();
    }
	
	/**
	 * Outputs 505 error HTML
	 * @param connectionType: String of whether the connection is keep-alive 
	 * or not. 
	 * @return String of error page's HTML
	 */
	private String badRequest505(String connectionType){
		StringBuilder respond = new StringBuilder();
		respond.append("HTML/1.1 505 HTML Version Not Supported\r\n");
        SimpleDateFormat sdf =
        new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String s = sdf.format(new Date());
        respond.append("Date: " + s + "\r\n");
        respond.append("Server: WebServerCustom/0.9.0 (Win32/64)\r\n");
        respond.append("Content-Length: " + 208 + "\r\n");
        respond.append(connectionType + "\r\n");
        respond.append("Content-Type: text/html; charset=iso-8859-1\r\n");
        respond.append("\r\n");
        respond.append("<html>\n");
        respond.append("<head>\n");
        respond.append("\t<title>505 HTTP Version Not Supported</title>\n");
        respond.append("</head>\n");
        respond.append("<body>\n");
        respond.append("\t<h1>505 HTTP Version Not Supported</h1>\n");
        respond.append("\t<p>This server does not supported on this server. </p>\n");
        respond.append("</body>\n");
        respond.append("</html>");
        return respond.toString();
    }
	
	/**
	 * Outputs 405 error HTML
	 * @param httpType: String representation of requestor's HTML
	 * @param connectionType: String of whether the connection is keep-alive 
	 * @return String of error page's HTML
	 */
	private String notAllowed405(String httpType, 
String connectionType){
		StringBuilder respond = new StringBuilder();
        respond.append(httpType + " 405 Not Allowed \r\n");
        SimpleDateFormat sdf =
        new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String s = sdf.format(new Date());
        respond.append("Date: " + s + "\r\n");
        respond.append("Server: WebServerCustom/0.9.0 (Win32/64)\r\n");
        respond.append("Content-Length: " + 190 + "\r\n");
        respond.append(connectionType + "\r\n");
        respond.append("Content-Type: text/html; charset=iso-8859-1\r\n");
        respond.append("\r\n");
        respond.append("<html>\n");
        respond.append("<head>\n");
        respond.append("\t<title>405 Action Not Allowed</title>\n");
        respond.append("</head>\n");
        respond.append("<body>\n");
        respond.append("\t<h1>405 Action Not Allowed</h1>\n");
        respond.append("\t<p>The action requested is not allowed on directory. </p>\n");
        respond.append("</body>\n");
        respond.append("</html>");
        return respond.toString();
    }
	
    private String readHTTPHeader(BufferedReader reader)
    {
        String message = "";
        String line = "";
        while ((line != null) && (!line.equals(this.HTTP_LINE_BREAK)))
        {   
            line = this.readHTTPHeaderLine(reader);
            message += line;
        }
        return message;
    }
	
    private String readHTTPHeaderLine(BufferedReader reader)
    {
        String line = "";
        try 
        {
            line = reader.readLine() + this.HTTP_LINE_BREAK;
        }
        catch (IOException e) 
        {
            System.err.println("SocketConnection::readHTTPHeaderLine: " + e.toString());
            line = "";
        } 
        return line;
    }
}
