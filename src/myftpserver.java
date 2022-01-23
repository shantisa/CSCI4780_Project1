import java.io.*;
import java.net.*;
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

                //testing purposes - server prints out the client command that was sent
                System.out.println(command);

                String filename = "";

                //implement commands
                if (command.equals("get")) {

                } else if (command.equals("put")) {

                } else if (command.equals("delete")) {

                } else if (command.equals("ls")) {

                } else if (command.equals("cd")) {

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

