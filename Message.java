package FileServer;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum MessageType {
        FILE_REQUEST,
        REQUEST_FULFILLED,
        UPLOAD_COMPLETE,
        DOWNLOAD_COMPLETE
    }
    
    private final String messageId;
    private final MessageType type;
    private final String from;
    private final String content;
    private final LocalDateTime timestamp;
    private boolean read;
    
    public Message(String messageId, MessageType type, String from, String content) {
        this.messageId = messageId;
        this.type = type;
        this.from = from;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.read = false;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public String getFrom() {
        return from;
    }
    
    public String getContent() {
        return content;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public boolean isRead() {
        return read;
    }
    
    public void markAsRead() {
        this.read = true;
    }
    
    public String getFormattedTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return timestamp.format(formatter);
    }
    
    @Override
    public String toString() {
        String typeStr = "";
        switch (type) {
            case FILE_REQUEST:
                typeStr = "[FILE REQUEST]";
                break;
            case REQUEST_FULFILLED:
                typeStr = "[REQUEST FULFILLED]";
                break;
            case UPLOAD_COMPLETE:
                typeStr = "[UPLOAD COMPLETE]";
                break;
            case DOWNLOAD_COMPLETE:
                typeStr = "[DOWNLOAD COMPLETE]";
                break;
        }
        
        return typeStr + " From: " + from + " | " + getFormattedTimestamp() + "\n" + content;
    }
}
