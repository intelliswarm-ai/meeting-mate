package ai.intelliswarm.meetingmate.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import ai.intelliswarm.meetingmate.analytics.AppLogger;
import ai.intelliswarm.meetingmate.analytics.LogViewerActivity;
import ai.intelliswarm.meetingmate.databinding.FragmentNotificationsBinding;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import ai.intelliswarm.meetingmate.transcription.TranscriptionProvider;
import ai.intelliswarm.meetingmate.transcription.TranscriptionManager;
import ai.intelliswarm.meetingmate.transcription.LocalWhisperProvider;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {
    
    private static final String TAG = "NotificationsFragment";
    private FragmentNotificationsBinding binding;
    private SettingsManager settingsManager;
    private TranscriptionManager transcriptionManager;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        try {
            AppLogger.lifecycle("NotificationsFragment", "onCreateView");
            
            NotificationsViewModel notificationsViewModel =
                    new ViewModelProvider(this).get(NotificationsViewModel.class);
            AppLogger.d(TAG, "ViewModel created");

            binding = FragmentNotificationsBinding.inflate(inflater, container, false);
            View root = binding.getRoot();
            AppLogger.d(TAG, "Fragment view inflated successfully");

            // Initialize services
            settingsManager = SettingsManager.getInstance(requireContext());
            transcriptionManager = new TranscriptionManager(requireContext());
            AppLogger.d(TAG, "Services initialized");

            setupUI();
            AppLogger.d(TAG, "UI setup completed");
            
            loadSettings();
            AppLogger.d(TAG, "Settings loaded");
            
            // Update settings path display
            updateSettingsPath();
            
            AppLogger.i(TAG, "NotificationsFragment initialized successfully");
            return root;
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error initializing NotificationsFragment", e);
            
            // Return a simple error view if initialization fails
            android.widget.TextView errorView = new android.widget.TextView(requireContext());
            errorView.setText("Error loading settings. Please check logs.");
            errorView.setGravity(android.view.Gravity.CENTER);
            errorView.setPadding(32, 32, 32, 32);
            return errorView;
        }
    }

    private void setupUI() {
        // Setup API key section
        binding.buttonSaveApiKey.setOnClickListener(v -> saveOpenAIApiKey());
        binding.buttonSaveAssemblyaiKey.setOnClickListener(v -> saveAssemblyAIApiKey());
        
        // Setup debug logs button
        binding.buttonViewDebugLogs.setOnClickListener(v -> {
            AppLogger.userAction("NotificationsFragment", "debug_logs_clicked", null);
            Intent intent = new Intent(requireContext(), LogViewerActivity.class);
            startActivity(intent);
        });
        
        // Setup settings export/import buttons
        binding.buttonExportSettings.setOnClickListener(v -> exportSettings());
        binding.buttonImportSettings.setOnClickListener(v -> importSettings());
        
        // Setup general settings
        binding.checkboxAutoTranscribe.setOnCheckedChangeListener((buttonView, isChecked) -> 
            settingsManager.setAutoTranscribe(isChecked));
            
        binding.checkboxAutoSummarize.setOnCheckedChangeListener((buttonView, isChecked) -> 
            settingsManager.setAutoSummarize(isChecked));
            
        // Setup audio quality spinner
        setupAudioQualitySpinner();
        
        // Setup app language spinner
        setupAppLanguageSpinner();
        
        // Setup transcription provider spinner
        setupTranscriptionProviderSpinner();
        
        // Setup transcription language spinner
        setupTranscriptionLanguageSpinner();
    }
    
    
    
    
    private void setupAudioQualitySpinner() {
        SettingsManager.AudioQuality[] qualities = SettingsManager.AudioQuality.values();
        String[] qualityNames = new String[qualities.length];
        
        for (int i = 0; i < qualities.length; i++) {
            qualityNames[i] = qualities[i].name() + " (" + 
                qualities[i].getBitRate()/1000 + " kbps)";
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(), 
            android.R.layout.simple_spinner_item, 
            qualityNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerAudioQuality.setAdapter(adapter);
        
        binding.spinnerAudioQuality.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position < qualities.length) {
                        settingsManager.setAudioQuality(qualities[position]);
                    }
                }
                
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            }
        );
    }
    
    private void setupAppLanguageSpinner() {
        // UI languages - only those we have translations for
        String[] languages = {
            "System Default", "English", "Spanish", "French", "German", "Italian", "Portuguese", 
            "Russian", "Polish", "Dutch", "Greek", "Swedish", "Danish", "Czech"
        };
        String[] codes = {
            "system", "en", "es", "fr", "de", "it", "pt", "ru", "pl", "nl", "el", "sv", "da", "cs"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(), 
            android.R.layout.simple_spinner_item, 
            languages
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerAppLanguage.setAdapter(adapter);
        
        binding.spinnerAppLanguage.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position < codes.length) {
                        String selectedLanguage = codes[position];
                        String currentLanguage = settingsManager.getAppLanguage();
                        
                        // Only change if different from current language
                        if (!selectedLanguage.equals(currentLanguage)) {
                            // Apply language change immediately (this will save and restart)
                            SettingsManager.applyLanguageToActivity(requireActivity(), selectedLanguage);
                        }
                    }
                }
                
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            }
        );
    }
    
    private void setupTranscriptionLanguageSpinner() {
        // European languages supported by OpenAI Whisper
        String[] languages = {
            "Auto-detect", "English", "Spanish", "French", "German", "Italian", "Portuguese", 
            "Russian", "Polish", "Dutch", "Swedish", "Danish", "Norwegian", "Finnish", 
            "Hungarian", "Czech", "Slovak", "Romanian", "Bulgarian", "Croatian", "Slovenian", 
            "Estonian", "Latvian", "Lithuanian", "Maltese", "Greek", "Turkish", "Ukrainian"
        };
        String[] codes = {
            "auto", "en", "es", "fr", "de", "it", "pt", "ru", "pl", "nl", "sv", "da", "no", 
            "fi", "hu", "cs", "sk", "ro", "bg", "hr", "sl", "et", "lv", "lt", "mt", "el", "tr", "uk"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(), 
            android.R.layout.simple_spinner_item, 
            languages
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerLanguage.setAdapter(adapter);
        
        binding.spinnerLanguage.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position < codes.length) {
                        settingsManager.setTranscriptLanguage(codes[position]);
                    }
                }
                
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            }
        );
    }
    
    private void setupTranscriptionProviderSpinner() {
        // Get available providers from TranscriptionProvider enum
        TranscriptionProvider.ProviderType[] allProviders = TranscriptionProvider.ProviderType.values();
        
        // Filter to working providers only
        List<TranscriptionProvider.ProviderType> workingProviders = new ArrayList<>();
        List<String> providerNames = new ArrayList<>();
        
        for (TranscriptionProvider.ProviderType provider : allProviders) {
            if (provider == TranscriptionProvider.ProviderType.OPENAI_WHISPER || 
                provider == TranscriptionProvider.ProviderType.ASSEMBLYAI_SPEAKER) {
                workingProviders.add(provider);
                providerNames.add(provider.getDisplayName());
            }
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(), 
            android.R.layout.simple_spinner_item, 
            providerNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTranscriptionProvider.setAdapter(adapter);
        
        binding.spinnerTranscriptionProvider.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position < workingProviders.size()) {
                        TranscriptionProvider.ProviderType selectedProvider = workingProviders.get(position);
                        settingsManager.setSelectedTranscriptionProvider(selectedProvider);
                        Log.d(TAG, "Selected transcription provider: " + selectedProvider.getDisplayName());
                    }
                }
                
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            }
        );
    }
    
    
    private void loadSettings() {
        Log.d(TAG, "Loading settings - refreshing UI");
        
        // Load current OpenAI API key (show only first few characters for security)
        String openAIApiKey = settingsManager.getOpenAIApiKey();
        Log.d(TAG, "OpenAI API Key length: " + openAIApiKey.length());
        if (!openAIApiKey.isEmpty()) {
            String maskedKey = openAIApiKey.substring(0, Math.min(8, openAIApiKey.length())) + "...";
            binding.editOpenaiKey.setHint("Current: " + maskedKey);
            binding.editOpenaiKey.setText(""); // Clear the text field
            Log.d(TAG, "Set OpenAI hint to: " + maskedKey);
        } else {
            binding.editOpenaiKey.setHint("Enter your OpenAI API key");
        }
        
        // Load current AssemblyAI API key (show only first few characters for security)
        String assemblyAIApiKey = settingsManager.getAssemblyAIApiKey();
        Log.d(TAG, "AssemblyAI API Key length: " + assemblyAIApiKey.length());
        if (!assemblyAIApiKey.isEmpty()) {
            String maskedKey = assemblyAIApiKey.substring(0, Math.min(8, assemblyAIApiKey.length())) + "...";
            binding.editAssemblyaiKey.setHint("Current: " + maskedKey);
            binding.editAssemblyaiKey.setText(""); // Clear the text field
            Log.d(TAG, "Set AssemblyAI hint to: " + maskedKey);
        } else {
            binding.editAssemblyaiKey.setHint("Enter your AssemblyAI API key");
        }
        
        
        // Load checkboxes
        binding.checkboxAutoTranscribe.setChecked(settingsManager.isAutoTranscribeEnabled());
        binding.checkboxAutoSummarize.setChecked(settingsManager.isAutoSummarizeEnabled());
        
        // Load audio quality
        SettingsManager.AudioQuality currentQuality = settingsManager.getAudioQuality();
        SettingsManager.AudioQuality[] qualities = SettingsManager.AudioQuality.values();
        for (int i = 0; i < qualities.length; i++) {
            if (qualities[i] == currentQuality) {
                binding.spinnerAudioQuality.setSelection(i);
                break;
            }
        }
        
        // Load app language
        String currentAppLanguage = settingsManager.getAppLanguage();
        String[] appCodes = {"system", "en", "es", "fr", "de", "it", "pt", "ru", "pl", "nl", "el", "sv", "da", "cs"};
        for (int i = 0; i < appCodes.length; i++) {
            if (appCodes[i].equals(currentAppLanguage)) {
                binding.spinnerAppLanguage.setSelection(i);
                break;
            }
        }
        
        // Load transcription provider
        TranscriptionProvider.ProviderType currentProvider = settingsManager.getSelectedTranscriptionProvider();
        TranscriptionProvider.ProviderType[] workingProviders = {
            TranscriptionProvider.ProviderType.OPENAI_WHISPER,
            TranscriptionProvider.ProviderType.ASSEMBLYAI_SPEAKER
        };
        for (int i = 0; i < workingProviders.length; i++) {
            if (workingProviders[i] == currentProvider) {
                binding.spinnerTranscriptionProvider.setSelection(i);
                break;
            }
        }
        
        // Load transcription language
        String currentTranscriptLanguage = settingsManager.getTranscriptLanguage();
        String[] transcriptCodes = {
            "auto", "en", "es", "fr", "de", "it", "pt", "ru", "pl", "nl", "sv", "da", "no", 
            "fi", "hu", "cs", "sk", "ro", "bg", "hr", "sl", "et", "lv", "lt", "mt", "el", "tr", "uk"
        };
        for (int i = 0; i < transcriptCodes.length; i++) {
            if (transcriptCodes[i].equals(currentTranscriptLanguage)) {
                binding.spinnerLanguage.setSelection(i);
                break;
            }
        }
    }
    
    private void saveOpenAIApiKey() {
        String apiKey = binding.editOpenaiKey.getText().toString().trim();
        Log.d(TAG, "Attempting to save API key of length: " + apiKey.length());
        
        if (apiKey.isEmpty()) {
            Log.w(TAG, "Empty API key entered");
            Toast.makeText(requireContext(), "Please enter an API key", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show validation progress
        Toast.makeText(requireContext(), "Validating API key...", Toast.LENGTH_SHORT).show();
        binding.buttonSaveApiKey.setEnabled(false);
        
        Log.d(TAG, "Creating temporary OpenAI service for validation");
        // Create temporary OpenAI service to validate the key
        ai.intelliswarm.meetingmate.service.OpenAIService tempService = 
            new ai.intelliswarm.meetingmate.service.OpenAIService(apiKey);
        
        tempService.validateApiKey(new ai.intelliswarm.meetingmate.service.OpenAIService.ApiKeyValidationCallback() {
            @Override
            public void onValidationResult(boolean isValid, String message) {
                Log.d(TAG, "API key validation result: " + isValid + ", message: " + message);
                requireActivity().runOnUiThread(() -> {
                    binding.buttonSaveApiKey.setEnabled(true);
                    
                    if (isValid) {
                        Log.d(TAG, "API key validation successful, saving to preferences");
                        // Save the key only if it's valid
                        settingsManager.setOpenAIApiKey(apiKey);
                        Toast.makeText(requireContext(), "API key saved and validated successfully!", Toast.LENGTH_LONG).show();
                        binding.editOpenaiKey.setText("");
                        
                        // Update the hint to show it's saved
                        String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "...";
                        binding.editOpenaiKey.setHint("Current: " + maskedKey);
                        
                        // Trigger backup to ensure persistence across app updates
                        settingsManager.backupAllSettings(requireContext());
                        
                    } else {
                        Log.w(TAG, "API key validation failed: " + message);
                        Toast.makeText(requireContext(), "API key validation failed: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    
    private void saveAssemblyAIApiKey() {
        String apiKey = binding.editAssemblyaiKey.getText().toString().trim();
        Log.d(TAG, "Attempting to save AssemblyAI API key of length: " + apiKey.length());
        
        if (apiKey.isEmpty()) {
            Log.w(TAG, "Empty AssemblyAI API key entered");
            Toast.makeText(requireContext(), "Please enter an AssemblyAI API key", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // For now, save without validation (AssemblyAI validation is more complex)
        Log.d(TAG, "Saving AssemblyAI API key to preferences");
        settingsManager.setAssemblyAIApiKey(apiKey);
        Toast.makeText(requireContext(), "AssemblyAI API key saved successfully!", Toast.LENGTH_LONG).show();
        binding.editAssemblyaiKey.setText("");
        
        // Update the hint to show it's saved
        String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "...";
        binding.editAssemblyaiKey.setHint("Current: " + maskedKey);
        
        // Trigger backup to ensure persistence across app updates
        settingsManager.backupAllSettings(requireContext());
        
        Log.d(TAG, "AssemblyAI API key saved and ready for speaker detection");
    }
    
    private void exportSettings() {
        Log.d(TAG, "Manual settings export requested");
        
        // Log what we're about to export
        Log.d(TAG, "Current settings before export:");
        Log.d(TAG, "OpenAI Key: " + (settingsManager.hasOpenAIApiKey() ? "SET" : "NOT SET"));
        Log.d(TAG, "AssemblyAI Key: " + (settingsManager.hasAssemblyAIApiKey() ? "SET" : "NOT SET"));
        Log.d(TAG, settingsManager.getSettingsSummary());
        
        boolean success = settingsManager.exportSettings();
        if (success) {
            String path = settingsManager.getSettingsFilePath();
            Toast.makeText(requireContext(), 
                "Settings exported successfully!\nSaved to: " + path, 
                Toast.LENGTH_LONG).show();
            Log.i(TAG, "Settings exported to: " + path);
        } else {
            Toast.makeText(requireContext(), 
                "Failed to export settings. Check permissions.", 
                Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Settings export failed");
        }
    }
    
    private void importSettings() {
        Log.d(TAG, "Manual settings import requested");
        
        if (!settingsManager.hasExternalSettings()) {
            Toast.makeText(requireContext(), 
                "No external settings file found. Export settings first or check Documents/MeetingMate/ folder.", 
                Toast.LENGTH_LONG).show();
            return;
        }
        
        boolean success = settingsManager.importSettings();
        if (success) {
            // Log current state for debugging
            Log.i(TAG, "Settings imported successfully. Current state:");
            Log.i(TAG, "OpenAI Key: " + (settingsManager.hasOpenAIApiKey() ? "SET" : "NOT SET"));
            Log.i(TAG, "AssemblyAI Key: " + (settingsManager.hasAssemblyAIApiKey() ? "SET" : "NOT SET"));
            Log.i(TAG, "App Language: " + settingsManager.getAppLanguage());
            Log.i(TAG, settingsManager.getSettingsSummary());
            
            Toast.makeText(requireContext(), 
                "Settings imported successfully! Refreshing UI...", 
                Toast.LENGTH_LONG).show();
            
            // Force refresh the UI to show imported settings
            requireActivity().runOnUiThread(() -> {
                loadSettings();
                updateSettingsPath();
            });
            
        } else {
            Toast.makeText(requireContext(), 
                "Failed to import settings.", 
                Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Settings import failed");
        }
    }
    
    private void updateSettingsPath() {
        String path = settingsManager.getSettingsFilePath();
        boolean hasSettings = settingsManager.hasExternalSettings();
        
        String statusText = "Settings are automatically saved to:\n" + path;
        if (hasSettings) {
            statusText += "\n✅ Settings file found - will survive reinstalls";
        } else {
            statusText += "\n⚠️ No settings file found yet";
        }
        statusText += "\n💡 Also backed up to Downloads folder for easy access";
        
        binding.textSettingsPath.setText(statusText);
    }
    

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}