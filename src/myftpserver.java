import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class myftpserver {
    //initialize socket
    Socket socket = null;
    ServerSocket server = null;

    public static void main(String[] args) {
        try {
            myftpserver serverSocket = new myftpserver();
            serverSocket.connection(args[0]);
        } catch (IOException e) {

        }
    }

    // connection is a function that establishes a connection with the client
    public void connection(String port) throws IOException {
        try {
            //server is listening
            server = new ServerSocket(Integer.parseInt(port));

            System.out.print("Server is Running... ");

            //run infinite loop to get the client requests
            while (true) {
                //receive incoming client request and establish connection
                socket = server.accept();

                //create a new thread
                ClientThread thread = new ClientThread(socket);

                thread.start();
            }

        } catch (Exception e) {
            server.close();
            e.printStackTrace();

        }
    }
}

class ClientThread extends Thread {
    Socket socket = null;
    DataInputStream inputStream = null;
    DataOutputStream outputStream = null;
    FileInputStream fileInput = null;
    FileOutputStream fileOutput = null;

    Path path = Paths.get("").toAbsolutePath();

    //Constructor
    public ClientThread(Socket socket) {
        try {
            this.socket = socket;
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (Exception e) {

        }

    }

    public void run() {
        try {
            while (true) {
                String command = inputStream.readUTF();
                String filename = "", fileData = "";
                File file;

                byte[] data;
                int read;
                //testing purposes - server prints out the client command that was sent
                System.out.println(command);

                //implement commands
                if (command.equals("get")) {
                    filename = inputStream.readUTF();
                    file = path.resolve(filename).toFile();

                    if(file.isFile()){
                        outputStream.writeLong(file.length());
                        fileInput = new FileInputStream(file);
                        data = new byte[1024*1000];

                        while ((read=fileInput.read(data))!=-1){
                            outputStream.write(data,0,read);
                            outputStream.flush();
                        }
                        fileInput.close();
                    }else{
                        outputStream.writeLong(0); //No file
                    }

                } else if (command.equals("put")) {

                    filename = inputStream.readUTF();
                    long size = inputStream.readLong();

                    if(size > 0) {
                        data = new byte[1024 * 1000];
                        fileOutput = new FileOutputStream(path.resolve(filename).toFile());

                        while (size > 0 && (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1) {
                            fileOutput.write(data, 0, read);
                            size -= read;
                        }

                        fileOutput.close();
                    }
                } else if (command.equals("delete")) {
                	
                	file = path.toFile();
                	
                	if (file.delete()) { 
                	      System.out.println("Deleted the file: " + file.getName());
                	    } else {
                	      System.out.println("Failed to delete the file.");
                	} 

                } else if (command.equals("ls")) {
                    file = path.toFile();
                    String[] list = file.list();
                    outputStream.writeUTF(String.join(" ",list));

                } else if (command.equals("cd")) {
                    String cd = inputStream.readUTF();

                    file = path.resolve(cd).toFile();
                    if(file.isDirectory()){
                        path = path.resolve(cd);
                        outputStream.writeUTF("Directory changed");
                    }else{
                        outputStream.writeUTF(cd + " is not a valid directory");
                    }
                } else if (command.equals("mkdir")) {
                	File  f = new File(command[1]);
                	
                	if (f.mkdir()) {
                		System.out.println("Directory was created.")
                	}
                	else {
                		System.out.println("Directory not successfully created.");
                	}
                	
                } else if (command.equals("pwd")) {
                	System.out.println(path.toFile());

                } else if (command.equals("quit")) {
                    break;
                } else {
                    System.out.println("Error");
                }

            }
            inputStream.close();
            outputStream.close();

        } catch (Exception e) {

        }
    }
}

