import java.io.*;
import java.net.*;
import java.util.*;

public class myftp {
    //initialize socket
    Socket socket = null;
    Socket tsocket = null;

    DataInputStream inputStream = null;
    DataOutputStream outputStream = null;
    DataInputStream tinput = null;
    DataOutputStream toutput = null;
    FileInputStream fileInput = null;
    FileOutputStream fileOutput = null;
    List<Integer> terminatedCommands = new ArrayList<>();
    Boolean nSocketInUse = false;

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
        try {
            Scanner scanner = new Scanner(System.in);

            // establish connection with server
            socket = new Socket(ip, Integer.parseInt(port));
            tsocket = new Socket(ip, Integer.parseInt(tport));

            // obtain input and out streams
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
            tinput = new DataInputStream(tsocket.getInputStream());
            toutput = new DataOutputStream(tsocket.getOutputStream());

            while (true) {
                System.out.print("mytftp> ");
                String input = scanner.nextLine();
                String[] command = input.trim().split(" ");

                byte[] data;

                if (command[0].equals("terminate")) {
                    toutput.writeUTF(command[0]);
                    int id = Integer.parseInt(command[1]);
                    toutput.writeInt(id);
                    boolean success = tinput.readBoolean();
                    if (success) {
                        synchronized (terminatedCommands) {
                            terminatedCommands.add(id);
                            inputStream.reset();
                        }
                    }
                    String result = tinput.readUTF();
                    System.out.println(result);
                    continue;
                }

                boolean busy;
                synchronized (nSocketInUse) {
                    busy = nSocketInUse;
                }
                if (busy) {
                    System.out.println("normal port is busy, you can use terminate or wait for upload/download to finish");
                    continue;
                }

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
                        fileOutput = new FileOutputStream(file);
                        if (concurrent) {
                            int commandId = inputStream.readInt();
                            System.out.println("command is running with id : " + commandId);
                            final String fileId = file.getAbsolutePath();

                            new Thread(() -> {
                                synchronized (nSocketInUse) {
                                    nSocketInUse = true;
                                }
                                try {
                                    int read;
                                    long size = finalSize;
                                    boolean terminated = false;
                                    while (size > 0 && (read = inputStream.read(data, 0, (int) Math.min(data.length, size))) != -1
                                            && !terminated) {
                                        synchronized (terminatedCommands) {
                                            terminated = terminatedCommands.contains(commandId);
                                        }
                                        fileOutput.write(data, 0, read);
                                        size -= read;
                                    }
                                    fileOutput.close();

                                    if (terminated) {
                                        file.delete();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    synchronized (nSocketInUse) {
                                        nSocketInUse = false;
                                    }
                                    try {
                                        inputStream.reset();
                                    } catch (IOException e) {
                                        e.printStackTrace();
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
                        }
                    } else {
                        System.out.println("Did not receive file from server");
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
                        fileInput = new FileInputStream(file);
                        data = new byte[1000];
                        if (concurrent) {
                            int commandId = inputStream.readInt();
                            System.out.println("command is running with id : " + commandId);
                            new Thread(() -> {
                                synchronized (nSocketInUse) {
                                    nSocketInUse = true;
                                }
                                try {
                                    int read;
                                    boolean terminated = false;
                                    while ((read = fileInput.read(data)) != -1 && !terminated) {
                                        synchronized (terminatedCommands) {
                                            terminated = terminatedCommands.contains(commandId);
                                        }
                                        if(!terminated){
                                            outputStream.write(data, 0, read);
                                            outputStream.flush();
                                        }
                                    }
                                    fileInput.close();

                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    synchronized (nSocketInUse) {
                                        nSocketInUse = false;
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
                        }
                    } else {
                        System.out.println("File Not Found");
                        outputStream.writeLong(0); //No file
                    }
                } else if (command[0].equals("delete")) {
                    outputStream.writeUTF(command[1]);
                    System.out.println(inputStream.readUTF());
                } else if (command[0].equals("ls")) {
                    String lsFiles = inputStream.readUTF();
                    System.out.println(lsFiles);

                } else if (command[0].equals("cd")) {
                    outputStream.writeUTF(command[1]);
                    String result = inputStream.readUTF();
                    System.out.println(result);

                } else if (command[0].equals("mkdir")) {
                    outputStream.writeUTF(command[1]);
                    System.out.println(inputStream.readUTF());

                } else if (command[0].equals("pwd")) {
                    String pwd = inputStream.readUTF();
                    System.out.println(pwd);
                } else if (command[0].equals("quit")) {
                    outputStream.writeUTF(input);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Can't establish connection to Server");
            e.printStackTrace();
        } finally {
            socket.close();
            inputStream.close();
            outputStream.close();
            tsocket.close();
            tinput.close();
            toutput.close();
        }

    }
}
