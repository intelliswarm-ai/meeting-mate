package ai.intelliswarm.meetingmate.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import android.os.Environment;
import org.json.JSONObject;
import org.json.JSONException;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String PREFS_NAME = "meeting_mate_secure_prefs";
    private static final String BACKUP_PREFS_NAME = "meeting_mate_backup_prefs";
    private static final int SETTINGS_VERSION = 1;
    
    // External storage for persistent settings
    private static final String EXTERNAL_SETTINGS_DIR = "MeetingMate";
    private static final String EXTERNAL_SETTINGS_FILE = "app_settings.json";
    private static final String EXTERNAL_BACKUP_FILE = "app_settings_backup.json";
    private static final String KEY_OPENAI_API = "openai_api_key";
    private static final String KEY_ASSEMBLYAI_API = "assemblyai_api_key";
    private static final String KEY_DEFAULT_CALENDAR_ID = "default_calendar_id";
    private static final String KEY_AUTO_TRANSCRIBE = "auto_transcribe";
    private static final String KEY_AUTO_SUMMARIZE = "auto_summarize";
    private static final String KEY_AUDIO_QUALITY = "audio_quality";
    private static final String KEY_TRANSCRIPT_LANGUAGE = "transcript_language";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_TRANSCRIPTION_PROVIDER = "transcription_provider";
    private static final String KEY_SETTINGS_VERSION = "settings_version";
    
    private SharedPreferences sharedPreferences;
    private static SettingsManager instance;
    private Context appContext;
    
    private SettingsManager(Context context) {
        this.appContext = context.getApplicationContext();
        
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
            
            Log.d(TAG, "EncryptedSharedPreferences initialized successfully");
            
        } catch (GeneralSecurityException | IOException e) {
            Log.w(TAG, "Failed to initialize EncryptedSharedPreferences, using fallback", e);
            // Fallback to regular SharedPreferences if encryption fails
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        
        // Load settings from external storage (survives uninstall/reinstall)
        loadFromExternalStorage(context);
        
        // Perform settings migration/verification on first run after update
        verifyAndMigrateSettings(context);
        
        // Create backup of critical settings in external storage
        createSettingsBackup(context);
        saveToExternalStorage(context);
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
        
        // Also save to external storage immediately to survive uninstalls
        saveToExternalStorageAsync();
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
    
    // AssemblyAI API Key
    public void setAssemblyAIApiKey(String apiKey) {
        Log.d(TAG, "Saving AssemblyAI API key: " + (apiKey != null && !apiKey.isEmpty() ? 
            apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "EMPTY"));
        sharedPreferences.edit().putString(KEY_ASSEMBLYAI_API, apiKey).apply();
        Log.d(TAG, "AssemblyAI API key saved successfully");
        
        // Also save to external storage immediately to survive uninstalls
        saveToExternalStorageAsync();
    }
    
    public String getAssemblyAIApiKey() {
        String key = sharedPreferences.getString(KEY_ASSEMBLYAI_API, "");
        Log.d(TAG, "Retrieved AssemblyAI API key: " + (key != null && !key.isEmpty() ? 
            key.substring(0, Math.min(8, key.length())) + "..." : "EMPTY"));
        return key;
    }
    
    public boolean hasAssemblyAIApiKey() {
        String key = getAssemblyAIApiKey();
        boolean hasKey = key != null && !key.isEmpty();
        Log.d(TAG, "hasAssemblyAIApiKey: " + hasKey);
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
        return sharedPreferences.getString(KEY_TRANSCRIPT_LANGUAGE, "auto");
    }
    
    // App UI Language Setting
    public void setAppLanguage(String languageCode) {
        sharedPreferences.edit().putString(KEY_APP_LANGUAGE, languageCode).apply();
    }
    
    public String getAppLanguage() {
        return sharedPreferences.getString(KEY_APP_LANGUAGE, "system");
    }
    
    // Apply language to app context
    public static Context applyLanguage(Context context) {
        try {
            SettingsManager settingsManager = getInstance(context);
            String languageCode = settingsManager.getAppLanguage();
            
            Log.d(TAG, "applyLanguage called with language: " + languageCode);
            
            if ("system".equals(languageCode) || languageCode == null || languageCode.isEmpty()) {
                Log.d(TAG, "Using system default language");
                return context;
            }
            
            Locale locale = new Locale(languageCode);
            Locale.setDefault(locale);
            
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);
            
            Context newContext = context.createConfigurationContext(config);
            Log.d(TAG, "Language applied successfully: " + languageCode);
            return newContext;
            
        } catch (Exception e) {
            // If called from attachBaseContext and context isn't ready, return original context
            Log.d(TAG, "Unable to apply language in attachBaseContext, using original context: " + e.getMessage());
            return context;
        }
    }
    
    // Apply language change immediately to activity
    public static void applyLanguageToActivity(android.app.Activity activity, String languageCode) {
        Log.d(TAG, "applyLanguageToActivity called with language: " + languageCode);
        
        // Save the language first
        SettingsManager settingsManager = getInstance(activity);
        settingsManager.setAppLanguage(languageCode);
        
        // Apply the locale change immediately
        if (!"system".equals(languageCode)) {
            Locale locale = new Locale(languageCode);
            Locale.setDefault(locale);
            
            Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.setLocale(locale);
            
            activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
            Log.d(TAG, "Configuration updated for language: " + languageCode);
        } else {
            Log.d(TAG, "Reverting to system language");
        }
        
        // Recreate the activity to refresh all UI
        Log.d(TAG, "Recreating activity to apply language changes");
        activity.recreate();
    }
    
    // Transcription Provider Setting
    public void setSelectedTranscriptionProvider(ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType type) {
        sharedPreferences.edit().putString(KEY_TRANSCRIPTION_PROVIDER, type.name()).apply();
    }
    
    public ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType getSelectedTranscriptionProvider() {
        // Default to AssemblyAI for better speaker detection, fallback to OpenAI Whisper
        String providerName = sharedPreferences.getString(KEY_TRANSCRIPTION_PROVIDER, 
            ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.ASSEMBLYAI_SPEAKER.name());
        try {
            ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType type = 
                ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.valueOf(providerName);
            
            // Only allow working providers (OpenAI Whisper and AssemblyAI)
            if (type == ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.OPENAI_WHISPER ||
                type == ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.ASSEMBLYAI_SPEAKER) {
                return type;
            } else {
                // Default to AssemblyAI for speaker detection
                return ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.ASSEMBLYAI_SPEAKER;
            }
        } catch (IllegalArgumentException e) {
            return ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.ASSEMBLYAI_SPEAKER;
        }
    }
    
    // Clear all settings
    public void clearAll() {
        sharedPreferences.edit().clear().apply();
    }
    
    /**
     * Verify settings integrity and migrate from older versions if needed
     */
    private void verifyAndMigrateSettings(Context context) {
        int currentVersion = sharedPreferences.getInt(KEY_SETTINGS_VERSION, 0);
        
        if (currentVersion < SETTINGS_VERSION) {
            Log.i(TAG, "Performing settings migration from version " + currentVersion + " to " + SETTINGS_VERSION);
            
            // Try to restore from backup if main settings are corrupted
            if (currentVersion == 0 && !hasAnySettings()) {
                Log.w(TAG, "No settings found, attempting to restore from backup");
                restoreFromBackup(context);
            }
            
            // Update version
            sharedPreferences.edit().putInt(KEY_SETTINGS_VERSION, SETTINGS_VERSION).apply();
            Log.i(TAG, "Settings migration completed");
        }
    }
    
    /**
     * Backup a single critical setting immediately
     */
    private void backupCriticalSetting(String key, String value) {
        try {
            Context context = null; // We need context - let's get from getInstance
            if (instance != null) {
                // We can't easily get context here, so we'll rely on the full backup during init
                Log.d(TAG, "Individual backup requested for " + key + " - will be included in next full backup");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to backup critical setting: " + key, e);
        }
    }
    
    /**
     * Create backup of critical settings (API keys, language preferences)
     */
    private void createSettingsBackup(Context context) {
        try {
            SharedPreferences backup = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor backupEditor = backup.edit();
            
            // Backup critical settings that user doesn't want to lose
            String openAIKey = getOpenAIApiKey();
            if (!openAIKey.isEmpty()) {
                backupEditor.putString(KEY_OPENAI_API, openAIKey);
            }
            
            String assemblyAIKey = getAssemblyAIApiKey();
            if (!assemblyAIKey.isEmpty()) {
                backupEditor.putString(KEY_ASSEMBLYAI_API, assemblyAIKey);
            }
            
            backupEditor.putString(KEY_APP_LANGUAGE, getAppLanguage());
            backupEditor.putString(KEY_TRANSCRIPT_LANGUAGE, getTranscriptLanguage());
            backupEditor.putString(KEY_TRANSCRIPTION_PROVIDER, getSelectedTranscriptionProvider().name());
            backupEditor.putBoolean(KEY_AUTO_TRANSCRIBE, isAutoTranscribeEnabled());
            backupEditor.putBoolean(KEY_AUTO_SUMMARIZE, isAutoSummarizeEnabled());
            backupEditor.putString(KEY_AUDIO_QUALITY, getAudioQuality().name());
            backupEditor.putLong(KEY_DEFAULT_CALENDAR_ID, getDefaultCalendarId());
            backupEditor.putInt(KEY_SETTINGS_VERSION, SETTINGS_VERSION);
            
            backupEditor.apply();
            Log.d(TAG, "Settings backup created successfully");
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to create settings backup", e);
        }
    }
    
    /**
     * Restore settings from backup
     */
    private void restoreFromBackup(Context context) {
        try {
            SharedPreferences backup = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE);
            
            // Only restore if backup exists and has content
            if (backup.getAll().isEmpty()) {
                Log.d(TAG, "No backup available to restore");
                return;
            }
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Restore API keys
            String openAIKey = backup.getString(KEY_OPENAI_API, "");
            if (!openAIKey.isEmpty()) {
                editor.putString(KEY_OPENAI_API, openAIKey);
                Log.i(TAG, "Restored OpenAI API key from backup");
            }
            
            String assemblyAIKey = backup.getString(KEY_ASSEMBLYAI_API, "");
            if (!assemblyAIKey.isEmpty()) {
                editor.putString(KEY_ASSEMBLYAI_API, assemblyAIKey);
                Log.i(TAG, "Restored AssemblyAI API key from backup");
            }
            
            // Restore other settings
            editor.putString(KEY_APP_LANGUAGE, backup.getString(KEY_APP_LANGUAGE, "system"));
            editor.putString(KEY_TRANSCRIPT_LANGUAGE, backup.getString(KEY_TRANSCRIPT_LANGUAGE, "auto"));
            editor.putString(KEY_TRANSCRIPTION_PROVIDER, backup.getString(KEY_TRANSCRIPTION_PROVIDER, 
                ai.intelliswarm.meetingmate.transcription.TranscriptionProvider.ProviderType.ASSEMBLYAI_SPEAKER.name()));
            editor.putBoolean(KEY_AUTO_TRANSCRIBE, backup.getBoolean(KEY_AUTO_TRANSCRIBE, true));
            editor.putBoolean(KEY_AUTO_SUMMARIZE, backup.getBoolean(KEY_AUTO_SUMMARIZE, true));
            editor.putString(KEY_AUDIO_QUALITY, backup.getString(KEY_AUDIO_QUALITY, AudioQuality.HIGH.name()));
            editor.putLong(KEY_DEFAULT_CALENDAR_ID, backup.getLong(KEY_DEFAULT_CALENDAR_ID, -1));
            
            editor.apply();
            Log.i(TAG, "Settings restored from backup successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore settings from backup", e);
        }
    }
    
    /**
     * Check if any settings are present (used to detect first run or data loss)
     */
    private boolean hasAnySettings() {
        return !getOpenAIApiKey().isEmpty() || 
               !getAssemblyAIApiKey().isEmpty() || 
               !getAppLanguage().equals("system") ||
               getDefaultCalendarId() != -1;
    }
    
    /**
     * Get settings summary for debugging
     */
    public String getSettingsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Settings Summary:\n");
        summary.append("- OpenAI API Key: ").append(hasOpenAIApiKey() ? "SET" : "NOT SET").append("\n");
        summary.append("- AssemblyAI API Key: ").append(hasAssemblyAIApiKey() ? "SET" : "NOT SET").append("\n");
        summary.append("- App Language: ").append(getAppLanguage()).append("\n");
        summary.append("- Transcription Language: ").append(getTranscriptLanguage()).append("\n");
        summary.append("- Transcription Provider: ").append(getSelectedTranscriptionProvider().getDisplayName()).append("\n");
        summary.append("- Auto Transcribe: ").append(isAutoTranscribeEnabled()).append("\n");
        summary.append("- Auto Summarize: ").append(isAutoSummarizeEnabled()).append("\n");
        summary.append("- Audio Quality: ").append(getAudioQuality().name()).append("\n");
        summary.append("- Settings Version: ").append(sharedPreferences.getInt(KEY_SETTINGS_VERSION, 0));
        return summary.toString();
    }
    
    /**
     * Manually trigger a backup of all settings (useful after configuration changes)
     */
    public void backupAllSettings(Context context) {
        Log.i(TAG, "Manual settings backup requested");
        createSettingsBackup(context);
        saveToExternalStorage(context);
    }
    
    /**
     * Get external storage directory for persistent settings with fallbacks
     */
    private File getExternalSettingsDirectory() {
        // Try multiple locations in order of preference
        File[] possibleDirs = {
            // First try: Documents directory (ideal for user access)
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), EXTERNAL_SETTINGS_DIR),
            // Second try: Downloads directory (more likely to be accessible)
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXTERNAL_SETTINGS_DIR),
            // Third try: External storage root with app name
            new File(Environment.getExternalStorageDirectory(), EXTERNAL_SETTINGS_DIR),
            // Fourth try: External files directory (app-specific external)
            appContext != null ? new File(appContext.getExternalFilesDir(null), EXTERNAL_SETTINGS_DIR) : null
        };
        
        for (File dir : possibleDirs) {
            if (dir == null) continue;
            
            try {
                // Check if we can write to this location
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        Log.i(TAG, "Created external settings directory: " + dir.getAbsolutePath());
                    } else {
                        Log.w(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                        continue;
                    }
                }
                
                // Test write permissions by creating a test file
                File testFile = new File(dir, ".test_write");
                if (testFile.createNewFile()) {
                    testFile.delete();
                    Log.d(TAG, "Using external settings directory: " + dir.getAbsolutePath());
                    return dir;
                } else {
                    Log.w(TAG, "No write permission for: " + dir.getAbsolutePath());
                }
                
            } catch (Exception e) {
                Log.w(TAG, "Cannot use directory " + dir.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        
        // If all external options fail, use internal app directory as absolute fallback
        // (This won't survive uninstall, but at least the app won't crash)
        File internalDir = new File(appContext.getFilesDir(), EXTERNAL_SETTINGS_DIR);
        internalDir.mkdirs();
        Log.w(TAG, "Using internal storage fallback: " + internalDir.getAbsolutePath() + " (won't survive uninstall)");
        return internalDir;
    }
    
    /**
     * Check if we have storage permissions
     */
    private boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Save all settings to external storage (survives uninstall/reinstall)
     */
    private void saveToExternalStorage(Context context) {
        try {
            File settingsDir = getExternalSettingsDirectory();
            File settingsFile = new File(settingsDir, EXTERNAL_SETTINGS_FILE);
            File backupFile = new File(settingsDir, EXTERNAL_BACKUP_FILE);
            
            // Create JSON with all settings
            JSONObject settings = new JSONObject();
            settings.put(KEY_OPENAI_API, getOpenAIApiKey());
            settings.put(KEY_ASSEMBLYAI_API, getAssemblyAIApiKey());
            settings.put(KEY_APP_LANGUAGE, getAppLanguage());
            settings.put(KEY_TRANSCRIPT_LANGUAGE, getTranscriptLanguage());
            settings.put(KEY_TRANSCRIPTION_PROVIDER, getSelectedTranscriptionProvider().name());
            settings.put(KEY_AUTO_TRANSCRIBE, isAutoTranscribeEnabled());
            settings.put(KEY_AUTO_SUMMARIZE, isAutoSummarizeEnabled());
            settings.put(KEY_AUDIO_QUALITY, getAudioQuality().name());
            settings.put(KEY_DEFAULT_CALENDAR_ID, getDefaultCalendarId());
            settings.put(KEY_SETTINGS_VERSION, SETTINGS_VERSION);
            settings.put("save_timestamp", System.currentTimeMillis());
            
            // Save primary file
            try (FileWriter writer = new FileWriter(settingsFile)) {
                writer.write(settings.toString(2)); // Pretty print
                Log.i(TAG, "Settings saved to external storage: " + settingsFile.getAbsolutePath());
            }
            
            // Save backup copy
            try (FileWriter backupWriter = new FileWriter(backupFile)) {
                backupWriter.write(settings.toString(2));
                Log.d(TAG, "Settings backup saved to: " + backupFile.getAbsolutePath());
            }
            
            // Also try to save a copy to Downloads folder for easy user access
            saveToDownloadsFolder(context, settings);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save settings to external storage", e);
        }
    }
    
    /**
     * Save to external storage asynchronously
     */
    private void saveToExternalStorageAsync() {
        if (appContext != null) {
            new Thread(() -> saveToExternalStorage(appContext)).start();
        } else {
            Log.w(TAG, "No context available for async save");
        }
    }
    
    /**
     * Load settings from external storage (called on app start)
     */
    private void loadFromExternalStorage(Context context) {
        try {
            File settingsDir = getExternalSettingsDirectory();
            File settingsFile = new File(settingsDir, EXTERNAL_SETTINGS_FILE);
            File backupFile = new File(settingsDir, EXTERNAL_BACKUP_FILE);
            
            File fileToLoad = settingsFile;
            if (!settingsFile.exists() && backupFile.exists()) {
                Log.w(TAG, "Primary settings file missing, using backup");
                fileToLoad = backupFile;
            }
            
            if (!fileToLoad.exists()) {
                Log.d(TAG, "No external settings file found - first run or fresh install");
                return;
            }
            
            // Read the JSON file
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(fileToLoad))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            JSONObject settings = new JSONObject(jsonContent.toString());
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Restore all settings using helper method
            restoreSettingsFromJson(settings, editor);
            editor.apply();
            
            long timestamp = settings.optLong("save_timestamp", 0);
            Log.i(TAG, "Successfully restored settings from external storage (saved: " + 
                  new java.util.Date(timestamp) + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load settings from external storage", e);
            // Also try to load from Downloads folder as fallback
            loadFromDownloadsFolder(context);
        }
    }
    
    /**
     * Save settings to Downloads folder with clear filename
     */
    private void saveToDownloadsFolder(Context context, JSONObject settings) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists() || !downloadsDir.canWrite()) {
                Log.w(TAG, "Cannot access Downloads directory for backup");
                return;
            }
            
            // Use timestamp in filename for easy identification
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(new java.util.Date());
            String filename = "MeetingMate_Settings_" + timestamp + ".json";
            File downloadFile = new File(downloadsDir, filename);
            
            try (FileWriter writer = new FileWriter(downloadFile)) {
                writer.write(settings.toString(2));
                Log.i(TAG, "Settings backup saved to Downloads: " + downloadFile.getAbsolutePath());
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to save settings to Downloads", e);
        }
    }
    
    /**
     * Load settings from Downloads folder (fallback)
     */
    private void loadFromDownloadsFolder(Context context) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                Log.d(TAG, "Downloads directory not accessible for fallback load");
                return;
            }
            
            // Look for MeetingMate settings files in Downloads
            File[] files = downloadsDir.listFiles((dir, name) -> 
                name.startsWith("MeetingMate_Settings_") && name.endsWith(".json"));
            
            if (files == null || files.length == 0) {
                Log.d(TAG, "No MeetingMate settings files found in Downloads");
                return;
            }
            
            // Use the most recent file
            File mostRecent = files[0];
            for (File file : files) {
                if (file.lastModified() > mostRecent.lastModified()) {
                    mostRecent = file;
                }
            }
            
            // Load from the most recent file
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(mostRecent))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            JSONObject settings = new JSONObject(jsonContent.toString());
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Restore all settings (same logic as before)
            restoreSettingsFromJson(settings, editor);
            editor.apply();
            
            Log.i(TAG, "Settings restored from Downloads fallback: " + mostRecent.getName());
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to load settings from Downloads fallback", e);
        }
    }
    
    /**
     * Helper method to restore settings from JSON to SharedPreferences editor
     */
    private void restoreSettingsFromJson(JSONObject settings, SharedPreferences.Editor editor) throws JSONException {
        Log.d(TAG, "Restoring settings from JSON. Keys found: " + settings.keys());
        
        if (settings.has(KEY_OPENAI_API)) {
            String openAIKey = settings.getString(KEY_OPENAI_API);
            Log.d(TAG, "Found OpenAI key in JSON, length: " + openAIKey.length());
            if (!openAIKey.isEmpty()) {
                editor.putString(KEY_OPENAI_API, openAIKey);
                Log.i(TAG, "Restored OpenAI API key: " + openAIKey.substring(0, Math.min(8, openAIKey.length())) + "...");
            }
        } else {
            Log.w(TAG, "No OpenAI key found in JSON");
        }
        
        if (settings.has(KEY_ASSEMBLYAI_API)) {
            String assemblyAIKey = settings.getString(KEY_ASSEMBLYAI_API);
            Log.d(TAG, "Found AssemblyAI key in JSON, length: " + assemblyAIKey.length());
            if (!assemblyAIKey.isEmpty()) {
                editor.putString(KEY_ASSEMBLYAI_API, assemblyAIKey);
                Log.i(TAG, "Restored AssemblyAI API key: " + assemblyAIKey.substring(0, Math.min(8, assemblyAIKey.length())) + "...");
            }
        } else {
            Log.w(TAG, "No AssemblyAI key found in JSON");
        }
        
        if (settings.has(KEY_APP_LANGUAGE)) {
            editor.putString(KEY_APP_LANGUAGE, settings.getString(KEY_APP_LANGUAGE));
        }
        
        if (settings.has(KEY_TRANSCRIPT_LANGUAGE)) {
            editor.putString(KEY_TRANSCRIPT_LANGUAGE, settings.getString(KEY_TRANSCRIPT_LANGUAGE));
        }
        
        if (settings.has(KEY_TRANSCRIPTION_PROVIDER)) {
            editor.putString(KEY_TRANSCRIPTION_PROVIDER, settings.getString(KEY_TRANSCRIPTION_PROVIDER));
        }
        
        if (settings.has(KEY_AUTO_TRANSCRIBE)) {
            editor.putBoolean(KEY_AUTO_TRANSCRIBE, settings.getBoolean(KEY_AUTO_TRANSCRIBE));
        }
        
        if (settings.has(KEY_AUTO_SUMMARIZE)) {
            editor.putBoolean(KEY_AUTO_SUMMARIZE, settings.getBoolean(KEY_AUTO_SUMMARIZE));
        }
        
        if (settings.has(KEY_AUDIO_QUALITY)) {
            editor.putString(KEY_AUDIO_QUALITY, settings.getString(KEY_AUDIO_QUALITY));
        }
        
        if (settings.has(KEY_DEFAULT_CALENDAR_ID)) {
            editor.putLong(KEY_DEFAULT_CALENDAR_ID, settings.getLong(KEY_DEFAULT_CALENDAR_ID));
        }
        
        if (settings.has(KEY_SETTINGS_VERSION)) {
            editor.putInt(KEY_SETTINGS_VERSION, settings.getInt(KEY_SETTINGS_VERSION));
        }
    }
    
    /**
     * Get the path where settings are saved for user reference
     */
    public String getSettingsFilePath() {
        File settingsDir = getExternalSettingsDirectory();
        File settingsFile = new File(settingsDir, EXTERNAL_SETTINGS_FILE);
        return settingsFile.getAbsolutePath();
    }
    
    /**
     * Check if external settings file exists
     */
    public boolean hasExternalSettings() {
        File settingsDir = getExternalSettingsDirectory();
        File settingsFile = new File(settingsDir, EXTERNAL_SETTINGS_FILE);
        File backupFile = new File(settingsDir, EXTERNAL_BACKUP_FILE);
        return settingsFile.exists() || backupFile.exists();
    }
    
    /**
     * Manually export current settings to external storage
     */
    public boolean exportSettings() {
        if (appContext != null) {
            try {
                saveToExternalStorage(appContext);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to export settings", e);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Manually import settings from external storage
     */
    public boolean importSettings() {
        if (appContext != null) {
            try {
                Log.i(TAG, "Starting manual settings import");
                
                // Before import, log current state
                Log.d(TAG, "Before import - OpenAI key: " + (hasOpenAIApiKey() ? "SET" : "NOT SET"));
                Log.d(TAG, "Before import - AssemblyAI key: " + (hasAssemblyAIApiKey() ? "SET" : "NOT SET"));
                
                loadFromExternalStorage(appContext);
                
                // After import, log new state
                Log.d(TAG, "After import - OpenAI key: " + (hasOpenAIApiKey() ? "SET" : "NOT SET"));
                Log.d(TAG, "After import - AssemblyAI key: " + (hasAssemblyAIApiKey() ? "SET" : "NOT SET"));
                
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to import settings", e);
                return false;
            }
        }
        return false;
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