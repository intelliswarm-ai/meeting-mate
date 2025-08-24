package ai.intelliswarm.meetingmate.data;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONException;


public class MeetingFileManager {
    private static final String TAG = "MeetingFileManager";
    private static final String ROOT_FOLDER = "MeetingMate";
    private static final String MEETINGS_FOLDER = "Meetings";
    private static final String AUDIO_FOLDER = "Audio";
    private static final String TRANSCRIPTS_FOLDER = "Transcripts";
    private static final String SUMMARIES_FOLDER = "Summaries";
    
    private Context context;
    private File rootDirectory;
    
    public MeetingFileManager(Context context) {
        this.context = context;
        initializeDirectories();
    }
    
    private void initializeDirectories() {
        // For Android 11+ (API 30+), use app-specific storage (no permissions needed)
        // For older versions, try external storage first, fallback to app-specific
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+: Use app-specific external storage (no permissions needed)
            File appSpecificDir = context.getExternalFilesDir(null);
            if (appSpecificDir != null) {
                rootDirectory = new File(appSpecificDir, ROOT_FOLDER);
                Log.d(TAG, "Using app-specific storage for Android 11+: " + rootDirectory.getAbsolutePath());
            } else {
                // Fallback to internal storage
                rootDirectory = new File(context.getFilesDir(), ROOT_FOLDER);
                Log.d(TAG, "Fallback to internal storage: " + rootDirectory.getAbsolutePath());
            }
        } else {
            // Android 10 and below: Try external storage first
            try {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                rootDirectory = new File(documentsDir, ROOT_FOLDER);
                Log.d(TAG, "Using external storage for Android 10 and below: " + rootDirectory.getAbsolutePath());
            } catch (Exception e) {
                Log.w(TAG, "External storage not available, using app-specific storage", e);
                rootDirectory = new File(context.getExternalFilesDir(null), ROOT_FOLDER);
            }
        }
        
        // Create the directory
        if (!rootDirectory.exists()) {
            boolean created = rootDirectory.mkdirs();
            Log.d(TAG, "Root directory created: " + created + " at " + rootDirectory.getAbsolutePath());
        } else {
            Log.d(TAG, "Root directory already exists: " + rootDirectory.getAbsolutePath());
        }
        
