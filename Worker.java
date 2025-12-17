package FileServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.File;


public class Worker extends Thread {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    
    public Worker(Socket socket) {
        this.socket = socket;
    }
    

    @Override
    public void run() {
        try {
            // Initialize streams
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            // Handle login
            if (!handleLogin()) {
                socket.close();
                return;
            }
            
            // Show menu and handle client requests
            while (true) {
                showMenu();
                String choice = (String) in.readObject();
                
                if (!handleClientRequest(choice)) {
                    break; // Client wants to disconnect
                }
            }
            
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error with client " + username + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    private boolean handleLogin() throws IOException, ClassNotFoundException {
        // Request username
        out.writeObject("Enter your username: ");
        username = ((String) in.readObject()).trim();
        
        // Try to login
        if (Server.loginUser(username)) {
            out.writeObject("LOGIN_SUCCESS");
            
            // Get unread message count
            MessageManager msgManager = Server.getMessageManager();
            int unreadCount = msgManager.getUnreadCount(username);
            String welcomeMsg = "Welcome " + username + "!";
            if (unreadCount > 0) {
                welcomeMsg += " You have " + unreadCount + " unread message(s).";
            }
            out.writeObject(welcomeMsg);

            return true;
        } else {
            out.writeObject("LOGIN_FAILED");
            out.writeObject("User already logged in. Connection terminated.");
            return false;
        }
    }
    
    private void showMenu() throws IOException {
        StringBuilder menu = new StringBuilder();
        menu.append("\n=== File Server Menu ===\n");
        menu.append("1. View all clients\n");
        menu.append("2. View my files\n");
        menu.append("3. View public files of others\n");
        menu.append("4. Upload a file\n");
        menu.append("5. Download a file\n");
        menu.append("6. Make a file request\n");
        menu.append("7. View unread messages\n");
        menu.append("8. View read messages\n");
        menu.append("9. View activity history\n");
        menu.append("10. Logout\n");
        menu.append("Enter your choice: ");
        out.writeObject(menu.toString());
    }
    
    private boolean handleClientRequest(String choice) throws IOException, ClassNotFoundException {
        switch (choice.trim()) {
            case "1":
                handleViewAllClients();
                break;
            case "2":
                handleViewMyFiles();
                break;
            case "3":
                handleViewPublicFiles();
                break;
            case "4":
                handleUploadFile();
                break;
            case "5":
                handleDownloadFile();
                break;
            case "6":
                handleFileRequest();
                break;
            case "7":
                handleViewMessages();
                break;
            case "8":
                handleViewReadMessages();
                break;
            case "9":
                handleViewHistory();
                break;
            case "10":
                out.writeObject("Logging out...");
                return false;
            default:
                out.writeObject("Invalid choice. Please try again.");
        }
        return true;
    }
    
    private void handleViewAllClients() throws IOException {
        Map<String, Boolean> allUsers = Server.getAllUsers();
        StringBuilder userList = new StringBuilder("\n=== All Clients ===\n");
        
        for (Map.Entry<String, Boolean> entry : allUsers.entrySet()) {
            String user = entry.getKey();
            boolean isOnline = entry.getValue();
            userList.append(user);
            if (isOnline) {
                userList.append(" [ONLINE]");
            } else {
                userList.append(" [OFFLINE]");
            }
            userList.append("\n");
        }
        
        out.writeObject(userList.toString());
    }
    
    private void handleViewMyFiles() throws IOException {
        List<FileInfo> files = Server.getUserFiles(this.username);
        StringBuilder fileList = new StringBuilder("\n=== All My Files ===\n");
        if(files.isEmpty()){
            fileList.append("\nNo Files are currently Uploaded\n");
        } else{
            fileList.append("\nFile ID\t\tFile Name\t\tStatus(Public/Private)\t\tSize(in Bytes)\n");
        }
        Integer cnt = 1;
        for(FileInfo file: files){
            fileList.append(String.valueOf(cnt) + ") " + file.getFileId() + "\t\t" + file.getFileName() + "\t\t" );
            if(file.isPublic()){
                fileList.append("Public\t\t");
            } else{
                fileList.append("Private\t\t");
            }

            fileList.append(String.valueOf(file.getFileSize()) + "\n");
            cnt++;
        }

        fileList.append("\nTotal files: " + String.valueOf(cnt - 1));

        out.writeObject(fileList.toString());
    }
    
    private void handleViewPublicFiles() throws IOException {
        Map<String, List<FileInfo>> publicFiles = Server.getAllPublicFiles(username);
        StringBuilder fileList = new StringBuilder("\n=== Public Files of Other Users ===\n");
        
        if (publicFiles.isEmpty()) {
            fileList.append("\nNo public files available from other users.\n");
        } else {
            for (Map.Entry<String, List<FileInfo>> entry : publicFiles.entrySet()) {
                String owner = entry.getKey();
                fileList.append("\n--- Files from " + owner + " ---\n");
                
                int count = 1;
                for (FileInfo file : entry.getValue()) {
                    fileList.append(count + ") ");
                    fileList.append(file.getFileId() + "\t\t");
                    fileList.append(file.getFileName() + "\t\t");
                    fileList.append(file.getFileSize() + " bytes\n");
                    count++;
                }
            }
        }
        
        out.writeObject(fileList.toString());
    }
    
    private void handleUploadFile() throws IOException, ClassNotFoundException {
        // Receive upload mode first
        String mode = (String) in.readObject();
        boolean isResponseToRequest = false;
        String requestId = null;
        if (mode != null && mode.equals("REQUEST_UPLOAD")) {
            isResponseToRequest = true;
            requestId = ((String) in.readObject()).trim();
        }

        // Receive file info from client
        String fileName = (String) in.readObject();
        long fileSize = (long) in.readObject();
        boolean isPublic = (boolean) in.readObject();

        if (isResponseToRequest) {
            // Per spec: response uploads are public by default
            isPublic = true;
        }
        
        // Check if client cancelled (file not found)
        if (fileName.equals("CANCEL_UPLOAD")) {
            out.writeObject("Upload cancelled.");
            return;
        }

        // Validate request id (if this upload is in response to a request)
        if (isResponseToRequest) {
            if (requestId == null || requestId.isEmpty() || Server.getRequestById(requestId) == null) {
                out.writeObject("INVALID_REQUEST_ID");
                return;
            }
        }
        
        System.out.println("Upload request from " + username + ": " + fileName + " (" + fileSize + " bytes)");
        
        // Check if file already exists
        if (Server.fileExists(username, fileName)) {
            out.writeObject("FILE_EXISTS");
            
            // Wait for client decision
            String decision = (String) in.readObject();
            if (decision.equals("CANCEL")) {
                out.writeObject("UPLOAD_CANCELLED");
                return;
            } else if (decision.startsWith("RENAME:")) {
                fileName = decision.substring(7);
                System.out.println("File renamed to: " + fileName);
            } else if (decision.equals("REPLACE")) {
                System.out.println("Replacing existing file: " + fileName);
            }
        } else {
            out.writeObject("FILE_NEW");
        }
        
        // Initiate upload on server
        String response = Server.initiateUpload(username, fileName, fileSize);
        
        if (response == null) {
            out.writeObject("UPLOAD_REJECTED:Server buffer full. Cannot accept upload.");
            return;
        }
        
        // Parse response (fileId:chunkSize)
        String[] parts = response.split(":");
        String fileId = parts[0];
        int chunkSize = Integer.parseInt(parts[1]);
        
        // Send confirmation to client with chunk size
        out.writeObject("UPLOAD_APPROVED:" + fileId + ":" + chunkSize);
        
        // Receive chunks
        while (true) {
            String msg = (String) in.readObject();
            
            if (msg.equals("CHUNK")) {
                byte[] chunk = (byte[]) in.readObject();
                
                if (Server.receiveChunk(fileId, chunk)) {
                    // smol delay

                    // try {
                    //     Thread.sleep(1000); 
                    // } catch (InterruptedException e) {
                    //     Thread.currentThread().interrupt();
                    // }
                    out.writeObject("ACK");
                } else {
                    out.writeObject("ERROR");
                    Server.cancelUpload(fileId);
                    return;
                }
            } else if (msg.equals("COMPLETE")) {
                // Complete the upload
                String result = Server.completeUpload(fileId, isPublic);
                out.writeObject(result);
                System.out.println("Upload completed for " + username + ": " + fileName);
                
                // Send notification to uploader
                MessageManager msgManager = Server.getMessageManager();
                String messageId = UUID.randomUUID().toString();
                String content = "Successfully uploaded file: " + fileName;
                Message uploadMsg = new Message(messageId, Message.MessageType.UPLOAD_COMPLETE, 
                                               "Server", content);
                msgManager.addMessage(username, uploadMsg);
                
                // Notify if online
                Server.sendMessageNotification(username, "UPLOAD_COMPLETE: " + fileName);
                
                // Log activity
                String uploadDesc = isPublic ? "Public file" : "Private file";
                Server.getActivityLog().logActivity(username, fileName, ActivityLog.ActivityType.UPLOAD, uploadDesc);

                // If this upload was a response to a request, fulfill it and notify requester
                if (isResponseToRequest && requestId != null) {
                    FileRequest req = Server.fulfillRequestById(requestId, username);
                    if (req != null) {
                        String requester = req.getRequester();

                        String requesterMessageId = UUID.randomUUID().toString();
                        String requesterContent = "Your request (ID: " + requestId + ") has been fulfilled by " + username +
                                ". Uploaded file: " + fileName;
                        Message requesterMsg = new Message(requesterMessageId, Message.MessageType.REQUEST_FULFILLED,
                                username, requesterContent);
                        msgManager.addMessage(requester, requesterMsg);

                        Server.sendMessageNotification(requester, "REQUEST_FULFILLED (ID: " + requestId + ") by " + username + ": " + fileName);
                    }
                }
                
                return;
            } else {
                out.writeObject("ERROR: Invalid message");
                Server.cancelUpload(fileId);
                return;
            }
        }
    }
    
    private void handleDownloadFile() throws IOException, ClassNotFoundException {
        // Show available files (own files + public files from others)
        StringBuilder availableFiles = new StringBuilder("\n=== Available Files for Download ===\n");
        
        // Show user's own files (both public and private)
        List<FileInfo> myFiles = Server.getUserFiles(username);
        if (!myFiles.isEmpty()) {
            availableFiles.append("\n--- Your Files ---\n");
            int count = 1;
            for (FileInfo file : myFiles) {
                availableFiles.append(count + ") ");
                availableFiles.append(file.getFileName() + "\t\t");
                availableFiles.append("(" + (file.isPublic() ? "Public" : "Private") + ")\t\t");
                availableFiles.append(file.getFileSize() + " bytes\n");
                count++;
            }
        }
        
        // Show only public files from others
        Map<String, List<FileInfo>> publicFiles = Server.getAllPublicFiles(username);
        if (!publicFiles.isEmpty()) {
            for (Map.Entry<String, List<FileInfo>> entry : publicFiles.entrySet()) {
                String owner = entry.getKey();
                availableFiles.append("\n--- Files from " + owner + " ---\n");
                
                int count = 1;
                for (FileInfo file : entry.getValue()) {
                    availableFiles.append(count + ") ");
                    availableFiles.append(file.getFileName() + "\t\t");
                    availableFiles.append(file.getFileSize() + " bytes\n");
                    count++;
                }
            }
        }
        
        out.writeObject(availableFiles.toString());
        
       
        String ownerName = (String) in.readObject();
        
        
        if (ownerName.equals("CANCEL_DOWNLOAD")) {
            out.writeObject("Download cancelled.");
            return;
        }
        
        String fileName = (String) in.readObject();
        
        // Validate file access
        FileInfo fileInfo = Server.getFileInfo(ownerName, fileName);
        if (fileInfo == null) {
            out.writeObject("ERROR:File not found");
            return;
        }
        
        // Check if user has access (own file or public file)
        if (!ownerName.equals(username) && !fileInfo.isPublic()) {
            out.writeObject("ERROR:Access denied - file is private");
            return;
        }
        
        // Get file from disk
        String filePath = Server.BASE_DIRECTORY + File.separator + ownerName + File.separator + fileName;
        java.io.File file = new java.io.File(filePath);
        
        if (!file.exists()) {
            out.writeObject("ERROR:File not found on server");
            return;
        }
        
        // Send approval with file size
        out.writeObject("DOWNLOAD_APPROVED:" + file.length());
        
        // Send file in chunks (MAX_CHUNK_SIZE, no ACK needed)
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[Server.MAX_CHUNK_SIZE];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) > 0) {
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                out.writeObject(chunk);

                // Add delay to simulate slow network / test concurrent downloads
                // try {
                //     Thread.sleep(100); 
                // } catch (InterruptedException e) {
                //     Thread.currentThread().interrupt();
                // }

            }
            
