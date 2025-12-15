package FileServer;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Manages persistent activity logging for uploads, downloads, and file requests.
 * Format: username|fileName|activityType|description|timestamp
 */
public class ActivityLog {
    private static final String BASE_DIRECTORY = "server_files";
    private static final String LOG_FILE = BASE_DIRECTORY + File.separator + "activities.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public enum ActivityType {
        UPLOAD,
        DOWNLOAD,
        REQUEST
    }
    
    // In-memory cache: username -> List of activities
    private Map<String, List<Activity>> userActivities;
    
    public ActivityLog() {
        this.userActivities = new HashMap<>();
        loadActivities();
    }
    
    /**
     * Load all activities from the log file on startup.
     */
    private void loadActivities() {
        File logFile = new File(LOG_FILE);
        if (!logFile.exists()) {
            System.out.println("No activities log found. Starting fresh.");
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            System.out.println("Loading activities from log...");
            String line;
            int count = 0;
            
            while ((line = reader.readLine()) != null) {
                // Format: username|fileName|activityType|descriptionBase64|timestamp
                String[] parts = line.split("\\|", 5);
                if (parts.length == 5) {
                    String username = parts[0];
                    String fileName = parts[1];
                    ActivityType type = ActivityType.valueOf(parts[2]);
                    // Decode Base64 description
                    String description = new String(
                            Base64.getDecoder().decode(parts[3]), 
                            java.nio.charset.StandardCharsets.UTF_8);
                    String timestamp = parts[4];
                    
                    Activity activity = new Activity(username, fileName, type, description, timestamp);
                    userActivities.computeIfAbsent(username, k -> new ArrayList<>()).add(activity);
                    count++;
                }
            }
            System.out.println("Total activities loaded: " + count);
        } catch (IOException e) {
            System.err.println("Error loading activities log: " + e.getMessage());
        }
    }
    
    /**
     * Log a new activity and persist to file.
     */
    public synchronized void logActivity(String username, String fileName, ActivityType type, String description) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        Activity activity = new Activity(username, fileName, type, description, timestamp);
        
        // Add to in-memory cache
        userActivities.computeIfAbsent(username, k -> new ArrayList<>()).add(activity);
        
        // Append to log file
        appendToLog(activity);
        
        System.out.println("Activity logged: " + username + " - " + type + " - " + fileName);
    }
    
    /**
     * Append a single activity to the log file.
     */
    private void appendToLog(Activity activity) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            // Encode description as Base64 to prevent delimiter issues
            String encodedDesc = Base64.getEncoder().encodeToString(
                    activity.getDescription().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            String line = activity.getUsername() + "|" +
                         activity.getFileName() + "|" +
                         activity.getType().name() + "|" +
                         encodedDesc + "|" +
                         activity.getTimestamp();
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to activities log: " + e.getMessage());
        }
    }
    
    /**
     * Get all activities for a specific user.
     */
    public synchronized List<Activity> getUserActivities(String username) {
        return userActivities.getOrDefault(username, new ArrayList<>());
    }
    
    /**
     * Get activities of a specific type for a user.
     */
    public synchronized List<Activity> getUserActivitiesByType(String username, ActivityType type) {
        List<Activity> all = userActivities.getOrDefault(username, new ArrayList<>());
        List<Activity> filtered = new ArrayList<>();
        for (Activity a : all) {
            if (a.getType() == type) {
                filtered.add(a);
            }
        }
        return filtered;
    }
    
    /**
     * Inner class representing a single activity entry.
     */
    public static class Activity {
        private final String username;
        private final String fileName;
        private final ActivityType type;
        private final String description;
        private final String timestamp;
        
        public Activity(String username, String fileName, ActivityType type, String description, String timestamp) {
            this.username = username;
            this.fileName = fileName;
            this.type = type;
            this.description = description != null ? description : "";
            this.timestamp = timestamp;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public ActivityType getType() {
            return type;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            String typeStr;
            switch (type) {
                case UPLOAD:
                    typeStr = "UPLOAD";
                    break;
                case DOWNLOAD:
                    typeStr = "DOWNLOAD";
                    break;
                case REQUEST:
                    typeStr = "REQUEST";
                    break;
                default:
                    typeStr = type.name();
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(typeStr).append("] ");
            sb.append(fileName);
            if (!description.isEmpty()) {
                sb.append(" - ").append(description);
            }
            sb.append(" | ").append(timestamp);
            return sb.toString();
        }
    }
}
