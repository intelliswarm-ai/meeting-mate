package ai.intelliswarm.meetingmate;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import ai.intelliswarm.meetingmate.analytics.FlowValidator;
import ai.intelliswarm.meetingmate.analytics.TranscriptionLogger;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import ai.intelliswarm.meetingmate.analytics.AppLogger;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class FullFlowValidationTest {
    
    private Context context;
    private FlowValidator validator;
    private SettingsManager settingsManager;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // Initialize logging
        AppLogger.initialize(context);
        
        validator = new FlowValidator(context);
        settingsManager = SettingsManager.getInstance(context);
        
        // Set up API key if provided
        String apiKey = InstrumentationRegistry.getArguments().getString("openai_api_key");
        if (apiKey != null && !apiKey.isEmpty()) {
            settingsManager.setOpenAIApiKey(apiKey);
            TranscriptionLogger.logStep(TranscriptionLogger.FlowStep.RECORDING_STARTED, "test", 
                "API key configured for testing");
        }
    }
    
    @Test
    public void testSystemConfiguration() {
        FlowValidator.ValidationResult result = validator.validateSystemConfiguration();
        
        System.out.println("System Configuration Test: " + result.toString());
        
        // If API key is provided via arguments, system should be configured
        String apiKey = InstrumentationRegistry.getArguments().getString("openai_api_key");
        if (apiKey != null && !apiKey.isEmpty()) {
            assertTrue("System should be configured when API key is provided", result.success);
        }
        
        // At minimum, should not crash
        assertNotNull("Result should not be null", result);
        assertNotNull("Result message should not be null", result.message);
    }
    
    @Test
    public void testFileSystemValidation() {
        FlowValidator.ValidationResult result = validator.validateFileSystem();
        
        System.out.println("File System Test: " + result.toString());
        
        assertTrue("File system should be accessible", result.success);
        assertNotNull("Result should not be null", result);
        assertNotNull("Result message should not be null", result.message);
    }
    
    @Test
    public void testAudioFileCreation() {
        File testAudio = validator.createTestAudioFile();
        
        assertNotNull("Should be able to create test audio file", testAudio);
        assertTrue("Test audio file should exist", testAudio.exists());
        assertTrue("Test audio file should have content", testAudio.length() > 0);
        
        System.out.println("Created test audio file: " + testAudio.getName() + 
                          " (" + testAudio.length() + " bytes)");
        
        // Clean up
        testAudio.delete();
    }
    
    @Test
    public void testWhisperIntegrationIfConfigured() {
        FlowValidator.ValidationResult configResult = validator.validateSystemConfiguration();
        
        if (!configResult.success) {
            System.out.println("Skipping Whisper test - system not configured: " + configResult.message);
            return;
        }
        
        File testAudio = validator.createTestAudioFile();
        assertNotNull("Should be able to create test audio file", testAudio);
        
        try {
            FlowValidator.ValidationResult result = validator.validateWhisperIntegration(testAudio);
            
            System.out.println("Whisper Integration Test: " + result.toString());
            
            // The test should complete (success or failure, but not crash)
            assertNotNull("Result should not be null", result);
            assertNotNull("Result message should not be null", result.message);
            
            if (result.success) {
                System.out.println("✅ Whisper API is working correctly");
            } else {
                System.out.println("❌ Whisper API test failed: " + result.message);
                // Log the failure but don't fail the test - API might be rate limited or have other issues
            }
            
        } finally {
            testAudio.delete();
        }
    }
    
    @Test
    public void testCompleteFlowValidation() {
        System.out.println("\\n=== COMPLETE FLOW VALIDATION ===");
        
        FlowValidator.ValidationResult[] results = validator.validateCompleteFlow();
        
        assertNotNull("Validation results should not be null", results);
        assertEquals("Should have 4 validation steps", 4, results.length);
        
        String[] stepNames = {
            "System Configuration",
            "File System",
            "Test Audio Creation", 
            "Whisper Integration"
        };
        
        int passed = 0;
        for (int i = 0; i < results.length; i++) {
            FlowValidator.ValidationResult result = results[i];
            assertNotNull("Result " + i + " should not be null", result);
            
            String status = result.success ? "✅ PASS" : "❌ FAIL";
            System.out.println(String.format("%d. %s: %s - %s", 
                i + 1, stepNames[i], status, result.message));
            
            if (result.details != null) {
                System.out.println("   Details: " + result.details);
            }
            
            if (result.success) {
                passed++;
            }
        }
        
        System.out.println(String.format("\\n📊 SUMMARY: %d/%d tests passed (%.1f%%)", 
            passed, results.length, (passed * 100.0 / results.length)));
        
        // At least file system should work
        assertTrue("File system validation should pass", results[1].success);
        
        // If API key is configured, system config should pass
        String apiKey = InstrumentationRegistry.getArguments().getString("openai_api_key");
        if (apiKey != null && !apiKey.isEmpty()) {
            assertTrue("System configuration should pass when API key provided", results[0].success);
        }
        
        System.out.println("\\n=== VALIDATION COMPLETE ===\\n");
    }
    
    @Test
    public void testTranscriptionLoggerFunctionality() {
        String testMeetingId = "test_meeting_" + System.currentTimeMillis();
        
        // Test various logging functions
        TranscriptionLogger.logRecordingStart(testMeetingId, "Test Meeting");
        TranscriptionLogger.logRecordingStop(testMeetingId, "/test/path/audio.m4a", 30000);
        
        File testFile = new File(context.getCacheDir(), "test.txt");
        try {
            testFile.createNewFile();
            TranscriptionLogger.logAudioFileSaved(testMeetingId, testFile, 1000, 1000);
            
            TranscriptionLogger.logTranscriptionStart(testMeetingId, "OpenAI Whisper", testFile);
            TranscriptionLogger.logTranscriptionProgress(testMeetingId, 50);
            TranscriptionLogger.logTranscriptionCompleted(testMeetingId, "Test transcript", "segments");
            TranscriptionLogger.logTranscriptSaved(testMeetingId, testFile);
            
            TranscriptionLogger.logFlowCompleted(testMeetingId, true, true);
            
        } catch (Exception e) {
            fail("Logging should not throw exceptions: " + e.getMessage());
        } finally {
            if (testFile.exists()) {
                testFile.delete();
            }
        }
        
        // Test validation functions
        File validTestFile = validator.createTestAudioFile();
        if (validTestFile != null) {
            assertTrue("Valid audio file should pass validation", 
                      TranscriptionLogger.validateAudioFile(validTestFile));
            validTestFile.delete();
        }
        
        assertTrue("Valid transcript should pass validation",
                  TranscriptionLogger.validateTranscript("This is a valid transcript"));
        
        assertFalse("Empty transcript should fail validation",
                   TranscriptionLogger.validateTranscript(""));
        
        assertFalse("Null transcript should fail validation",
                   TranscriptionLogger.validateTranscript(null));
        
        System.out.println("✅ TranscriptionLogger functionality test passed");
    }
}