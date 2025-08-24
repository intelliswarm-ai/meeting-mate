package ai.intelliswarm.meetingmate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import ai.intelliswarm.meetingmate.transcription.TranscriptionManager;
import ai.intelliswarm.meetingmate.transcription.TranscriptionProvider;
import ai.intelliswarm.meetingmate.transcription.OpenAIWhisperProvider;
import ai.intelliswarm.meetingmate.utils.SettingsManager;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class WhisperUnitTest {
    
    private Context context;
    private TranscriptionManager transcriptionManager;
    private SettingsManager settingsManager;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        transcriptionManager = new TranscriptionManager(context);
        settingsManager = SettingsManager.getInstance(context);
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
            if (testFile != null && testFile.exists()) {
                testFile.delete();
            }
        }
    }
    
    @Test
    public void testTranscriptionWithInvalidFileFormat() {
        // Set a dummy API key
        settingsManager.setOpenAIApiKey("test-key-sk-1234567890");
        
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
            if (testFile != null && testFile.exists()) {
                testFile.delete();
            }
        }
    }
    
    @Test
    public void testTranscriptionManagerConfiguration() {
        // Test provider availability
        TranscriptionProvider[] providers = transcriptionManager.getAllProviders();
        assertTrue("Should have at least one provider", providers.length > 0);
        
        // Test current provider selection
        TranscriptionProvider currentProvider = transcriptionManager.getCurrentProvider();
        assertNotNull("Should have a current provider", currentProvider);
        assertEquals("Should be OpenAI Whisper provider", 
                    TranscriptionProvider.ProviderType.OPENAI_WHISPER, 
                    currentProvider.getType());
    }
    
    @Test
    public void testTranscriptionManagerSetupInstructions() {
        String instructions = transcriptionManager.getProviderSetupInstructions(
            TranscriptionProvider.ProviderType.OPENAI_WHISPER);
        
        assertFalse("Instructions should not be empty", instructions.isEmpty());
        assertTrue("Instructions should mention OpenAI", instructions.contains("OpenAI"));
        assertTrue("Instructions should mention API key", instructions.contains("API"));
        assertTrue("Instructions should mention supported formats", instructions.contains("formats"));
        assertTrue("Instructions should mention file size limit", instructions.contains("25MB"));
    }
    
    @Test 
    public void testApiKeyValidation() {
        OpenAIWhisperProvider provider = new OpenAIWhisperProvider(context);
        
        // Test without API key
        settingsManager.setOpenAIApiKey("");
        assertFalse("Should not be configured without API key", provider.isConfigured());
        
        // Test with API key
        settingsManager.setOpenAIApiKey("sk-test1234567890");
        assertTrue("Should be configured with API key", provider.isConfigured());
    }
    
    private File createDummyAudioFile() {
        try {
            File file = new File(context.getCacheDir(), "test_audio.m4a");
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (Exception e) {
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