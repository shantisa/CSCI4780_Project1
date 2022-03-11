import java.io.*;
import java.net.*;
import java.util.*;

public class myftp {
    final Map<Integer, Socket> workingSockets = new HashMap<>();
    final List<String> filesInUse = new ArrayList<>();

    public static void main(String[] args) {
        try {
            myftp clientSocket = new myftp();
            clientSocket.connection(args[0], args[1], args[2]);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // connection is a function that establishes a connection with the server
    public void connection(String ip, String port, String tport) throws IOException {
        Scanner scanner = new Scanner(System.in);
        // establish connection with server
        Terminator terminator = new Terminator(ip, Integer.parseInt(tport));
        Client client = new Client(ip, Integer.parseInt(port));

        while (true) {
            System.out.print("mytftp> ");
            String input = scanner.nextLine();
            String[] command = input.trim().split(" ");
            terminator.process(command);
            client.process(command);
            if (command[0].equals("quit")) {
                terminator.close();
                break;
            }
        }

    }

    class Terminator {
        String ip;
        int port;
        Socket socket;
        DataInputStream inputStream;
        DataOutputStream outputStream;

        Terminator(String ip, int port) throws IOException {
            this.ip = ip;
            this.port = port;
            // establish connection with server
            socket = new Socket(ip, port);
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        }

        private void process(String[] command) {
            try {
                outputStream.writeUTF(command[0]);

                if (command[0].equals("terminate")) {
                    int id = Integer.parseInt(command[1]);
                    outputStream.writeInt(id);
                    boolean success = inputStream.readBoolean();
                    if (success) {
                        synchronized (workingSockets) {
                            Socket socket = workingSockets.get(id);
                            if(socket != null){
                                socket.close();
                            }
                        }
                    }
                    String result = inputStream.readUTF();
                    System.out.println(result);
                }
            } catch (Exception e) {
                System.out.println("Can't establish connection to Server");
                e.printStackTrace();
            }
        }

        void close() throws IOException {
            outputStream.writeUTF("quit");
            inputStream.close();
            outputStream.close();
            socket.close();
        }
    }

    class Client {
        String ip;
        int port;
        int id;

        Client(String ip, int port) {
            this.ip = ip;
            this.port = port;
            id = ((int) (Math.random()*(253))) + 1;
        }

        private void process(String[] command) {
            try {
                // establish connection with server
                Socket socket = new Socket(ip, port, InetAddress.getByName("127.0.0."+id), 0);
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                byte[] data;

                outputStream.writeUTF(command[0]);

                //implement commands
                if (command[0].equals("get")) {
                    String filename = command[1];
                    boolean concurrent = command.length > 2 && command[2].equals("&");
                    if (concurrent) {
                        outputStream.writeUTF(filename + " &");
                    } else {
                        outputStream.writeUTF(filename);
                    }
                    final long finalSize = inputStream.readLong();

                    if (finalSize > 0) {
                        data = new byte[1000];
                        File file = new File(filename);
                        File parent = file.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        FileOutputStream fileOutput = new FileOutputStream(file);
                        if (concurrent) {
                            int commandId = inputStream.readInt();
                            System.out.println("command is running with id : " + commandId);
                            workingSockets.put(commandId, socket);

                            final String fileId = file.getAbsolutePath();
                            new Thread(() -> {
                                try {
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

                                    int read;
                                    long size = finalSize;
                                    while (size > 0 && (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1) {
                                        fileOutput.write(data, 0, read);
                                        size -= read;
                                    }
                                    fileOutput.close();
                                } catch (Exception ignored) {
                                    System.out.println("Couldn't receive file completely, deleting...");
                                    try {
                                        fileOutput.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    file.delete();
                                } finally {
                                    workingSockets.remove(commandId);
                                    try {
                                        inputStream.close();
                                        outputStream.close();
                                        socket.close();
                                        synchronized (filesInUse) {
                                            int nIndex = filesInUse.indexOf(fileId);
                                            if (nIndex < 0) {
                                                throw new Exception("File in use list is invalid");
                                            }
                                            filesInUse.remove(nIndex);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }).start();
                        } else {
                            int read;
                            long size = finalSize;
                            while (size > 0 && (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1) {
                                fileOutput.write(data, 0, read);
                                size -= read;
                            }
                            fileOutput.close();
                            inputStream.close();
                            outputStream.close();
                            socket.close();
                        }
                    } else {
                        System.out.println("Did not receive file from server");
                        inputStream.close();
                        outputStream.close();
                        socket.close();
                    }
                } else if (command[0].equals("put")) {
                    String filename = command[1];
                    File file = new File(filename);
                    boolean concurrent = command.length > 2 && command[2].equals("&");
                    if (concurrent) {
                        outputStream.writeUTF(file.getName() + " &");
                    } else {
                        outputStream.writeUTF(file.getName());
                    }

                    if (file.isFile()) {
                        outputStream.writeLong(file.length());
                        FileInputStream fileInput = new FileInputStream(file);
                        data = new byte[1000];
                        if (concurrent) {
                            int commandId = inputStream.readInt();
                            System.out.println("command is running with id : " + commandId);
                            workingSockets.put(commandId, socket);

                            new Thread(() -> {
                                try {
                                    int read;
                                    while ((read = fileInput.read(data)) != -1) {
                                        outputStream.write(data, 0, read);
                                        outputStream.flush();
                                    }

                                } catch (Exception ignored) {
                                } finally {
                                    workingSockets.remove(commandId);
                                    try {
                                        fileInput.close();
                                        inputStream.close();
                                        outputStream.close();
                                        socket.close();
                                    } catch (IOException ignored) {
                                    }
                                }
                            }).start();
                        } else {
                            int read;
                            while ((read = fileInput.read(data)) != -1) {
                                outputStream.write(data, 0, read);
                                outputStream.flush();
                            }
                            fileInput.close();
                            inputStream.close();
                            outputStream.close();
                            socket.close();
                        }
                    } else {
                        System.out.println("File Not Found");
                        outputStream.writeLong(0); //No file
                        inputStream.close();
                        outputStream.close();
                        socket.close();
                    }
                } else if (command[0].equals("delete")) {
                    outputStream.writeUTF(command[1]);
                    System.out.println(inputStream.readUTF());
                    inputStream.close();
                    outputStream.close();
                    socket.close();
                } else if (command[0].equals("ls")) {
                    String lsFiles = inputStream.readUTF();
                    System.out.println(lsFiles);
                    inputStream.close();
                    outputStream.close();
                    socket.close();
                } else if (command[0].equals("cd")) {
                    outputStream.writeUTF(command[1]);
                    String result = inputStream.readUTF();
                    System.out.println(result);
                    inputStream.close();
                    outputStream.close();
                    socket.close();
                } else if (command[0].equals("mkdir")) {
                    outputStream.writeUTF(command[1]);
                    System.out.println(inputStream.readUTF());
                    inputStream.close();
                    outputStream.close();
                    socket.close();
                } else if (command[0].equals("pwd")) {
                    String pwd = inputStream.readUTF();
                    System.out.println(pwd);
                } else {
                    inputStream.close();
                    outputStream.close();
                    socket.close();
                }
            } catch (Exception e) {
                System.out.println("Can't establish connection to Server");
                e.printStackTrace();
            }
        }
    }
}
