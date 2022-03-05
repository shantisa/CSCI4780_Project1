import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class myftpserver {
    //initialize socket n-port
    Socket socket = null;
    //initialize socket t-port
    Socket tsocket = null;

    ServerSocket server = null;
    ServerSocket tserver = null;
    final List<String> filesInUse = new ArrayList<>();

    public static void main(String[] args) {
        try {
            myftpserver serverSocket = new myftpserver();
            serverSocket.connection(args[0], args[1]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // connection is a function that establishes a connection with the client
    public void connection(String nport, String tport) throws IOException {
        try {
            //server is listening on n-port
            server = new ServerSocket(Integer.parseInt(nport));

            //server is listening on t-port
            tserver = new ServerSocket(Integer.parseInt(tport));

            System.out.print("Server is Running... ");

            //run infinite loop to get the client requests
            while (true) {
                //receive incoming client request and establish connection for n-port
                socket = server.accept();

                //receive incoming client request and establish connection for t-port
                tsocket = tserver.accept();

                //create a new thread
                ClientThread thread = new ClientThread(socket, tsocket, filesInUse);

                thread.start();
            }

        } catch (Exception e) {
            server.close();
            tserver.close();
            e.printStackTrace();

        }
    }
}

class ClientThread extends Thread {
    Socket socket = null;
    Socket tsocket = null;
    DataInputStream inputStream = null;
    DataInputStream tinput = null;
    DataOutputStream outputStream = null;
    DataOutputStream toutput = null;
    FileInputStream fileInput = null;
    FileOutputStream fileOutput = null;
    final List<Boolean> commandStatus = new ArrayList<>();
    List<String> filesInUse;

    Path path = Paths.get("").toAbsolutePath();

    //Constructor
    public ClientThread(Socket socket, Socket tsocket,List<String> filesInUse) {
        try {
            this.socket = socket;
            this.tsocket = tsocket;
            this.filesInUse = filesInUse;
            inputStream = new DataInputStream(socket.getInputStream());
            tinput = new DataInputStream(tsocket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
            toutput = new DataOutputStream(tsocket.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void runTerminator(){
        new Thread(()->{
            try {
                while (true) {
                    String command = tinput.readUTF();
                    if(command.equals("terminate")){
                        int commandID = tinput.readInt();
                        if(commandStatus.size() > commandID){
                            boolean done;
                            synchronized(commandStatus){
                                done = !commandStatus.get(commandID);
                                if(!done){
                                    commandStatus.set(commandID, false);
                                }
                            }


                            if(done){
                                toutput.writeBoolean(!done);
                                toutput.writeUTF("command already finished or terminated");
                            }else{

                            }
                        }
                        else{
                            toutput.writeBoolean(false);
                            toutput.writeUTF("command with this id doesn't exist");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    public void run() {
        try {
            runTerminator();
            while (true) {
                String command = inputStream.readUTF();
                File file;

                byte[] data;

                //testing purposes - server prints out the client command that was sent
                //System.out.println(command);

                //implement commands
                if (command.equals("get")) {
                    String[] input = inputStream.readUTF().split(" ");
                    String filename = input[0];
                    boolean concurrent = input.length > 1 && input[1].equals("&");
                    file = path.resolve(filename).toFile();

                    if(file.isFile()){
                        outputStream.writeLong(file.length());
                        fileInput = new FileInputStream(file);
                        data = new byte[1000];
                        if(concurrent){
                            int commandId = -1;
                            synchronized(commandStatus){
                                commandId = commandStatus.size();
                                commandStatus.add(true);
                            }
                            outputStream.writeInt(commandId);
                            final int finalCommandId = commandId;
                            try {
                                int read;
                                boolean terminated = false;
                                while (!terminated &&(read=fileInput.read(data))!=-1){
                                    synchronized(commandStatus){
                                        terminated = !commandStatus.get(finalCommandId);
                                    }
                                    outputStream.write(data,0,read);
                                    outputStream.flush();
                                }
                                fileInput.close();

                                if(terminated){
                                    toutput.writeBoolean(true);
                                    toutput.writeUTF("command terminated");
                                }

                                synchronized(commandStatus){
                                    commandStatus.set(commandId,false);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }else{
                            int read;
                            while ((read=fileInput.read(data))!=-1){
                                outputStream.write(data,0,read);
                                outputStream.flush();
                            }
                            fileInput.close();
                        }
                    }else{
                        outputStream.writeLong(0); //No file
                    }


                } else if (command.equals("put")) {

                    String[] input = inputStream.readUTF().split(" ");
                    String filename = input[0];
                    boolean concurrent = input.length > 1 && input[1].equals("&");
                    final long finalSize = inputStream.readLong();

                    if(finalSize > 0) {
                        data = new byte[1000];
                        file = path.resolve(filename).toFile();
                        fileOutput = new FileOutputStream(file);
                        final String fileId = file.getAbsolutePath();
                        if(concurrent){
                            int commandId = -1;
                            synchronized(commandStatus){
                                commandId = commandStatus.size();
                                commandStatus.add(true);
                            }
                            outputStream.writeInt(commandId);
                            final int finalCommandId = commandId;

                            try {
                                boolean inUse;
                                do{
                                    synchronized(filesInUse){
                                        if(!filesInUse.contains(fileId)){
                                            filesInUse.add(fileId);
                                            inUse = false;
                                        }
                                        else{
                                            inUse = true;
                                        }
                                    }
                                    if(inUse){
                                        filesInUse.wait();
                                    }
                                }while(inUse);

                                int read;
                                long size = finalSize;
                                boolean terminated = false;
                                while (size > 0 && !terminated &&
                                        (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1) {
                                    synchronized(commandStatus){
                                        terminated = !commandStatus.get(finalCommandId);
                                    }
                                    fileOutput.write(data, 0, read);
                                    size -= read;
                                }
                                fileOutput.close();

                                if(terminated){
                                    file.delete();
                                    toutput.writeBoolean(true);
                                    toutput.writeUTF("command terminated");
                                }

                                synchronized(filesInUse){
                                    int nIndex = filesInUse.indexOf(fileId);
                                    if(nIndex < 0){
                                        throw new Exception("File in use list is invalid");
                                    }
                                    filesInUse.remove(nIndex);
                                    filesInUse.notifyAll();
                                }

                                synchronized(commandStatus){
                                    commandStatus.set(commandId,false);
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                                file.delete();
                            }finally{
                                inputStream.reset();
                            }
                        }else{
                            boolean inUse;
                            do{
                                synchronized(filesInUse){
                                    if(!filesInUse.contains(fileId)){
                                        filesInUse.add(fileId);
                                        inUse = false;
                                    }
                                    else{
                                        inUse = true;
                                    }
                                }
                                if(inUse){
                                    filesInUse.wait();
                                }
                            }while(inUse);
                            int read;
                            long size = finalSize;
                            while (size > 0 && (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1) {
                                fileOutput.write(data, 0, read);
                                size -= read;
                            }
                            fileOutput.close();
                        }

                    }
                } else if (command.equals("delete")) {
                    String del = inputStream.readUTF();
                    file = path.resolve(del).toFile();

                    if(file.isFile() && file.delete()){
                        outputStream.writeUTF("File deleted");
                    }else{
                        outputStream.writeUTF("File does not exist");
                    }
                } else if (command.equals("ls")) {
                    file = path.toFile();
                    String[] list = file.list();
                    String result = "";
                    if(list != null){
                        result = String.join(" ",list);
                    }
                    outputStream.writeUTF(result);

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
                    String dir = inputStream.readUTF();

                    file = path.resolve(dir).toFile();

                    if(file.mkdir()){
                        outputStream.writeUTF("Directory is created");
                    }else{
                        outputStream.writeUTF("Directory could not be created");
                    }
                } else if (command.equals("pwd")) {
                    outputStream.writeUTF(path.normalize().toString());
                } else if (command.equals("quit")) {
                    break;
                } else {
                    System.out.println("Error");
                }

            }
            inputStream.close();
            outputStream.close();
            tinput.close();
            toutput.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

