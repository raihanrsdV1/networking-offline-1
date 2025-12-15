package FileServer;

import java.io.Serializable;
import java.util.UUID;

public class FileRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String requestId;
    private final String requester;      // Username who made the request
    private final String description;    // File description
    private final String recipient;      // Recipient username or "ALL" for broadcast
    
    public FileRequest(String requester, String description, String recipient) {
        this.requestId = UUID.randomUUID().toString();
        this.requester = requester;
        this.description = description;
        this.recipient = recipient;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getRequester() {
        return requester;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    @Override
    public String toString() {
        return "Request ID: " + requestId + 
               "\nFrom: " + requester + 
               "\nDescription: " + description;
    }
}
