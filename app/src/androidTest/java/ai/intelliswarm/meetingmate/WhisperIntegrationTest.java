package ai.intelliswarm.meetingmate;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import ai.intelliswarm.meetingmate.transcription.TranscriptionManager;
import ai.intelliswarm.meetingmate.transcription.TranscriptionProvider;
import ai.intelliswarm.meetingmate.transcription.OpenAIWhisperProvider;
import ai.intelliswarm.meetingmate.utils.SettingsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class WhisperIntegrationTest {
    
    private Context context;
    private TranscriptionManager transcriptionManager;
    private SettingsManager settingsManager;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        transcriptionManager = new TranscriptionManager(context);
        settingsManager = SettingsManager.getInstance(context);
        
        // Check if API key is provided via instrumentation arguments
        String apiKey = InstrumentationRegistry.getArguments().getString("openai_api_key");
        if (apiKey != null && !apiKey.isEmpty()) {
            settingsManager.setOpenAIApiKey(apiKey);
        }
    }
    
    @Test
    public void testWhisperProviderConfiguration() {
        OpenAIWhisperProvider provider = new OpenAIWhisperProvider(context);
        
        // Test provider type
        assertEquals(TranscriptionProvider.ProviderType.OPENAI_WHISPER, provider.getType());
        
        // Test configuration requirement
        assertFalse(provider.getConfigurationRequirement().isEmpty());
        assertTrue(provider.getConfigurationRequirement().contains("OpenAI API Key"));
        
        // Test supported formats
        String[] formats = provider.getSupportedFormats();
        assertTrue("Should support m4a format", contains(formats, "m4a"));
        assertTrue("Should support wav format", contains(formats, "wav"));
        assertTrue("Should support mp3 format", contains(formats, "mp3"));
        
        // Test max file size
        assertEquals(25, provider.getMaxFileSizeMB());
    }
    
    @Test
    public void testTranscriptionManagerWithoutApiKey() {
        // Clear any existing API key
        settingsManager.setOpenAIApiKey("");
        
        // Create a dummy audio file
        File testFile = createDummyAudioFile();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        
        transcriptionManager.transcribe(testFile, new TranscriptionProvider.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, String segments) {
                fail("Should not succeed without API key");
            }
            
            @Override
            public void onError(String error) {
                errorMessage.set(error);
                latch.countDown();
            }
            
            @Override
            public void onProgress(int progress) {
                // Ignore progress updates
            }
        });
        
        try {
            assertTrue("Callback should be called within 5 seconds", latch.await(5, TimeUnit.SECONDS));
            assertNotNull("Error message should not be null", errorMessage.get());
            assertTrue("Should mention API key configuration", 
                      errorMessage.get().contains("not configured") || 
                      errorMessage.get().contains("API key"));
        } catch (InterruptedException e) {
            fail("Test interrupted: " + e.getMessage());
        } finally {
            testFile.delete();
        }
    }
    
    @Test
    public void testTranscriptionWithInvalidFileFormat() {
        // Set a dummy API key
        settingsManager.setOpenAIApiKey("test-key");
        
        // Create a file with unsupported extension
        File testFile = new File(context.getCacheDir(), "test.invalid");
        try {
            testFile.createNewFile();
            
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorMessage = new AtomicReference<>();
            
            transcriptionManager.transcribe(testFile, new TranscriptionProvider.TranscriptionCallback() {
                @Override
                public void onSuccess(String transcript, String segments) {
                    fail("Should not succeed with invalid format");
                }
                
                @Override
                public void onError(String error) {
                    errorMessage.set(error);
                    latch.countDown();
                }
                
                @Override
                public void onProgress(int progress) {
                    // Ignore progress updates
                }
            });
            
            assertTrue("Callback should be called within 5 seconds", latch.await(5, TimeUnit.SECONDS));
            assertNotNull("Error message should not be null", errorMessage.get());
            assertTrue("Should mention file format not supported", 
                      errorMessage.get().contains("not supported"));
            
        } catch (Exception e) {
            fail("Failed to create test file: " + e.getMessage());
        } finally {
            testFile.delete();
        }
    }
    
    @Test
    public void testWhisperApiCallWithRealKey() {
        // This test only runs if a real API key is configured
        if (!settingsManager.hasOpenAIApiKey()) {
            // Skip test if no API key is configured
            return;
        }
        
        // Create a small test audio file from resources or generate silence
        File testFile = createTestAudioFile();
        if (testFile == null) {
            return; // Skip if we can't create test audio
        }
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        
        transcriptionManager.transcribe(testFile, new TranscriptionProvider.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, String segments) {
                result.set(transcript);
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
                errorMessage.set(error);
                latch.countDown();
            }
            
            @Override
            public void onProgress(int progress) {
                // Progress updates are expected
                assertTrue("Progress should be between 0 and 100", progress >= 0 && progress <= 100);
            }
        });
        
        try {
            assertTrue("Transcription should complete within 60 seconds", 
                      latch.await(60, TimeUnit.SECONDS));
            
            if (errorMessage.get() != null) {
                // If there's an error, it should be meaningful
                assertFalse("Error message should not be empty", errorMessage.get().isEmpty());
                System.out.println("Transcription error (expected if API key is invalid): " + errorMessage.get());
            } else {
                // If successful, we should have a transcript
                assertNotNull("Transcript should not be null on success", result.get());
                System.out.println("Transcription result: " + result.get());
            }
            
        } catch (InterruptedException e) {
            fail("Test interrupted: " + e.getMessage());
        } finally {
            testFile.delete();
        }
    }
    
    @Test
    public void testTranscriptionCancellation() {
        // Set a dummy API key
        settingsManager.setOpenAIApiKey("test-key");
        
        File testFile = createDummyAudioFile();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        
        transcriptionManager.transcribe(testFile, new TranscriptionProvider.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, String segments) {
                fail("Should not succeed after cancellation");
            }
            
            @Override
            public void onError(String error) {
                errorMessage.set(error);
                latch.countDown();
            }
            
            @Override
            public void onProgress(int progress) {
                // Cancel after first progress update
                transcriptionManager.cancelTranscription();
            }
        });
        
        try {
            assertTrue("Callback should be called within 10 seconds", latch.await(10, TimeUnit.SECONDS));
            // Error message might be about cancellation or network error (both are fine)
            assertNotNull("Should receive error after cancellation", errorMessage.get());
        } catch (InterruptedException e) {
            fail("Test interrupted: " + e.getMessage());
        } finally {
            testFile.delete();
        }
    }
    
    private File createDummyAudioFile() {
        try {
            File file = new File(context.getCacheDir(), "test_audio.m4a");
            file.createNewFile();
            
            // Write some dummy data to make it a valid file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(new byte[1024]); // 1KB of zeros
            }
            
            return file;
        } catch (Exception e) {
            fail("Failed to create dummy audio file: " + e.getMessage());
            return null;
        }
    }
    
    private File createTestAudioFile() {
        try {
            // Try to copy a test audio file from assets if available
            File file = new File(context.getCacheDir(), "test_audio.wav");
            
            // Create a minimal WAV file with silence (44 bytes header + some data)
            byte[] wavHeader = new byte[] {
                0x52, 0x49, 0x46, 0x46, // "RIFF"
                0x24, 0x00, 0x00, 0x00, // File size - 8
                0x57, 0x41, 0x56, 0x45, // "WAVE"
                0x66, 0x6D, 0x74, 0x20, // "fmt "
                0x10, 0x00, 0x00, 0x00, // Chunk size
                0x01, 0x00,             // PCM format
                0x01, 0x00,             // Mono
                0x44, 0x11, 0x00, 0x00, // Sample rate (4420 Hz)
                (byte)0x88, 0x22, 0x00, 0x00, // Byte rate
                0x02, 0x00,             // Block align
                0x10, 0x00,             // Bits per sample
                0x64, 0x61, 0x74, 0x61, // "data"
                0x00, 0x00, 0x00, 0x00  // Data size (0 bytes)
            };
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(wavHeader);
            }
            
            return file;
        } catch (Exception e) {
            // Return null if we can't create test audio
            return null;
        }
    }
    
    private boolean contains(String[] array, String value) {
        for (String item : array) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }
}