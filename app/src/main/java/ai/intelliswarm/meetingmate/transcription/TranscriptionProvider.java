package ai.intelliswarm.meetingmate.transcription;

import java.io.File;

public interface TranscriptionProvider {
    
    enum ProviderType {
        ANDROID_SPEECH("Android Speech Recognition (Default)", false),
        OPENAI_WHISPER("OpenAI Whisper", true),
        LOCAL_WHISPER("Local Whisper (Offline)", false),
        GOOGLE_SPEECH("Google Speech-to-Text", true),
        AZURE_SPEECH("Azure Speech Services", true);
        
        private final String displayName;
        private final boolean requiresApiKey;
        
        ProviderType(String displayName, boolean requiresApiKey) {
            this.displayName = displayName;
            this.requiresApiKey = requiresApiKey;
        }
        
        public String getDisplayName() { return displayName; }
        public boolean requiresApiKey() { return requiresApiKey; }
    }
    
    interface TranscriptionCallback {
        void onSuccess(String transcript, String segments);
        void onProgress(int progressPercent);
        void onError(String error);
        default void onPartialResult(String partialTranscript) {
            // Optional callback for live transcription
        }
    }
    
    /**
     * Get the provider type
     */
    ProviderType getType();
    
    /**
     * Check if provider is properly configured
     */
    boolean isConfigured();
    
    /**
     * Get configuration requirements (API key, model file, etc.)
     */
    String getConfigurationRequirement();
    
    /**
     * Transcribe audio file
     */
    void transcribe(File audioFile, TranscriptionCallback callback);
    
    /**
     * Get supported audio formats
     */
    String[] getSupportedFormats();
    
    /**
     * Get maximum file size in MB
     */
    int getMaxFileSizeMB();
    
    /**
     * Cancel ongoing transcription
     */
    void cancel();
}