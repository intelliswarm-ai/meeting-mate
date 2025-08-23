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
        // Create root directory in Documents folder
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        rootDirectory = new File(documentsDir, ROOT_FOLDER);
        
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs();
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
        File audioFolder = new File(rootDirectory, AUDIO_FOLDER);
        File destinationFile = new File(audioFolder, meetingId + ".m4a");
        
        // Copy or move the audio file to the destination
        if (audioFile.renameTo(destinationFile)) {
            return destinationFile;
        }
        return null;
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
    
    // Helper class for meeting information
    public static class MeetingInfo {
        public String meetingId;
        public String title;
        public Date date;
        public String audioPath;
        public String calendarEventId;
    }
}