            // Send completion signal
            out.writeObject("DOWNLOAD_COMPLETE");
            System.out.println("Download completed for " + username + ": " + fileName + " from " + ownerName);
            
            // Send notification to downloader
            MessageManager msgManager = Server.getMessageManager();
            String messageId = UUID.randomUUID().toString();
            String content = "Successfully downloaded file: " + fileName + " from " + ownerName;
            Message downloadMsg = new Message(messageId, Message.MessageType.DOWNLOAD_COMPLETE, 
                                             "Server", content);
            msgManager.addMessage(username, downloadMsg);
            

            Server.sendMessageNotification(username, "DOWNLOAD_COMPLETE: " + fileName);
            

            String downloadDesc = "Downloaded from " + ownerName;
            Server.getActivityLog().logActivity(username, fileName, ActivityLog.ActivityType.DOWNLOAD, downloadDesc);
            
        } catch (IOException e) {
            out.writeObject("ERROR:" + e.getMessage());
            System.err.println("Error during download: " + e.getMessage());
        }
    }
    
    private void handleFileRequest() throws IOException, ClassNotFoundException {
        // Get file description from client
        out.writeObject("ENTER_DESCRIPTION");
        out.flush();
        String description = (String) in.readObject();
        
        if (description == null || description.trim().isEmpty()) {
            out.writeObject("ERROR:Description cannot be empty");
            return;
        }
        
        // Get recipient (username or ALL)
        out.writeObject("ENTER_RECIPIENT");
        out.flush();
        String recipient = (String) in.readObject();
        
        if (recipient == null || recipient.trim().isEmpty()) {
            out.writeObject("ERROR:Recipient cannot be empty");
            return;
        }
        
        recipient = recipient.trim();
        
        // Validate recipient
        if (!recipient.equalsIgnoreCase("ALL")) {
            if (recipient.equals(username)) {
                out.writeObject("ERROR:Cannot send request to yourself");
                return;
            }

            // Recipient may be offline; only reject if user was never registered
            if (!Server.isUserRegistered(recipient)) {
                out.writeObject("ERROR:User '" + recipient + "' does not exist");
                return;
            }
        }
        
        // Create file request
        FileRequest request = new FileRequest(username, description, recipient);
        
        // Add to pending requests
        Server.addFileRequest(request);
        
        // Send notification to recipients
        Server.sendRequestNotification(request);
        
        // Confirm to requester
        if (recipient.equalsIgnoreCase("ALL")) {
            out.writeObject("SUCCESS:Request broadcast to all online users");
        } else {
            out.writeObject("SUCCESS:Request sent to " + recipient);
        }
        out.flush();
        
        // Log activity
        String requestDesc = "To: " + recipient + " - " + description;
        Server.getActivityLog().logActivity(username, "[REQUEST]", ActivityLog.ActivityType.REQUEST, requestDesc);
    }
    
    private void handleViewMessages() throws IOException {
        MessageManager msgManager = Server.getMessageManager();
        List<Message> unreadMessages = msgManager.getUnreadMessages(username);
        
        if (unreadMessages.isEmpty()) {
            out.writeObject("NO_MESSAGES");
            out.flush();
            return;
        }
        
        // Send unread messages
        out.writeObject("UNREAD_MESSAGES");
        out.writeObject(unreadMessages.size());
        
        List<String> messageIds = new ArrayList<>();
        for (Message msg : unreadMessages) {
            out.writeObject(msg.toString());
            messageIds.add(msg.getMessageId());
        }
        out.flush();
        
        // Mark as read
        msgManager.markMessagesAsRead(username, messageIds);
    }
    
    private void handleViewReadMessages() throws IOException {
        MessageManager msgManager = Server.getMessageManager();
        List<Message> readMessages = msgManager.getReadMessages(username);
        
        if (readMessages.isEmpty()) {
            out.writeObject("NO_MESSAGES");
            out.flush();
            return;
        }
        
        // Send read messages
        out.writeObject("READ_MESSAGES");
        out.writeObject(readMessages.size());
        
        for (Message msg : readMessages) {
            out.writeObject(msg.toString());
        }
        out.flush();
    }
    
    private void handleViewHistory() throws IOException {
        ActivityLog activityLog = Server.getActivityLog();
        List<ActivityLog.Activity> activities = activityLog.getUserActivities(username);
        
        if (activities.isEmpty()) {
            out.writeObject("NO_HISTORY");
            out.flush();
            return;
        }
        
        // Send activity history
        out.writeObject("ACTIVITY_HISTORY");
        out.writeObject(activities.size());
        
        for (ActivityLog.Activity activity : activities) {
            out.writeObject(activity.toString());
        }
        out.flush();
    }
    
    private void cleanup() {
        if (username != null) {
            Server.logoutUser(username);
        }
        
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
        
        System.out.println("Connection closed for user: " + username);
    }
}