        // Create subdirectories
        new File(rootDirectory, MEETINGS_FOLDER).mkdirs();
        new File(rootDirectory, AUDIO_FOLDER).mkdirs();
        new File(rootDirectory, TRANSCRIPTS_FOLDER).mkdirs();
        new File(rootDirectory, SUMMARIES_FOLDER).mkdirs();
    }
    
    // Generate folder structure: Year/Month/Day
    private File getMeetingFolder(Date date) {
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM-MMMM", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());
        
        String year = yearFormat.format(date);
        String month = monthFormat.format(date);
        String day = dayFormat.format(date);
        
        File yearFolder = new File(rootDirectory, MEETINGS_FOLDER + "/" + year);
        File monthFolder = new File(yearFolder, month);
        File dayFolder = new File(monthFolder, day);
        
        if (!dayFolder.exists()) {
            dayFolder.mkdirs();
        }
        
        return dayFolder;
    }
    
    // Generate unique meeting ID based on timestamp
    public String generateMeetingId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        return "meeting_" + sdf.format(new Date());
    }
    
    // Save audio file
    public File saveAudioFile(String meetingId, File audioFile) {
        Log.d(TAG, "Saving audio file for meeting: " + meetingId);
        Log.d(TAG, "Source file: " + audioFile.getAbsolutePath() + " (exists: " + audioFile.exists() + ", size: " + audioFile.length() + " bytes)");
        
        File audioFolder = new File(rootDirectory, AUDIO_FOLDER);
        if (!audioFolder.exists()) {
            boolean created = audioFolder.mkdirs();
            Log.d(TAG, "Audio folder created: " + created + " at " + audioFolder.getAbsolutePath());
        }
        
        File destinationFile = new File(audioFolder, meetingId + ".m4a");
        Log.d(TAG, "Destination file: " + destinationFile.getAbsolutePath());
        
        try {
            // First try to rename/move the file (more efficient)
            if (audioFile.renameTo(destinationFile)) {
                Log.d(TAG, "Audio file moved successfully using renameTo()");
                return destinationFile;
            }
            
            // If rename fails, copy the file (more robust)
            Log.w(TAG, "renameTo() failed, attempting to copy file");
            
            java.io.FileInputStream fis = new java.io.FileInputStream(audioFile);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(destinationFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            fis.close();
            fos.close();
            
            Log.d(TAG, "Audio file copied successfully: " + totalBytes + " bytes");
            
            // Delete the original file after successful copy
            if (audioFile.delete()) {
                Log.d(TAG, "Original audio file deleted");
            } else {
                Log.w(TAG, "Failed to delete original audio file");
            }
            
            return destinationFile;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to save audio file", e);
            // Clean up partial destination file if it exists
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            return null;
        }
    }
    
    // Save transcript
    public boolean saveTranscript(String meetingId, String title, String transcript, Date meetingDate) {
        Log.d(TAG, "Saving transcript for meeting: " + meetingId + ", title: " + title);
        try {
            File meetingFolder = getMeetingFolder(meetingDate);
            File transcriptFile = new File(meetingFolder, meetingId + "_transcript.txt");
            Log.d(TAG, "Saving transcript to: " + transcriptFile.getAbsolutePath());
            
            FileWriter writer = new FileWriter(transcriptFile);
            writer.write("Meeting Title: " + title + "\n");
            writer.write("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(meetingDate) + "\n");
            writer.write("Meeting ID: " + meetingId + "\n");
            writer.write("\n--- TRANSCRIPT ---\n\n");
            writer.write(transcript);
            writer.close();
            
            // Also save in central transcripts folder for easy access
            File centralTranscript = new File(rootDirectory, TRANSCRIPTS_FOLDER + "/" + meetingId + "_transcript.txt");
            FileWriter centralWriter = new FileWriter(centralTranscript);
            centralWriter.write(transcript);
            centralWriter.close();
            
            Log.d(TAG, "Transcript saved successfully");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save transcript", e);
            e.printStackTrace();
            return false;
        }
    }
    
    // Save summary
    public boolean saveSummary(String meetingId, String title, String summary, Date meetingDate) {
        try {
            File meetingFolder = getMeetingFolder(meetingDate);
            File summaryFile = new File(meetingFolder, meetingId + "_summary.md");
            
            FileWriter writer = new FileWriter(summaryFile);
            writer.write("# Meeting Summary\n\n");
            writer.write("**Title:** " + title + "\n\n");
            writer.write("**Date:** " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(meetingDate) + "\n\n");
            writer.write("**Meeting ID:** " + meetingId + "\n\n");
            writer.write("---\n\n");
            writer.write(summary);
            writer.close();
            
            // Also save in central summaries folder
            File centralSummary = new File(rootDirectory, SUMMARIES_FOLDER + "/" + meetingId + "_summary.md");
            FileWriter centralWriter = new FileWriter(centralSummary);
            centralWriter.write(summary);
            centralWriter.close();
            
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Save meeting metadata
    public boolean saveMeetingMetadata(String meetingId, String title, Date meetingDate, 
                                      String audioPath, String calendarEventId) {
        try {
            File meetingFolder = getMeetingFolder(meetingDate);
            File metadataFile = new File(meetingFolder, meetingId + "_metadata.json");
            
            JSONObject metadata = new JSONObject();
            metadata.put("meetingId", meetingId);
            metadata.put("title", title);
            metadata.put("date", meetingDate.getTime());
            metadata.put("audioPath", audioPath);
            metadata.put("calendarEventId", calendarEventId);
            metadata.put("createdAt", System.currentTimeMillis());
            
            FileWriter writer = new FileWriter(metadataFile);
            writer.write(metadata.toString(2));
            writer.close();
            
            return true;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Get all meetings for a specific date
    public List<MeetingInfo> getMeetingsForDate(Date date) {
        List<MeetingInfo> meetings = new ArrayList<>();
        File meetingFolder = getMeetingFolder(date);
        Log.d(TAG, "Getting meetings from folder: " + meetingFolder.getAbsolutePath());
        
        if (meetingFolder.exists()) {
            File[] metadataFiles = meetingFolder.listFiles((dir, name) -> name.endsWith("_metadata.json"));
            Log.d(TAG, "Found " + (metadataFiles != null ? metadataFiles.length : 0) + " metadata files");
            
            if (metadataFiles != null) {
                for (File metadataFile : metadataFiles) {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(metadataFile));
                        StringBuilder jsonString = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            jsonString.append(line);
                        }
                        reader.close();
                        
                        JSONObject metadata = new JSONObject(jsonString.toString());
                        MeetingInfo info = new MeetingInfo();
                        info.meetingId = metadata.getString("meetingId");
                        info.title = metadata.getString("title");
                        info.date = new Date(metadata.getLong("date"));
                        info.audioPath = metadata.optString("audioPath", null);
                        info.calendarEventId = metadata.optString("calendarEventId", null);
                        
                        meetings.add(info);
                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        return meetings;
    }
    
    // Search meetings by title
    public List<MeetingInfo> searchMeetingsByTitle(String query) {
        List<MeetingInfo> results = new ArrayList<>();
        File meetingsRoot = new File(rootDirectory, MEETINGS_FOLDER);
        searchRecursively(meetingsRoot, query.toLowerCase(), results);
        return results;
    }
    
    private void searchRecursively(File directory, String query, List<MeetingInfo> results) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    searchRecursively(file, query, results);
                } else if (file.getName().endsWith("_metadata.json")) {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        StringBuilder jsonString = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            jsonString.append(line);
                        }
                        reader.close();
                        
                        JSONObject metadata = new JSONObject(jsonString.toString());
                        String title = metadata.getString("title");
                        
                        if (title.toLowerCase().contains(query)) {
                            MeetingInfo info = new MeetingInfo();
                            info.meetingId = metadata.getString("meetingId");
                            info.title = title;
                            info.date = new Date(metadata.getLong("date"));
                            info.audioPath = metadata.optString("audioPath", null);
                            info.calendarEventId = metadata.optString("calendarEventId", null);
                            results.add(info);
                        }
                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    // Get transcript for a meeting
    public String getTranscript(String meetingId) {
        File transcriptFile = new File(rootDirectory, TRANSCRIPTS_FOLDER + "/" + meetingId + "_transcript.txt");
        
        if (transcriptFile.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(transcriptFile));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                return content.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    // Get summary for a meeting
    public String getSummary(String meetingId) {
        File summaryFile = new File(rootDirectory, SUMMARIES_FOLDER + "/" + meetingId + "_summary.md");
        
        if (summaryFile.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(summaryFile));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                return content.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    // Get all transcript files
    public List<File> getAllTranscriptFiles() {
        List<File> transcriptFiles = new ArrayList<>();
        File transcriptsFolder = new File(rootDirectory, TRANSCRIPTS_FOLDER);
        
        if (transcriptsFolder.exists()) {
            File[] files = transcriptsFolder.listFiles((dir, name) -> name.endsWith("_transcript.txt"));
            if (files != null) {
                for (File file : files) {
                    transcriptFiles.add(file);
                }
            }
        }
        
        // Also search in meeting folders for transcript files
        File meetingsFolder = new File(rootDirectory, MEETINGS_FOLDER);
        searchTranscriptFilesRecursively(meetingsFolder, transcriptFiles);
        
        return transcriptFiles;
    }
    
    private void searchTranscriptFilesRecursively(File directory, List<File> transcriptFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    searchTranscriptFilesRecursively(file, transcriptFiles);
                } else if (file.getName().endsWith("_transcript.txt")) {
                    // Check if we already have this file (to avoid duplicates)
                    boolean isDuplicate = false;
                    for (File existing : transcriptFiles) {
                        if (existing.getName().equals(file.getName())) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    if (!isDuplicate) {
                        transcriptFiles.add(file);
                    }
                }
            }
        }
    }
    
    // Helper class for meeting information
    public static class MeetingInfo {
        public String meetingId;
        public String title;
        public Date date;
        public String audioPath;
        public String calendarEventId;
    }
}