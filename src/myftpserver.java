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
                //testing purposes - server prints out the client command that was sent
                System.out.println(command);

                //implement commands
                if (command.equals("get")) {
                    filename = inputStream.readUTF();
                    file = path.resolve(filename).toFile();

                    if(file.isFile()){
                        fileInput = new FileInputStream(file);
                        data = new byte[fileInput.available()];
                        fileInput.read(data);
                        fileData = new String(data);
                        fileInput.close();
                        outputStream.writeUTF(fileData);
                    }else{
                        outputStream.writeUTF(""); //No file
                    }

                } else if (command.equals("put")) {
                    filename = inputStream.readUTF();
                    fileData = inputStream.readUTF();
                    fileOutput = new FileOutputStream(path.resolve(filename).toFile());
                    fileOutput.write(fileData.getBytes());
                    fileOutput.close();

                } else if (command.equals("delete")) {

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

                } else if (command.equals("pwd")) {

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

