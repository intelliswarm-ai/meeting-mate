package ai.intelliswarm.meetingmate.transcription;

import android.content.Context;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TranscriptionManager {
    
    private final Context context;
    private final SettingsManager settingsManager;
    private final Map<TranscriptionProvider.ProviderType, TranscriptionProvider> providers;
    
    public TranscriptionManager(Context context) {
        this.context = context;
        this.settingsManager = SettingsManager.getInstance(context);
        this.providers = new HashMap<>();
        
        initializeProviders();
    }
    
    private void initializeProviders() {
        providers.put(TranscriptionProvider.ProviderType.ANDROID_SPEECH, 
                     new AndroidSpeechProvider(context));
        providers.put(TranscriptionProvider.ProviderType.OPENAI_WHISPER, 
                     new OpenAIWhisperProvider(context));
        providers.put(TranscriptionProvider.ProviderType.LOCAL_WHISPER, 
                     new LocalWhisperProvider(context));
        // Add more providers here as needed
    }
    
    /**
     * Get the currently selected transcription provider
     */
    public TranscriptionProvider getCurrentProvider() {
        TranscriptionProvider.ProviderType selectedType = settingsManager.getSelectedTranscriptionProvider();
        return providers.get(selectedType);
    }
    
    /**
     * Get a specific provider by type
     */
    public TranscriptionProvider getProvider(TranscriptionProvider.ProviderType type) {
        return providers.get(type);
    }
    
    /**
     * Get all available providers
     */
    public TranscriptionProvider[] getAllProviders() {
        return providers.values().toArray(new TranscriptionProvider[0]);
    }
    
    /**
     * Get all configured providers (ready to use)
     */
    public TranscriptionProvider[] getConfiguredProviders() {
        return providers.values().stream()
            .filter(TranscriptionProvider::isConfigured)
            .toArray(TranscriptionProvider[]::new);
    }
    
    /**
     * Check if any provider is configured
     */
    public boolean hasConfiguredProvider() {
        return providers.values().stream().anyMatch(TranscriptionProvider::isConfigured);
    }
    
    /**
     * Transcribe using the current provider
     */
    public void transcribe(File audioFile, TranscriptionProvider.TranscriptionCallback callback) {
        TranscriptionProvider provider = getCurrentProvider();
        
        if (provider == null) {
            callback.onError("No transcription provider selected");
            return;
        }
        
        if (!provider.isConfigured()) {
            callback.onError("Selected provider is not configured. " + provider.getConfigurationRequirement());
            return;
        }
        
        // Check file format
        String fileName = audioFile.getName().toLowerCase();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        
        boolean formatSupported = false;
        for (String format : provider.getSupportedFormats()) {
            if (extension.equals(format)) {
                formatSupported = true;
                break;
            }
        }
        
        if (!formatSupported) {
            callback.onError("File format ." + extension + " not supported by " + provider.getType().getDisplayName());
            return;
        }
        
        // Check file size
        long fileSizeMB = audioFile.length() / (1024 * 1024);
        if (fileSizeMB > provider.getMaxFileSizeMB()) {
            callback.onError("File too large (" + fileSizeMB + "MB). Maximum size: " + provider.getMaxFileSizeMB() + "MB");
            return;
        }
        
        provider.transcribe(audioFile, callback);
    }
    
    /**
     * Cancel ongoing transcription
     */
    public void cancelTranscription() {
        TranscriptionProvider provider = getCurrentProvider();
        if (provider != null) {
            provider.cancel();
        }
    }
    
    /**
     * Get provider setup instructions
     */
    public String getProviderSetupInstructions(TranscriptionProvider.ProviderType type) {
        TranscriptionProvider provider = providers.get(type);
        if (provider == null) {
            return "Provider not available";
        }
        
        StringBuilder instructions = new StringBuilder();
        instructions.append(provider.getType().getDisplayName()).append("\n\n");
        instructions.append("Requirements: ").append(provider.getConfigurationRequirement()).append("\n\n");
        instructions.append("Supported formats: ");
        String[] formats = provider.getSupportedFormats();
        for (int i = 0; i < formats.length; i++) {
            instructions.append(".").append(formats[i]);
            if (i < formats.length - 1) instructions.append(", ");
        }
        instructions.append("\n");
        instructions.append("Max file size: ").append(provider.getMaxFileSizeMB()).append("MB");
        
        return instructions.toString();
    }
}