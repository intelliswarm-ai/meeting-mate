package ai.intelliswarm.meetingmate.analytics;

import android.content.Context;
import android.util.Log;
import ai.intelliswarm.meetingmate.data.MeetingFileManager;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import ai.intelliswarm.meetingmate.transcription.TranscriptionManager;
import ai.intelliswarm.meetingmate.transcription.TranscriptionProvider;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive validator for the recording-to-transcription flow
 * This class provides methods to validate each step and test the full integration
 */
public class FlowValidator {
    
    private static final String TAG = "FlowValidator";
    private final Context context;
    private final MeetingFileManager fileManager;
    private final SettingsManager settingsManager;
    private final TranscriptionManager transcriptionManager;
    
    public static class ValidationResult {
        public final boolean success;
        public final String message;
        public final String details;
        
        public ValidationResult(boolean success, String message, String details) {
            this.success = success;
            this.message = message;
            this.details = details;
        }
        
        public ValidationResult(boolean success, String message) {
            this(success, message, null);
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s%s", 
                success ? "PASS" : "FAIL", 
                message,
                details != null ? " - " + details : "");
        }
    }
    
    public FlowValidator(Context context) {
        this.context = context;
        this.fileManager = new MeetingFileManager(context);
        this.settingsManager = SettingsManager.getInstance(context);
        this.transcriptionManager = new TranscriptionManager(context);
    }
    
    /**
     * Validate that the system is properly configured for transcription
     */
    public ValidationResult validateSystemConfiguration() {
        try {
            // Check if OpenAI API key is configured
            boolean hasApiKey = settingsManager.hasOpenAIApiKey();
            if (!hasApiKey) {
                return new ValidationResult(false, "No OpenAI API key configured", 
                    "Set API key in Settings for Whisper transcription");
            }
            
            // Check selected provider
            TranscriptionProvider.ProviderType selectedProvider = settingsManager.getSelectedTranscriptionProvider();
            if (selectedProvider == null) {
                return new ValidationResult(false, "No transcription provider selected");
            }
            
            // Check provider configuration
            TranscriptionProvider provider = transcriptionManager.getCurrentProvider();
            if (provider == null) {
                return new ValidationResult(false, "Current transcription provider is null");
            }
            
            if (!provider.isConfigured()) {
                return new ValidationResult(false, "Selected provider not configured", 
                    provider.getConfigurationRequirement());
            }
            
            AppLogger.i(TAG, "System configuration validation passed");
            return new ValidationResult(true, "System properly configured", 
                "Provider: " + selectedProvider + ", API Key: configured");
            
        } catch (Exception e) {
            AppLogger.e(TAG, "System configuration validation failed", e);
            return new ValidationResult(false, "Validation error", e.getMessage());
        }
    }
    
    /**
     * Validate file system setup and permissions
     */
    public ValidationResult validateFileSystem() {
        try {
            String testMeetingId = "test_" + System.currentTimeMillis();
            String testContent = "Test transcript content";
            Date testDate = new Date();
            
            // Test transcript saving
            boolean transcriptSaved = fileManager.saveTranscript(testMeetingId, "Test Meeting", testContent, testDate);
            if (!transcriptSaved) {
                return new ValidationResult(false, "Failed to save test transcript");
            }
            
            // Test transcript reading
            String retrievedTranscript = fileManager.getTranscript(testMeetingId);
            if (retrievedTranscript == null || !retrievedTranscript.contains(testContent)) {
                return new ValidationResult(false, "Failed to retrieve saved transcript");
            }
            
            // Test metadata saving
            boolean metadataSaved = fileManager.saveMeetingMetadata(testMeetingId, "Test Meeting", testDate, null, null);
            if (!metadataSaved) {
                return new ValidationResult(false, "Failed to save metadata");
            }
            
            // Test file listing
            List<File> transcriptFiles = fileManager.getAllTranscriptFiles();
            if (transcriptFiles == null) {
                return new ValidationResult(false, "Failed to list transcript files");
            }
            
            AppLogger.i(TAG, "File system validation passed");
            return new ValidationResult(true, "File system working correctly", 
                "Transcripts: " + transcriptFiles.size() + " files");
            
        } catch (Exception e) {
            AppLogger.e(TAG, "File system validation failed", e);
            return new ValidationResult(false, "File system error", e.getMessage());
        }
    }
    
    /**
     * Test the actual Whisper API integration with a small audio file
     */
    public ValidationResult validateWhisperIntegration(File testAudioFile) {
        if (testAudioFile == null || !testAudioFile.exists()) {
            return new ValidationResult(false, "Test audio file not provided or doesn't exist");
        }
        
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();
            AtomicReference<Integer> lastProgress = new AtomicReference<>(0);
            
            long startTime = System.currentTimeMillis();
            
            transcriptionManager.transcribe(testAudioFile, new TranscriptionProvider.TranscriptionCallback() {
                @Override
                public void onSuccess(String transcript, String segments) {
                    result.set(transcript);
                    latch.countDown();
                }
                
                @Override
                public void onError(String errorMessage) {
                    error.set(errorMessage);
                    latch.countDown();
                }
                
                @Override
                public void onProgress(int progress) {
                    lastProgress.set(progress);
                    AppLogger.d(TAG, "Whisper API progress: " + progress + "%");
                }
            });
            
            // Wait for completion (max 60 seconds)
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;
            
            if (!completed) {
                transcriptionManager.cancelTranscription();
                return new ValidationResult(false, "Whisper API timeout", 
                    "Request took longer than 60 seconds. Last progress: " + lastProgress.get() + "%");
            }
            
            if (error.get() != null) {
                return new ValidationResult(false, "Whisper API error", error.get());
            }
            
            String transcript = result.get();
            if (transcript == null || transcript.trim().isEmpty()) {
                return new ValidationResult(false, "Empty transcript from Whisper API");
            }
            
            AppLogger.i(TAG, "Whisper integration validation passed");
            return new ValidationResult(true, "Whisper API working correctly", 
                String.format("Duration: %dms, Transcript: %d chars", duration, transcript.length()));
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Whisper integration validation failed", e);
            return new ValidationResult(false, "Whisper integration error", e.getMessage());
        }
    }
    
    /**
     * Create a minimal test audio file for validation
     */
    public File createTestAudioFile() {
        try {
            File testFile = new File(context.getCacheDir(), "validation_test.wav");
            
            // Create a minimal WAV file with silence
            byte[] wavHeader = new byte[] {
                0x52, 0x49, 0x46, 0x46, // "RIFF"
                0x2C, 0x00, 0x00, 0x00, // File size - 8 (44 bytes total)
                0x57, 0x41, 0x56, 0x45, // "WAVE"
                0x66, 0x6D, 0x74, 0x20, // "fmt "
                0x10, 0x00, 0x00, 0x00, // Chunk size (16)
                0x01, 0x00,             // PCM format
                0x01, 0x00,             // Mono
                0x40, 0x1F, 0x00, 0x00, // Sample rate (8000 Hz)
                (byte)0x80, 0x3E, 0x00, 0x00, // Byte rate
                0x02, 0x00,             // Block align
                0x10, 0x00,             // Bits per sample (16)
                0x64, 0x61, 0x74, 0x61, // "data"
                0x08, 0x00, 0x00, 0x00, // Data size (8 bytes)
                0x00, 0x00, 0x00, 0x00, // Silent samples
                0x00, 0x00, 0x00, 0x00
            };
            
            java.io.FileOutputStream fos = new java.io.FileOutputStream(testFile);
            fos.write(wavHeader);
            fos.close();
            
            return testFile;
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to create test audio file", e);
            return null;
        }
    }
    
    /**
     * Run complete validation of the entire flow
     */
    public ValidationResult[] validateCompleteFlow() {
        ValidationResult[] results = new ValidationResult[4];
        
        AppLogger.i(TAG, "Starting complete flow validation");
        
        // 1. System configuration
        results[0] = validateSystemConfiguration();
        
        // 2. File system
        results[1] = validateFileSystem();
        
        // 3. Create test audio
        File testAudio = createTestAudioFile();
        if (testAudio == null) {
            results[2] = new ValidationResult(false, "Failed to create test audio file");
            results[3] = new ValidationResult(false, "Skipped Whisper test - no audio file");
        } else {
            results[2] = new ValidationResult(true, "Test audio file created", testAudio.getName());
            
            // 4. Only test Whisper if system is configured
            if (results[0].success) {
                results[3] = validateWhisperIntegration(testAudio);
            } else {
                results[3] = new ValidationResult(false, "Skipped Whisper test - system not configured");
            }
            
            // Clean up test file
            testAudio.delete();
        }
        
        // Log summary
        int passed = 0;
        for (ValidationResult result : results) {
            if (result.success) passed++;
            AppLogger.i(TAG, "Validation: " + result.toString());
        }
        
        AppLogger.i(TAG, String.format("Complete flow validation finished: %d/%d tests passed", passed, results.length));
        
        return results;
    }
}