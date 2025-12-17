package FileServer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MessageManager {
    
    private Map<String, List<Message>> userMessages;
    
    public MessageManager() {
        this.userMessages = new HashMap<>();
        loadAllMessages();
    }
    
    // Load all messages from files
    private void loadAllMessages() {
        File baseDir = new File(Server.BASE_DIRECTORY);
        File[] userDirs = baseDir.listFiles(File::isDirectory);
        
        if (userDirs != null) {
            for (File userDir : userDirs) {
                String username = userDir.getName();
                loadMessagesForUser(username);
            }
        }
    }
    
    private void loadMessagesForUser(String username) {
        String filePath = Server.BASE_DIRECTORY + File.separator + username + File.separator + username + Server.MESSAGES_SUFFIX;
        File messageFile = new File(filePath);
        
        if (!messageFile.exists()) {
            userMessages.put(username, new ArrayList<>());
            return;
        }
        
        List<Message> messages = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(messageFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Format: messageId|type|from|contentBase64|timestamp|read
                String[] parts = line.split("\\|", 6);
                if (parts.length == 6) {
                    String messageId = parts[0];
                    Message.MessageType type = Message.MessageType.valueOf(parts[1]);
                    String from = parts[2];
                   
                    String content;
                    try {
                        content = new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                  
                        content = parts[3];
                    }
                    boolean read = Boolean.parseBoolean(parts[5]);
                    
                    Message message = new Message(messageId, type, from, content);
                    if (read) {
                        message.markAsRead();
                    }
                    messages.add(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading messages for " + username + ": " + e.getMessage());
        }
        
        userMessages.put(username, messages);
    }
    
    
    private synchronized void saveMessagesForUser(String username) {
        String userDir = Server.BASE_DIRECTORY + File.separator + username;
        File dir = new File(userDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String filePath = userDir + File.separator + username + Server.MESSAGES_SUFFIX;
        List<Message> messages = userMessages.get(username);
        
        if (messages == null || messages.isEmpty()) {
            return;
        }
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Message message : messages) {
               
                String encodedContent = Base64.getEncoder().encodeToString(
                        message.getContent().getBytes(StandardCharsets.UTF_8));
                String line = message.getMessageId() + "|" +
                             message.getType().name() + "|" +
                             message.getFrom() + "|" +
                             encodedContent + "|" +
                             message.getFormattedTimestamp() + "|" +
                             message.isRead();
                writer.write(line);
                writer.newLine();
                // System.out.println("Saved message for " + username + ": " + message.getMessageId());
            }
        } catch (IOException e) {
            System.err.println("Error saving messages for " + username + ": " + e.getMessage());
        }
    }
    

    public synchronized void addMessage(String username, Message message) {
        List<Message> messages = userMessages.computeIfAbsent(username, k -> new ArrayList<>());
        messages.add(message);
        saveMessagesForUser(username);
    }
    

    public synchronized List<Message> getUnreadMessages(String username) {
        List<Message> messages = userMessages.get(username);
        if (messages == null) {
            return new ArrayList<>();
        }
        
        List<Message> unread = new ArrayList<>();
        for (Message msg : messages) {
            if (!msg.isRead()) {
                unread.add(msg);
            }
        }
        return unread;
    }
    

    public synchronized List<Message> getReadMessages(String username) {
        List<Message> messages = userMessages.get(username);
        if (messages == null) {
            return new ArrayList<>();
        }
        
        List<Message> read = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.isRead()) {
                read.add(msg);
            }
        }
        return read;
    }
    

    public synchronized void markMessagesAsRead(String username, List<String> messageIds) {
        List<Message> messages = userMessages.get(username);
        if (messages == null) {
            return;
        }
        
        for (Message msg : messages) {
            if (messageIds.contains(msg.getMessageId())) {
                msg.markAsRead();
            }
        }
        saveMessagesForUser(username);
    }
    

    public synchronized int getUnreadCount(String username) {
        List<Message> messages = userMessages.get(username);
        if (messages == null) {
            return 0;
        }
        int count = 0;
        for (Message msg : messages) {
            if (!msg.isRead()) {
                count++;
            }
        }
        return count;
    }

}
