package FileServer;

public class FileInfo {
    private String fileId;
    private String fileName;
    private long fileSize;
    private boolean isPublic;
    
    public FileInfo(String fileId, String fileName, long fileSize, boolean isPublic) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.isPublic = isPublic;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public boolean isPublic() {
        return isPublic;
    }
}
