import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
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
    final Map<String, Path> addressRegistry = new HashMap<>();
    final List<Boolean> commandStatus = new ArrayList<>();

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

            new Thread(() -> {
                try {
                    while (true) {
                        //receive incoming client request and establish connection for t-port
                        Socket socket = tserver.accept();

                        //create a new thread
                        TerminatorThread thread = new TerminatorThread(socket, commandStatus);
                        thread.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            new Thread(() -> {
                try {
                    //run infinite loop to get the client requests
                    while (true) {
                        //receive incoming client request and establish connection for n-port
                        Socket socket = server.accept();

                        //create a new thread
                        ClientThread thread = new ClientThread(socket, commandStatus, filesInUse, addressRegistry);
                        thread.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            server.close();
            tserver.close();
            e.printStackTrace();
        }
    }
}

class ClientThread extends Thread {
    Socket socket = null;
    DataInputStream inputStream = null;
    DataOutputStream outputStream = null;
    List<Boolean> commandStatus;
    List<String> filesInUse;
    Map<String, Path> addressRegistry;
    String id;

    Path path;

    //Constructor
    public ClientThread(Socket socket, List<Boolean> commandStatus, List<String> filesInUse, Map<String, Path> addressRegistry) {
        try {
            this.socket = socket;
            this.filesInUse = filesInUse;
            this.commandStatus = commandStatus;
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
            this.addressRegistry = addressRegistry;
            id = socket.getInetAddress().toString();
            if(addressRegistry.get(id) == null){
                addressRegistry.put(id,Paths.get("").toAbsolutePath());
            }
            path = addressRegistry.get(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void run() {
        try {
            String command = inputStream.readUTF();
            File file;

            byte[] data;

            //testing purposes - server prints out the client command that was sent
            //System.out.println(command);

            //implement commands
            if(command.equals("get")) {
                String[] input = inputStream.readUTF().split(" ");
                String filename = input[0];
                boolean concurrent = input.length > 1 && input[1].equals("&");
                file = path.resolve(filename).toFile();
                final String fileId = file.getAbsolutePath();
                if (file.isFile()) {
                    outputStream.writeLong(file.length());
                    data = new byte[1000];
                    if (concurrent) {
                        int commandId = -1;
                        synchronized (commandStatus) {
                            commandId = commandStatus.size();
                            commandStatus.add(true);
                        }
                        outputStream.writeInt(commandId);
                        final int finalCommandId = commandId;
                        try {
                            getFileLock(fileId);
                            FileInputStream fileInput = new FileInputStream(file);
                            int read;
                            boolean terminated = false;
                            while (!terminated && (read = fileInput.read(data)) != -1) {
                                synchronized (commandStatus) {
                                    terminated = !commandStatus.get(finalCommandId);
                                }
                                outputStream.write(data, 0, read);
                                outputStream.flush();
                            }
                            fileInput.close();

                            synchronized (commandStatus) {
                                commandStatus.set(commandId, false);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        getFileLock(fileId);
                        FileInputStream fileInput = new FileInputStream(file);
                        int read;
                        while ((read = fileInput.read(data)) != -1) {
                            outputStream.write(data, 0, read);
                            outputStream.flush();
                        }
                        fileInput.close();
                    }
                    releaseFileLock(fileId);
                } else {
                    outputStream.writeLong(0); //No file
                }


            } else if (command.equals("put")) {

                String[] input = inputStream.readUTF().split(" ");
                String filename = input[0];
                boolean concurrent = input.length > 1 && input[1].equals("&");
                final long finalSize = inputStream.readLong();

                if (finalSize > 0) {
                    data = new byte[1000];
                    file = path.resolve(filename).toFile();
                    final String fileId = file.getAbsolutePath();
                    if (concurrent) {
                        int commandId = -1;
                        synchronized (commandStatus) {
                            commandId = commandStatus.size();
                            commandStatus.add(true);
                        }
                        outputStream.writeInt(commandId);
                        final int finalCommandId = commandId;
                        try {
                            getFileLock(fileId);
                            file.delete();
                            FileOutputStream fileOutput = new FileOutputStream(file);
                            int read;
                            long size = finalSize;
                            boolean terminated = false;
                            while (size > 0 && !terminated &&
                                    (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1) {
                                synchronized (commandStatus) {
                                    terminated = !commandStatus.get(finalCommandId);
                                }
                                fileOutput.write(data, 0, read);
                                size -= read;
                            }
                            fileOutput.close();

                            if (terminated) {
                                file.delete();
                            }
                            synchronized (commandStatus) {
                                commandStatus.set(commandId, false);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            file.delete();
                        }
                    } else {
                        getFileLock(fileId);
                        file.delete();
                        FileOutputStream fileOutput = new FileOutputStream(file);
                        int read;
                        long size = finalSize;
                        while (size > 0 && (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1) {
                            fileOutput.write(data, 0, read);
                            size -= read;
                        }
                        fileOutput.close();
                    }
                    releaseFileLock(fileId);
                }
            } else if (command.equals("delete")) {
                String del = inputStream.readUTF();
                file = path.resolve(del).toFile();
                String fileId = file.getAbsolutePath();
                getFileLock(fileId);

                if (file.isFile() && file.delete()) {
                    outputStream.writeUTF("File deleted");
                } else {
                    outputStream.writeUTF("File does not exist");
                }
                releaseFileLock(fileId);
            } else if (command.equals("ls")) {
                file = path.toFile();
                String[] list = file.list();
                String result = "";
                if (list != null) {
                    result = String.join(" ", list);
                }
                outputStream.writeUTF(result);

            } else if (command.equals("cd")) {
                String cd = inputStream.readUTF();

                file = path.resolve(cd).toFile();
                if (file.isDirectory()) {
                    path = path.resolve(cd);
                    addressRegistry.put(id,path);
                    outputStream.writeUTF("Directory changed");
                } else {
                    outputStream.writeUTF(cd + " is not a valid directory");
                }
            } else if (command.equals("mkdir")) {
                String dir = inputStream.readUTF();

                file = path.resolve(dir).toFile();

                if (file.mkdir()) {
                    outputStream.writeUTF("Directory is created");
                } else {
                    outputStream.writeUTF("Directory could not be created");
                }
            } else if (command.equals("pwd")) {
                outputStream.writeUTF(path.normalize().toString());
            } else if (command.equals("quit")) {
                inputStream.close();
                outputStream.close();
                socket.close();
            } else {
                System.out.println("Error");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getFileLock(String fileId) throws InterruptedException {
        boolean inUse;
        do {
            synchronized (filesInUse) {
                if (!filesInUse.contains(fileId)) {
                    filesInUse.add(fileId);
                    inUse = false;
                } else {
                    inUse = true;
                }
            }
            if (inUse) {
                Thread.sleep(1000);
            }
        } while (inUse);
    }

    private void releaseFileLock(String fileId) throws Exception {
        synchronized (filesInUse) {
            int nIndex = filesInUse.indexOf(fileId);
            if (nIndex < 0) {
                throw new Exception("File in use list is invalid");
            }
            filesInUse.remove(nIndex);
        }
    }
}

class TerminatorThread extends Thread{
    Socket socket = null;
    DataInputStream inputStream;
    DataOutputStream outputStream;
    List<Boolean> commandStatus;
    //Constructor
    public TerminatorThread(Socket socket, List<Boolean> commandStatus) {
        try {
            this.socket = socket;
            this.commandStatus = commandStatus;
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        new Thread(() -> {
            try {
                while (true) {
                    String command = inputStream.readUTF();
                    if (command.equals("terminate")) {
                        int commandID = inputStream.readInt();
                        if (commandStatus.size() > commandID) {
                            boolean done;
                            synchronized (commandStatus) {
                                done = !commandStatus.get(commandID);
                                if (!done) {
                                    commandStatus.set(commandID, false);
                                }
                            }
                            outputStream.writeBoolean(!done);
                            if (done) {
                                outputStream.writeUTF("command already finished or terminated");
                            } else {
                                outputStream.writeUTF("command terminated");
                            }
                        } else {
                            outputStream.writeBoolean(false);
                            outputStream.writeUTF("command with this id doesn't exist");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}

