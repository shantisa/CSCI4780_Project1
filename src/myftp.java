import java.io.*;
import java.net.*;
import java.util.*;

public class myftp {
    //initialize socket
    Socket socket = null;

    DataInputStream inputStream = null;
    DataOutputStream outputStream = null;

    public static void main(String[] args) {
        try {
            myftp clientSocket = new myftp();
            clientSocket.connection(args[0], args[1]);
        } catch (IOException e) {

        }
    }

    // connection is a function that establishes a connection with the server
    public void connection(String ip, String port) throws IOException {
        try {
            Scanner scanner = new Scanner(System.in);

            // establish connection with server
            socket = new Socket(ip, Integer.parseInt(port));

            // obtain input and out streams
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());

            while (true) {
                System.out.print("mytftp> ");
                String input = scanner.nextLine();
                String[] command = input.split(" ");

                if(command[0].equals("get")){

                } else if(command[0].equals("put")){

                } else if(command[0].equals("delete")){

                }  else if(command[0].equals("ls")){

                }  else if(command[0].equals("cd")){

                }  else if(command[0].equals("mkdir")){

                }  else if(command[0].equals("pwd")){

                }  else if(command[0].equals("quit")){
                    outputStream.writeUTF(input);
                    break;
                }

            }
                socket.close();
                inputStream.close();
                outputStream.close();
            } catch(Exception e){
                System.out.println("Can't establish connection to Server");
            }

        }
    }
