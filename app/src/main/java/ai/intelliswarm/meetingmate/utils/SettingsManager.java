package ai.intelliswarm.meetingmate.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String PREFS_NAME = "meeting_mate_secure_prefs";
    private static final String KEY_OPENAI_API = "openai_api_key";
    private static final String KEY_DEFAULT_CALENDAR_ID = "default_calendar_id";
    private static final String KEY_AUTO_TRANSCRIBE = "auto_transcribe";
    private static final String KEY_AUTO_SUMMARIZE = "auto_summarize";
    private static final String KEY_AUDIO_QUALITY = "audio_quality";
    private static final String KEY_TRANSCRIPT_LANGUAGE = "transcript_language";
    private static final String KEY_TRANSCRIPTION_PROVIDER = "transcription_provider";
    
    private SharedPreferences sharedPreferences;
    private static SettingsManager instance;
    
    private SettingsManager(Context context) {
        try {
            // Create or get the master key for encryption
            MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
            
            // Create encrypted shared preferences
            sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Fallback to regular SharedPreferences if encryption fails
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
    
    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // OpenAI API Key
    public void setOpenAIApiKey(String apiKey) {
        Log.d(TAG, "Saving OpenAI API key: " + (apiKey != null && !apiKey.isEmpty() ? 
            apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "EMPTY"));
        sharedPreferences.edit().putString(KEY_OPENAI_API, apiKey).apply();
        Log.d(TAG, "OpenAI API key saved successfully");
    }
    
    public String getOpenAIApiKey() {
        String key = sharedPreferences.getString(KEY_OPENAI_API, "");
        Log.d(TAG, "Retrieved OpenAI API key: " + (key != null && !key.isEmpty() ? 
            key.substring(0, Math.min(8, key.length())) + "..." : "EMPTY"));
        return key;
    }
    
    public boolean hasOpenAIApiKey() {
        String key = getOpenAIApiKey();
        boolean hasKey = key != null && !key.isEmpty();
        Log.d(TAG, "hasOpenAIApiKey: " + hasKey);
        return hasKey;
    }
    
    // Default Calendar ID
    public void setDefaultCalendarId(long calendarId) {
        sharedPreferences.edit().putLong(KEY_DEFAULT_CALENDAR_ID, calendarId).apply();
    }
    
    public long getDefaultCalendarId() {
        return sharedPreferences.getLong(KEY_DEFAULT_CALENDAR_ID, -1);
    }
    
    // Auto Transcribe Setting
    public void setAutoTranscribe(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_TRANSCRIBE, enabled).apply();
    }
    
    public boolean isAutoTranscribeEnabled() {
        return sharedPreferences.getBoolean(KEY_AUTO_TRANSCRIBE, true);
    }
    
    // Auto Summarize Setting
    public void setAutoSummarize(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_SUMMARIZE, enabled).apply();
    }
    
    public boolean isAutoSummarizeEnabled() {
        return sharedPreferences.getBoolean(KEY_AUTO_SUMMARIZE, true);
    }
    
    // Audio Quality Setting
    public void setAudioQuality(AudioQuality quality) {
        sharedPreferences.edit().putString(KEY_AUDIO_QUALITY, quality.name()).apply();
    }
    
    public AudioQuality getAudioQuality() {
        String quality = sharedPreferences.getString(KEY_AUDIO_QUALITY, AudioQuality.HIGH.name());
        try {
            return AudioQuality.valueOf(quality);
        } catch (IllegalArgumentException e) {
            return AudioQuality.HIGH;
        }
    }
    
    // Transcript Language Setting
    public void setTranscriptLanguage(String languageCode) {
        sharedPreferences.edit().putString(KEY_TRANSCRIPT_LANGUAGE, languageCode).apply();
    }
    
    public String getTranscriptLanguage() {
        return sharedPreferences.getString(KEY_TRANSCRIPT_LANGUAGE, "en");
    }
    
    // Transcription Provider Setting
    public void setSelectedTranscriptionProvider(ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType type) {
        sharedPreferences.edit().putString(KEY_TRANSCRIPTION_PROVIDER, type.name()).apply();
    }
    
    public ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType getSelectedTranscriptionProvider() {
        // Always default to OpenAI Whisper as it's the only reliable provider
        String providerName = sharedPreferences.getString(KEY_TRANSCRIPTION_PROVIDER, 
            ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.OPENAI_WHISPER.name());
        try {
            ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType type = 
                ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.valueOf(providerName);
            // Force OpenAI Whisper if other providers are selected (they're not working)
            if (type != ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.OPENAI_WHISPER) {
                return ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.OPENAI_WHISPER;
            }
            return type;
        } catch (IllegalArgumentException e) {
            return ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.OPENAI_WHISPER;
        }
    }
    
    // Clear all settings
    public void clearAll() {
        sharedPreferences.edit().clear().apply();
    }
    
    // Audio quality enum
    public enum AudioQuality {
        LOW(64000, 22050),      // 64 kbps, 22.05 kHz
        MEDIUM(96000, 32000),   // 96 kbps, 32 kHz
        HIGH(128000, 44100),    // 128 kbps, 44.1 kHz
        VERY_HIGH(256000, 48000); // 256 kbps, 48 kHz
        
        private final int bitRate;
        private final int sampleRate;
        
        AudioQuality(int bitRate, int sampleRate) {
            this.bitRate = bitRate;
            this.sampleRate = sampleRate;
        }
        
        public int getBitRate() {
            return bitRate;
        }
        
        public int getSampleRate() {
            return sampleRate;
        }
    }
}