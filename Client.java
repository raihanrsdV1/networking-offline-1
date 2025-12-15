package FileServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 6666;
    private static final int NOTIFY_PORT = 6667;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket notifySocket;
    private ObjectOutputStream notifyOut;
    private ObjectInputStream notifyIn;
    private Thread notifyThread;
    private Scanner scanner;
    
    public Client() throws IOException {
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        notifySocket = new Socket(SERVER_ADDRESS, NOTIFY_PORT);
        notifyOut = new ObjectOutputStream(notifySocket.getOutputStream());
        notifyIn = new ObjectInputStream(notifySocket.getInputStream());
        scanner = new Scanner(System.in);
    }
    
    public void start() {
        try {

            if (!handleLogin()) {
                System.out.println("Login failed. Exiting...");
                return;
            }
            
            while (true) {
                String menu = (String) in.readObject();
                System.out.print(menu);
                String choice = scanner.nextLine();
                out.writeObject(choice);
                out.flush();
                
                if (choice.trim().equals("10")) {
                    String response = (String) in.readObject();
                    System.out.println(response);
                    break;
                }

                // Handle file upload
                if(choice.trim().equals("4")){
                    handleFileUpload();
                    continue;
                }
                
                // Handle file download
                if(choice.trim().equals("5")){
                    handleFileDownload();
                    continue;
                }
                
                // Handle file request
                if(choice.trim().equals("6")){
                    handleFileRequest();
                    continue;
                }
                
                // Handle view unread messages
                if(choice.trim().equals("7")){
                    handleViewUnreadMessages();
                    continue;
                }
                
                // Handle view read messages
                if(choice.trim().equals("8")){
                    handleViewReadMessages();
                    continue;
                }
                
                // Handle view activity history
                if(choice.trim().equals("9")){
                    handleViewHistory();
                    continue;
                }
                
                // Read and display server response
                String response = (String) in.readObject();
                System.out.println(response);
                
                
            }
            
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    private boolean handleLogin() throws IOException, ClassNotFoundException {
        // Get username prompt
        String prompt = (String) in.readObject();
        System.out.print(prompt);
        
        // Send username
        String username = scanner.nextLine().trim();
        out.writeObject(username);
        
        // Get login result
        String loginStatus = (String) in.readObject();
        String message = (String) in.readObject();
        System.out.println(message);
        
        if (!loginStatus.equals("LOGIN_SUCCESS")) {
            return false;
        }

        // Register to notification channel
        notifyOut.writeObject(username);
        notifyOut.flush();

        // Start notification listener thread
        notifyThread = new Thread(() -> {
            try {
                while (true) {
                    Object msg = notifyIn.readObject();
                    if (msg instanceof String) {
                        System.out.println("\n[NOTIFICATION] " + msg);
                    }
                }
            } catch (Exception ignored) {
            }
        });
        notifyThread.setDaemon(true);
        notifyThread.start();

        return true;
    }
    
    private void handleFileUpload() throws IOException, ClassNotFoundException {
        // First ask if this upload is in response to a request
        System.out.print("Is this upload in response to a request? (yes/no): ");
        String resp = scanner.nextLine().trim();
        boolean isResponseToRequest = resp.equalsIgnoreCase("yes");
        String requestId = null;

        if (isResponseToRequest) {
            System.out.print("Enter request ID: ");
            requestId = scanner.nextLine().trim();
        }

        // Prompt for file path
        System.out.print("Enter file path on your system: ");
        String filePath = scanner.nextLine();
        
        // Read file
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            // Send cancel signal to server (must match server protocol)
            if (isResponseToRequest) {
                out.writeObject("REQUEST_UPLOAD");
                out.writeObject(requestId == null ? "" : requestId);
            } else {
                out.writeObject("NORMAL_UPLOAD");
            }
            out.writeObject("CANCEL_UPLOAD");
            out.writeObject(0L);
            out.writeObject(false);
            return;
        }
        
        String originalFileName = file.getName();
        long fileSize = file.length();
        
        // Prompt for custom file name
        System.out.print("Enter file name (press Enter to use '" + originalFileName + "'): ");
        String customFileName = scanner.nextLine().trim();
        String fileName = customFileName.isEmpty() ? originalFileName : customFileName;
        
        // Prompt for public/private
        boolean isPublic;
        if (isResponseToRequest) {
            // Per spec: response uploads are public by default
            isPublic = true;
            System.out.println("(Response upload) File will be uploaded as PUBLIC.");
        } else {
            System.out.print("Is this file public? (yes/no): ");
            String isPublicStr = scanner.nextLine();
            isPublic = isPublicStr.trim().equalsIgnoreCase("yes");
        }

        // Send upload mode + (optional) requestId to server
        if (isResponseToRequest) {
            out.writeObject("REQUEST_UPLOAD");
            out.writeObject(requestId == null ? "" : requestId);
        } else {
            out.writeObject("NORMAL_UPLOAD");
        }
        
        // Send file info to server
        out.writeObject(fileName);
        out.writeObject(fileSize);
        out.writeObject(isPublic);
        
        // Check for file conflict
        String conflictCheck = (String) in.readObject();
        if (conflictCheck.equals("INVALID_REQUEST_ID")) {
            System.out.println("Invalid request ID. Upload cancelled.");
            return;
        }
        if (conflictCheck.equals("FILE_EXISTS")) {
            System.out.println("\nWarning: A file with name '" + fileName + "' already exists!");
            System.out.println("Choose an option:");
            System.out.println("1. Replace existing file");
            System.out.println("2. Rename and upload");
            System.out.println("3. Cancel upload");
            System.out.print("Enter choice (1/2/3): ");
            
            String choice = scanner.nextLine().trim();
            
            if (choice.equals("1")) {
                out.writeObject("REPLACE");
                System.out.println("Replacing existing file...");
            } else if (choice.equals("2")) {
                System.out.print("Enter new file name: ");
                String newFileName = scanner.nextLine().trim();
                if (newFileName.isEmpty()) {
                    System.out.println("Invalid file name. Cancelling upload.");
                    out.writeObject("CANCEL");
                    String cancelMsg = (String) in.readObject();
                    System.out.println(cancelMsg);
                    return;
                }
                fileName = newFileName;
                out.writeObject("RENAME:" + fileName);
                System.out.println("Uploading with new name: " + fileName);
            } else {
                System.out.println("Cancelling upload...");
                out.writeObject("CANCEL");
                String cancelMsg = (String) in.readObject();
                System.out.println(cancelMsg);
                return;
            }
        }
        
        // Wait for server response
        String response = (String) in.readObject();
        
        if (response.startsWith("UPLOAD_REJECTED:")) {
            System.out.println(response.substring(16));
            return;
        }
        
        if (response.startsWith("UPLOAD_APPROVED:")) {
            String[] parts = response.substring(16).split(":");
            String fileId = parts[0];
            int chunkSize = Integer.parseInt(parts[1]);
            
            System.out.println("Upload approved! File ID: " + fileId);
            System.out.println("Chunk size: " + chunkSize + " bytes");
            
            // Read and send file in chunks
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            int chunkCount = 0;
            
            while ((bytesRead = fis.read(buffer)) > 0) {
                // Send chunk indicator
                out.writeObject("CHUNK");
                
                // Send chunk data
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                out.writeObject(chunk);
                
                // Wait for ACK
                String ack = (String) in.readObject();
                if (!ack.equals("ACK")) {
                    System.out.println("Error during upload: " + ack);
                    fis.close();
                    return;
                }
                
                chunkCount++;
                System.out.println("Sent chunk " + chunkCount + " (" + bytesRead + " bytes)");
            }
            
            fis.close();
            
            // Send completion message
            out.writeObject("COMPLETE");
            
            // Get final result
            String result = (String) in.readObject();
            System.out.println(result);
        }
    }
    
    private void handleFileDownload() throws IOException, ClassNotFoundException {
        // Get and display available files from server
        String availableFiles = (String) in.readObject();
        System.out.println(availableFiles);
        
        // Prompt for owner name
        System.out.print("\nEnter owner name (or 'cancel' to abort): ");
        String ownerName = scanner.nextLine().trim();
        
        if (ownerName.equalsIgnoreCase("cancel") || ownerName.isEmpty()) {
            out.writeObject("CANCEL_DOWNLOAD");
            String cancelMsg = (String) in.readObject();
            System.out.println(cancelMsg);
            return;
        }
        
        out.writeObject(ownerName);
        
        // Prompt for file name
        System.out.print("Enter file name: ");
        String fileName = scanner.nextLine().trim();
        
        if (fileName.isEmpty()) {
            System.out.println("Invalid file name.");
            return;
        }
        
        out.writeObject(fileName);
        
        // Get server response
        String response = (String) in.readObject();
        
        if (response.startsWith("ERROR:")) {
            System.out.println(response.substring(6));
            return;
        }
        
        if (response.startsWith("DOWNLOAD_APPROVED:")) {
            long fileSize = Long.parseLong(response.substring(18));
            
            // Prompt for download path
            System.out.print("Enter download path (directory): ");
            String downloadDir = scanner.nextLine().trim();
            
            if (downloadDir.isEmpty()) {
                downloadDir = ".";
            }
            
            // Create directory if it doesn't exist
            java.io.File dir = new java.io.File(downloadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // Create output file
            String outputPath = downloadDir + java.io.File.separator + fileName;
            java.io.File outputFile = new java.io.File(outputPath);
            
            System.out.println("Downloading " + fileName + " (" + fileSize + " bytes)...");
            
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                long totalReceived = 0;
                int chunkCount = 0;
                
                while (true) {
                    Object obj = in.readObject();
                    
                    if (obj instanceof String) {
                        String msg = (String) obj;
                        if (msg.equals("DOWNLOAD_COMPLETE")) {
                            System.out.println("\nDownload completed successfully!");
                            System.out.println("File saved to: " + outputFile.getAbsolutePath());
                            break;
                        } else if (msg.startsWith("ERROR:")) {
                            System.out.println("Download error: " + msg.substring(6));
                            outputFile.delete();
                            return;
                        }
                    } else if (obj instanceof byte[]) {
                        byte[] chunk = (byte[]) obj;
                        fos.write(chunk);
                        totalReceived += chunk.length;
                        chunkCount++;
                        
                        // Show progress
                        int progress = (int) ((totalReceived * 100) / fileSize);
                        System.out.print("\rProgress: " + progress + "% (" + chunkCount + " chunks)");
                    }
                }
            } catch (IOException e) {
                System.err.println("\nError writing file: " + e.getMessage());
            }
        }
    }
    
    private void handleFileRequest() throws IOException, ClassNotFoundException {
        // Get description prompt
        String prompt1 = (String) in.readObject();
        if (prompt1.equals("ENTER_DESCRIPTION")) {
            System.out.print("Enter file description: ");
            String description = scanner.nextLine();
            out.writeObject(description);
            out.flush();
        } else {
            // Unexpected response - display and return
            System.out.println(prompt1);
            return;
        }
        
        // Get recipient prompt or error
        String prompt2 = (String) in.readObject();
        if (prompt2.equals("ENTER_RECIPIENT")) {
            System.out.print("Enter recipient username (or 'ALL' for broadcast): ");
            String recipient = scanner.nextLine();
            out.writeObject(recipient);
            out.flush();
        } else if (prompt2.startsWith("ERROR:")) {
            // Server returned early with error
            System.out.println("\nError: " + prompt2.substring(6));
            return;
        } else {
            // Unexpected response
            System.out.println(prompt2);
            return;
        }
        
        // Get result
        String result = (String) in.readObject();
        if (result.startsWith("SUCCESS:")) {
            System.out.println("\n" + result.substring(8));
        } else if (result.startsWith("ERROR:")) {
            System.out.println("\nError: " + result.substring(6));
        } else {
            System.out.println("\n" + result);
        }
    }
    
    private void handleViewUnreadMessages() throws IOException, ClassNotFoundException {
        String status = (String) in.readObject();
        
        if (status.equals("NO_MESSAGES")) {
            System.out.println("\nNo unread messages.\n");
            return;
        }
        
        if (status.equals("UNREAD_MESSAGES")) {
            int count = (int) in.readObject();
            System.out.println("\n=== Unread Messages (" + count + ") ===");
            
            for (int i = 0; i < count; i++) {
                String message = (String) in.readObject();
                System.out.println("\n" + (i + 1) + ". " + message);
            }
            
            System.out.println("\n================================\n");
            System.out.println("(Messages have been marked as read)\n");
        }
    }
    
    private void handleViewReadMessages() throws IOException, ClassNotFoundException {
        String status = (String) in.readObject();
        
        if (status.equals("NO_MESSAGES")) {
            System.out.println("\nNo read messages.\n");
            return;
        }
        
        if (status.equals("READ_MESSAGES")) {
            int count = (int) in.readObject();
            System.out.println("\n=== Read Messages (" + count + ") ===");
            
            for (int i = 0; i < count; i++) {
                String message = (String) in.readObject();
                System.out.println("\n" + (i + 1) + ". " + message);
            }
            
            System.out.println("\n================================\n");
        }
    }
    
    private void handleViewHistory() throws IOException, ClassNotFoundException {
        String status = (String) in.readObject();
        
        if (status.equals("NO_HISTORY")) {
            System.out.println("\nNo activity history.\n");
            return;
        }
        
        if (status.equals("ACTIVITY_HISTORY")) {
            int count = (int) in.readObject();
            System.out.println("\n=== Activity History (" + count + " entries) ===");
            
            for (int i = 0; i < count; i++) {
                String activity = (String) in.readObject();
                System.out.println("\n" + (i + 1) + ". " + activity);
            }
            
            System.out.println("\n================================\n");
        }
    }
    
    private void cleanup() {
        try {
            if (scanner != null) scanner.close();
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();

            try {
                if (notifyOut != null) {
                    notifyOut.writeObject("DISCONNECT");
                    notifyOut.flush();
                }
            } catch (Exception ignored) {
            }
            if (notifyOut != null) notifyOut.close();
            if (notifyIn != null) notifyIn.close();
            if (notifySocket != null) notifySocket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        try {
            Client client = new Client();
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
            client.start();
        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }
}
