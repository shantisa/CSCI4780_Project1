import java.io.*;
import java.net.*;
import java.util.*;

public class myftp {
    //initialize socket
    Socket socket = null;

    DataInputStream inputStream = null;
    DataOutputStream outputStream = null;
    FileInputStream fileInput = null;
    FileOutputStream fileOutput = null;
    BufferedReader buffered = null;

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
            InputStreamReader scanner = new InputStreamReader(System.in);
            buffered = new BufferedReader(scanner);

            // establish connection with server
            socket = new Socket(ip, Integer.parseInt(port));

            // obtain input and out streams
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());

            while (true) {
                System.out.print("mytftp> ");
                String input = buffered.readLine();
                String[] command = input.trim().split(" ");

                String filename = "", fileData = "";
                File file;
                byte[] data;

                outputStream.writeUTF(command[0]);

                //implement commands
                if(command[0].equals("get")){
                    filename = command[1];
                    outputStream.writeUTF(filename);

                    fileData = inputStream.readUTF();
                    if(fileData.equals("")){
                        System.out.println("Did not receive file from server");
                    } else{
                        fileOutput = new FileOutputStream(filename);
                        fileOutput.write(fileData.getBytes());
                        System.out.println(fileData);
                        fileOutput.close();
                    }

                } else if(command[0].equals("put")){
                    filename = command[1];

                    file = new File(filename);

                    if(file.isFile()){
                        fileInput = new FileInputStream(file);
                        data = new byte[fileInput.available()];
                        fileInput.read(data);
                        fileInput.close();

                        outputStream.writeUTF(filename);
                        outputStream.writeUTF(new String(data));
                    }else{
                        System.out.println("File Not Found");
                    }

                } else if(command[0].equals("delete")){

                }  else if(command[0].equals("ls")){
                    String lsFiles = inputStream.readUTF();
                    System.out.println(lsFiles);

                }  else if(command[0].equals("cd")){
                    outputStream.writeUTF(command[1]);
                    String result = inputStream.readUTF();
                    System.out.println(result);

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
