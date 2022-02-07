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

    public static void main(String[] args) {
        try {
            myftp clientSocket = new myftp();
            clientSocket.connection(args[0], args[1]);

        } catch (IOException e) {
            e.printStackTrace();
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
                String[] command = input.trim().split(" ");

                String filename = "";
                int read;
                File file;
                byte[] data;

                outputStream.writeUTF(command[0]);

                //implement commands
                if(command[0].equals("get")){
                    filename = command[1];
                    outputStream.writeUTF(filename);
                    long size = inputStream.readLong();

                    if(size > 0){
                        data = new byte[1024*100];
                        fileOutput = new FileOutputStream(filename);

                        while (size > 0 && (read = inputStream.read(data, 0, (int)Math.min(data.length, size))) != -1) {
                            fileOutput.write(data,0,read);
                            size -= read;
                        }

                        fileOutput.close();
                    }else{
                        System.out.println("Did not receive file from server");
                    }
                } else if(command[0].equals("put")){
                    filename = command[1];
                    outputStream.writeUTF(filename);

                    file = new File(filename);

                    if(file.isFile()){
                        outputStream.writeLong(file.length());
                        fileInput = new FileInputStream(file);
                        data = new byte[1024*100];

                        while ((read=fileInput.read(data))!=-1){
                            outputStream.write(data,0,read);
                            outputStream.flush();
                        }
                        fileInput.close();
                    } else{
                        System.out.println("File Not Found");
                        outputStream.writeLong(0); //No file
                    }
                } else if(command[0].equals("delete")){
                    outputStream.writeUTF(command[1]);
                    System.out.println(inputStream.readUTF());
                }  else if(command[0].equals("ls")){
                    String lsFiles = inputStream.readUTF();
                    System.out.println(lsFiles);

                }  else if(command[0].equals("cd")){
                    outputStream.writeUTF(command[1]);
                    String result = inputStream.readUTF();
                    System.out.println(result);

                }  else if(command[0].equals("mkdir")){
                    outputStream.writeUTF(command[1]);
                    System.out.println(inputStream.readUTF());

                }  else if(command[0].equals("pwd")){
                    String pwd = inputStream.readUTF();
                    System.out.println(pwd);
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
                e.printStackTrace();
            }

        }
    }
