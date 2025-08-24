package ai.intelliswarm.meetingmate.analytics;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TranscriptionLogger {
    
    private static final String TAG = "TranscriptionFlow";
    private static final SimpleDateFormat TIMESTAMP_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    
    // Step identifiers for tracking
    public enum FlowStep {
        RECORDING_STARTED("Recording Started"),
        RECORDING_STOPPED("Recording Stopped"), 
        AUDIO_FILE_SAVED("Audio File Saved"),
        TRANSCRIPTION_STARTED("Transcription Started"),
        TRANSCRIPTION_PROGRESS("Transcription Progress"),
        TRANSCRIPTION_COMPLETED("Transcription Completed"),
        TRANSCRIPTION_FAILED("Transcription Failed"),
        TRANSCRIPT_SAVED("Transcript Saved"),
        SUMMARY_STARTED("Summary Generation Started"),
        SUMMARY_COMPLETED("Summary Completed"),
        SUMMARY_FAILED("Summary Failed"),
        FLOW_COMPLETED("Full Flow Completed"),
        FLOW_FAILED("Flow Failed");
        
        private final String displayName;
        
        FlowStep(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public static void logStep(FlowStep step, String meetingId, String details) {
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        String logMessage = String.format("[%s] %s - Meeting: %s%s", 
            timestamp, step.getDisplayName(), meetingId,
            details != null && !details.isEmpty() ? " - " + details : "");
        
        Log.i(TAG, logMessage);
        AppLogger.i(TAG, logMessage);
    }
    
    public static void logStep(FlowStep step, String meetingId) {
        logStep(step, meetingId, null);
    }
    
    public static void logRecordingStart(String meetingId, String meetingTitle) {
        logStep(FlowStep.RECORDING_STARTED, meetingId, 
            "Title: '" + meetingTitle + "'");
    }
    
    public static void logRecordingStop(String meetingId, String audioFilePath, long duration) {
        logStep(FlowStep.RECORDING_STOPPED, meetingId, 
            String.format("Duration: %d ms, Path: %s", duration, audioFilePath));
    }
    
    public static void logAudioFileSaved(String meetingId, File audioFile, long originalSize, long savedSize) {
        String details = String.format("Original: %d bytes, Saved: %d bytes, Path: %s", 
            originalSize, savedSize, audioFile.getAbsolutePath());
        logStep(FlowStep.AUDIO_FILE_SAVED, meetingId, details);
    }
    
    public static void logTranscriptionStart(String meetingId, String provider, File audioFile) {
        String details = String.format("Provider: %s, File: %s (%d bytes)", 
            provider, audioFile.getName(), audioFile.length());
        logStep(FlowStep.TRANSCRIPTION_STARTED, meetingId, details);
    }
    
    public static void logTranscriptionProgress(String meetingId, int progress) {
        logStep(FlowStep.TRANSCRIPTION_PROGRESS, meetingId, progress + "%");
    }
    
    public static void logTranscriptionCompleted(String meetingId, String transcript, String segments) {
        String details = String.format("Transcript length: %d chars, Has segments: %s", 
            transcript.length(), segments != null && !segments.isEmpty() ? "Yes" : "No");
        logStep(FlowStep.TRANSCRIPTION_COMPLETED, meetingId, details);
    }
    
    public static void logTranscriptionFailed(String meetingId, String error) {
        logStep(FlowStep.TRANSCRIPTION_FAILED, meetingId, "Error: " + error);
        AppLogger.e(TAG, "Transcription failed for meeting " + meetingId + ": " + error);
    }
    
    public static void logTranscriptSaved(String meetingId, File transcriptFile) {
        String details = String.format("File: %s (%d bytes)", 
            transcriptFile.getAbsolutePath(), transcriptFile.length());
        logStep(FlowStep.TRANSCRIPT_SAVED, meetingId, details);
    }
    
    public static void logSummaryStart(String meetingId) {
        logStep(FlowStep.SUMMARY_STARTED, meetingId);
    }
    
    public static void logSummaryCompleted(String meetingId, String summary) {
        logStep(FlowStep.SUMMARY_COMPLETED, meetingId, 
            "Summary length: " + summary.length() + " chars");
    }
    
    public static void logSummaryFailed(String meetingId, String error) {
        logStep(FlowStep.SUMMARY_FAILED, meetingId, "Error: " + error);
    }
    
    public static void logFlowCompleted(String meetingId, boolean hasTranscript, boolean hasSummary) {
        String details = String.format("Transcript: %s, Summary: %s", 
            hasTranscript ? "Yes" : "No", hasSummary ? "Yes" : "No");
        logStep(FlowStep.FLOW_COMPLETED, meetingId, details);
    }
    
    public static void logFlowFailed(String meetingId, String reason) {
        logStep(FlowStep.FLOW_FAILED, meetingId, "Reason: " + reason);
        AppLogger.e(TAG, "Full flow failed for meeting " + meetingId + ": " + reason);
    }
    
    // Validation helpers
    public static boolean validateAudioFile(File audioFile) {
        if (audioFile == null) {
            AppLogger.e(TAG, "Audio file is null");
            return false;
        }
        
        if (!audioFile.exists()) {
            AppLogger.e(TAG, "Audio file does not exist: " + audioFile.getAbsolutePath());
            return false;
        }
        
        if (audioFile.length() == 0) {
            AppLogger.e(TAG, "Audio file is empty: " + audioFile.getAbsolutePath());
            return false;
        }
        
        // Check file size (should be reasonable for audio)
        long sizeKB = audioFile.length() / 1024;
        if (sizeKB < 1) {
            AppLogger.w(TAG, "Audio file suspiciously small: " + sizeKB + " KB");
        }
        
        if (sizeKB > 100000) { // 100MB
            AppLogger.w(TAG, "Audio file very large: " + sizeKB + " KB");
        }
        
        AppLogger.d(TAG, "Audio file validation passed: " + audioFile.getName() + " (" + sizeKB + " KB)");
        return true;
    }
    
    public static boolean validateTranscript(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            AppLogger.e(TAG, "Transcript is null or empty");
            return false;
        }
        
        if (transcript.length() < 5) {
            AppLogger.w(TAG, "Transcript suspiciously short: " + transcript.length() + " chars");
        }
        
        AppLogger.d(TAG, "Transcript validation passed: " + transcript.length() + " characters");
        return true;
    }
    
    public static void logApiKeyStatus(Context context) {
        try {
            ai.intelliswarm.meetingmate.utils.SettingsManager settingsManager = 
                ai.intelliswarm.meetingmate.utils.SettingsManager.getInstance(context);
            
            boolean hasApiKey = settingsManager.hasOpenAIApiKey();
            String provider = settingsManager.getSelectedTranscriptionProvider().toString();
            
            AppLogger.i(TAG, "API Key Status - Has OpenAI Key: " + hasApiKey + ", Selected Provider: " + provider);
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to check API key status", e);
        }
    }
}