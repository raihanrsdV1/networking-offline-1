package FileServer;

import java.util.ArrayList;
import java.util.List;

public class UploadSession {
    String username;
    String fileName;
    long expectedSize;
    long receivedSize;
    int chunkSize;
    List<byte[]> chunks;
    
    public UploadSession(String username, String fileName, long expectedSize, int chunkSize) {
        this.username = username;
        this.fileName = fileName;
        this.expectedSize = expectedSize;
        this.chunkSize = chunkSize;
        this.receivedSize = 0;
        this.chunks = new ArrayList<>();
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public long getExpectedSize() {
        return expectedSize;
    }
    
    public long getReceivedSize() {
        return receivedSize;
    }
    
    public int getChunkSize() {
        return chunkSize;
    }
    
    public List<byte[]> getChunks() {
        return chunks;
    }
    
    public void addChunk(byte[] chunk) {
        chunks.add(chunk);
        receivedSize += chunk.length;
    }
}
