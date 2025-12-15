package FileServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class Server {
    private static final int PORT = 6666;
    private static final int NOTIFY_PORT = 6667;
    public static final String BASE_DIRECTORY = "server_files";
    private static final String FILES_LOG = BASE_DIRECTORY + File.separator + "files.log";
    
    // Track online users
    private static Set<String> onlineUsers = new HashSet<>();

    // Track notification channels: username -> ObjectOutputStream
    private static Map<String, ObjectOutputStream> notifierStreams = new HashMap<>();
    
    // Track all registered users (who have connected at least once)
    private static Set<String> registeredUsers = new HashSet<>();
    
    // Track all files: username -> List of FileInfo
    private static Map<String, List<FileInfo>> userFiles = new HashMap<>();
    
    // Track active uploads: fileID -> UploadSession
    private static Map<String, UploadSession> activeUploads = new HashMap<>();
    
    // Track all requests by ID
    private static Map<String, FileRequest> requestsById = new HashMap<>();
    
    // Message manager for persistent message storage
    private static MessageManager messageManager = new MessageManager();
    
    // Activity log for tracking uploads, downloads, and requests
    private static ActivityLog activityLog = new ActivityLog();
    
    // Configuration parameters
    public static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB
    public static final int MIN_CHUNK_SIZE = 50 * 1024; // 50 KB
    public static final int MAX_CHUNK_SIZE = 200 * 1024; // 200 KB
    
    private static int currentBufferSize = 0;
    private static Random random = new Random();
    
    public static void main(String[] args) {
        // Create base directory for server files
        File baseDir = new File(BASE_DIRECTORY);
        if (!baseDir.exists()) {
            baseDir.mkdir();
            System.out.println("Created base directory: " + BASE_DIRECTORY);
        } else {
            // Load existing users from directories
            loadExistingUsers();
            // Load file metadata from log
            loadFilesFromLog();
        }
        
        // Ensure files.log exists
        File logFile = new File(FILES_LOG);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
                System.out.println("Created files log: " + FILES_LOG);
            } catch (IOException e) {
                System.err.println("Error creating files log: " + e.getMessage());
            }
        }
        
        try (ServerSocket welcomeSocket = new ServerSocket(PORT);
            ServerSocket notifySocket = new ServerSocket(NOTIFY_PORT)) {
            System.out.println("Server started on port " + PORT);
            System.out.println("Notification server started on port " + NOTIFY_PORT);
            System.out.println("Waiting for connections...");

            Thread notifyAcceptThread = new Thread(() -> {
                while (true) {
                    try {
                        Socket socket = notifySocket.accept();
                        NotificationWorker notificationWorker = new NotificationWorker(socket);
                        notificationWorker.start();
                    } catch (IOException e) {
                        System.err.println("Notification server error: " + e.getMessage());
                        break;
                    }
                }
            });
            notifyAcceptThread.setDaemon(true);
            notifyAcceptThread.start();
            
            while (true) {
                Socket socket = welcomeSocket.accept();
                System.out.println("New connection from: " + socket.getInetAddress() + ":" + socket.getPort());
                
                // Create worker thread for this client
                Worker worker = new Worker(socket);
                worker.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

    private static void loadExistingUsers() {
        File baseDir = new File(BASE_DIRECTORY);
        File[] userDirs = baseDir.listFiles(File::isDirectory);
        
        if (userDirs != null && userDirs.length > 0) {
            System.out.println("Loading existing users from directories...");
            for (File userDir : userDirs) {
                String username = userDir.getName();
                registeredUsers.add(username);
                System.out.println("  - Loaded user: " + username);
            }
            System.out.println("Total users loaded: " + registeredUsers.size());
        } else {
            System.out.println("No existing users found.");
        }
    }
    
    private static void loadFilesFromLog() {
        File logFile = new File(FILES_LOG);
        if (!logFile.exists()) {
            System.out.println("No files log found. Starting fresh.");
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            System.out.println("Loading files from log...");
            String line;
            int fileCount = 0;
            
            while ((line = reader.readLine()) != null) {
                // Format: username|fileId|fileName|fileSize|isPublic
                String[] parts = line.split("\\|");
                if (parts.length == 5) {
                    String username = parts[0];
                    String fileId = parts[1];
                    String fileName = parts[2];
                    long fileSize = Long.parseLong(parts[3]);
                    boolean isPublic = Boolean.parseBoolean(parts[4]);
                    
                    FileInfo fileInfo = new FileInfo(fileId, fileName, fileSize, isPublic);
                    userFiles.computeIfAbsent(username, k -> new ArrayList<>()).add(fileInfo);
                    fileCount++;
                }
            }
            
            System.out.println("Total files loaded: " + fileCount);
        } catch (IOException e) {
            System.err.println("Error loading files log: " + e.getMessage());
        }
    }
    
    private static void saveFileToLog(String username, FileInfo fileInfo) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILES_LOG, true))) {
            // Format: username|fileId|fileName|fileSize|isPublic
            String line = username + "|" + fileInfo.getFileId() + "|" + 
                         fileInfo.getFileName() + "|" + fileInfo.getFileSize() + "|" + 
                         fileInfo.isPublic();
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to files log: " + e.getMessage());
        }
    }
    
    private static void removeFileFromLog(String username, String fileName) {
        File logFile = new File(FILES_LOG);
        File tempFile = new File(FILES_LOG + ".tmp");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                // Keep line if it doesn't match username and filename
                if (parts.length == 5 && !(parts[0].equals(username) && parts[2].equals(fileName))) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Error updating files log: " + e.getMessage());
            return;
        }
        
        // Replace old log with updated one
        if (!logFile.delete() || !tempFile.renameTo(logFile)) {
            System.err.println("Error replacing files log");
        }
    }
    
    public synchronized static boolean loginUser(String username) {
        // Check if user is already online
        if (onlineUsers.contains(username)) {
            // System.out.println("User already logged in!");
            return false; 
        }

        onlineUsers.add(username);
        
        // Create directory if first time user
        if (!registeredUsers.contains(username)) {
            String userDir = BASE_DIRECTORY + File.separator + username;
            File dir = new File(userDir);
            if (!dir.exists()) {
                dir.mkdir();
                System.out.println("Created directory for new user: " + username);
            }
            registeredUsers.add(username);
        }
        
        System.out.println("User logged in: " + username);
        return true;
    }
    
    public synchronized static void logoutUser(String username) {
        onlineUsers.remove(username);
        System.out.println("User logged out: " + username);
    }

    public static synchronized void registerNotifier(String username, ObjectOutputStream out) {
        notifierStreams.put(username, out);
    }

    public static synchronized void unregisterNotifier(String username, ObjectOutputStream out) {
        ObjectOutputStream current = notifierStreams.get(username);
        if (current == out) {
            notifierStreams.remove(username);
        }
    }

    public static synchronized void sendNotification(String username, String message) {
        ObjectOutputStream notifyOut = notifierStreams.get(username);
        if (notifyOut == null) return;
        try {
            synchronized (notifyOut) {
                notifyOut.writeObject(message);
                notifyOut.flush();
            }
        } catch (IOException e) {
            notifierStreams.remove(username);
        }
    }
    

    public synchronized static Map<String, Boolean> getAllUsers() {
        Map<String, Boolean> allUsers = new HashMap<>();
        for (String username : registeredUsers) {
            allUsers.put(username, onlineUsers.contains(username));
        }
        return allUsers;
    }

    public synchronized static List<FileInfo> getUserFiles(String username){
        return userFiles.getOrDefault(username, new ArrayList<>());
    }
    
    public synchronized static Map<String, List<FileInfo>> getAllPublicFiles(String excludeUsername) {
        Map<String, List<FileInfo>> publicFiles = new HashMap<>();
        
        for (Map.Entry<String, List<FileInfo>> entry : userFiles.entrySet()) {
            String owner = entry.getKey();
            // Skip the requesting user's files
            if (owner.equals(excludeUsername)) {
                continue;
            }
            
            List<FileInfo> userPublicFiles = new ArrayList<>();
            for (FileInfo file : entry.getValue()) {
                if (file.isPublic()) {
                    userPublicFiles.add(file);
                }
            }
            
            if (!userPublicFiles.isEmpty()) {
                publicFiles.put(owner, userPublicFiles);
            }
        }
        
        return publicFiles;
    }
    
    public synchronized static FileInfo getFileInfo(String owner, String fileName) {
        List<FileInfo> files = userFiles.get(owner);
        if (files == null) return null;
        
        for (FileInfo file : files) {
            if (file.getFileName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }
    
    public synchronized static String getUserDirectory(String username) {
        return BASE_DIRECTORY + File.separator + username;
    }
    
    public synchronized static boolean fileExists(String username, String fileName) {
        List<FileInfo> files = userFiles.get(username);
        if (files == null) return false;
        
        for (FileInfo file : files) {
            if (file.getFileName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }
    
    public synchronized static String initiateUpload(String username, String fileName, long fileSize) {
        // Check buffer capacity
        if (!canAllocateBuffer(fileSize)) {
            return null; // Cannot allocate
        }
        
        // Generate random chunk size
        int chunkSize = MIN_CHUNK_SIZE + random.nextInt(MAX_CHUNK_SIZE - MIN_CHUNK_SIZE + 1);
        
        // Generate unique file ID
        String fileId = UUID.randomUUID().toString();
        
        // Create upload session
        UploadSession session = new UploadSession(username, fileName, fileSize, chunkSize);
        activeUploads.put(fileId, session);
        
        // Reserve buffer space
        updateBufferSize((int) fileSize);
        
        return fileId + ":" + chunkSize;
    }
    
    public synchronized static boolean receiveChunk(String fileId, byte[] chunk) {
        UploadSession session = activeUploads.get(fileId);
        if (session == null) {
            return false;
        }
        
        session.addChunk(chunk);
        return true;
    }
    
    public synchronized static String completeUpload(String fileId, boolean isPublic) {
        UploadSession session = activeUploads.get(fileId);
        if (session == null) {
            return "ERROR: Upload session not found";
        }
        
        // Verify file size
        if (session.getReceivedSize() != session.getExpectedSize()) {
            // Cleanup failed upload
            activeUploads.remove(fileId);
            updateBufferSize(-(int) session.getExpectedSize());
            return "ERROR: File size mismatch";
        }
        
        // Write file to disk
        try {
            String userDir = BASE_DIRECTORY + File.separator + session.getUsername();
            File file = new File(userDir, session.getFileName());
            
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            for (byte[] chunk : session.getChunks()) {
                fos.write(chunk);
            }
            fos.close();
            
            // Remove old entry if file already exists (replacement)
            List<FileInfo> files = userFiles.get(session.getUsername());
            if (files != null) {
                boolean wasReplaced = files.removeIf(f -> f.getFileName().equals(session.getFileName()));
                if (wasReplaced) {
                    // Remove old entry from log
                    removeFileFromLog(session.getUsername(), session.getFileName());
                }
            }
            
            // Add to user files
            FileInfo fileInfo = new FileInfo(fileId, session.getFileName(), session.getExpectedSize(), isPublic);
            userFiles.computeIfAbsent(session.getUsername(), k -> new ArrayList<>()).add(fileInfo);
            
            // Save to persistent log
            saveFileToLog(session.getUsername(), fileInfo);
            
            // Cleanup
            activeUploads.remove(fileId);
            updateBufferSize(-(int) session.getExpectedSize());
            
            return "SUCCESS: File uploaded successfully";
            
        } catch (IOException e) {
            activeUploads.remove(fileId);
            updateBufferSize(-(int) session.getExpectedSize());
            return "ERROR: " + e.getMessage();
        }
    }
    
    public synchronized static void cancelUpload(String fileId) {
        UploadSession session = activeUploads.remove(fileId);
        if (session != null) {
            updateBufferSize(-(int) session.getExpectedSize());
        }
    }
    

    
    public synchronized static boolean canAllocateBuffer(long fileSize) {
        return (currentBufferSize + fileSize) <= MAX_BUFFER_SIZE;
    }
    
    public synchronized static void updateBufferSize(int delta) {
        currentBufferSize += delta;
    }
    
    // File Request methods
    public static synchronized void addFileRequest(FileRequest request) {
        String recipient = request.getRecipient();

        requestsById.put(request.getRequestId(), request);
        
        if (recipient.equalsIgnoreCase("ALL")) {
            // Broadcast to all registered users except the requester
            for (String username : registeredUsers) {
                if (!username.equals(request.getRequester())) {
                    // Create message for this user
                    String messageId = UUID.randomUUID().toString();
                    String content = "File request (ID: " + request.getRequestId() + "): " + request.getDescription();
                    Message message = new Message(messageId, Message.MessageType.FILE_REQUEST, 
                                                  request.getRequester(), content);
                    messageManager.addMessage(username, message);
                }
            }
        } else {
            // Unicast to specific user
            // Create message for recipient
            String messageId = UUID.randomUUID().toString();
            String content = "File request (ID: " + request.getRequestId() + "): " + request.getDescription();
            Message message = new Message(messageId, Message.MessageType.FILE_REQUEST, 
                                          request.getRequester(), content);
            messageManager.addMessage(recipient, message);
        }
    }

    public static synchronized FileRequest getRequestById(String requestId) {
        return requestsById.get(requestId);
    }

    /**
     * Fulfill a request by ID.
     * - For broadcast requests (recipient = "ALL"): request remains available for others
     * - For unicast requests: request is removed after fulfillment
     * 
     * @param requestId the request ID
     * @param responder the username of the person fulfilling the request
     * @return the FileRequest if found, null otherwise
     */
    public static synchronized FileRequest fulfillRequestById(String requestId, String responder) {
        FileRequest request = requestsById.get(requestId);
        if (request == null) {
            return null;
        }
        
        // Only remove the request if it was a unicast (to a specific user)
        // Broadcast requests ("ALL") remain available for multiple responses
        if (!request.getRecipient().equalsIgnoreCase("ALL")) {
            requestsById.remove(requestId);
        }
        
        return request;
    }
    
    public static synchronized void sendRequestNotification(FileRequest request) {
        String recipient = request.getRecipient();
        
        if (recipient.equalsIgnoreCase("ALL")) {
            // Notify only online users except the requester
            for (String username : onlineUsers) {
                if (!username.equals(request.getRequester())) {
                    try {
                        sendNotification(username, "NEW_FILE_REQUEST (ID: " + request.getRequestId() + ") from " + request.getRequester() + ": " + request.getDescription());
                    } catch (Exception e) {
                        System.err.println("Error sending notification to " + username + ": " + e.getMessage());
                    }
                }
            }
        } else {
            // Notify specific user only if online
            if (onlineUsers.contains(recipient)) {
                try {
                    sendNotification(recipient, "NEW_FILE_REQUEST (ID: " + request.getRequestId() + ") from " + request.getRequester() + ": " + request.getDescription());
                } catch (Exception e) {
                    System.err.println("Error sending notification to " + recipient + ": " + e.getMessage());
                }
            }
            // If offline, message already saved in MessageManager
        }
    }
    
    // Message management methods
    public static synchronized MessageManager getMessageManager() {
        return messageManager;
    }
    
    public static synchronized void sendMessageNotification(String username, String notification) {
        if (onlineUsers.contains(username)) {
            sendNotification(username, notification);
        }
    }

    public static synchronized boolean isUserRegistered(String username) {
        return registeredUsers.contains(username);
    }
    
    // Activity log accessor
    public static synchronized ActivityLog getActivityLog() {
        return activityLog;
    }
}
