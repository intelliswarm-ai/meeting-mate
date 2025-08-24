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
        // Setup API key section - only OpenAI API key
        binding.buttonSaveApiKey.setOnClickListener(v -> saveApiKey());
        
        // Setup debug logs button
        binding.buttonViewDebugLogs.setOnClickListener(v -> {
            AppLogger.userAction("NotificationsFragment", "debug_logs_clicked", null);
            Intent intent = new Intent(requireContext(), LogViewerActivity.class);
            startActivity(intent);
        });
        
        // Setup general settings
        binding.checkboxAutoTranscribe.setOnCheckedChangeListener((buttonView, isChecked) -> 
            settingsManager.setAutoTranscribe(isChecked));
            
        binding.checkboxAutoSummarize.setOnCheckedChangeListener((buttonView, isChecked) -> 
            settingsManager.setAutoSummarize(isChecked));
            
        // Setup audio quality spinner
        setupAudioQualitySpinner();
        
        // Setup language spinner
        setupLanguageSpinner();
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
    
    private void setupLanguageSpinner() {
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
    
    private void loadSettings() {
        // Load current API key (show only first few characters for security)
        String apiKey = settingsManager.getOpenAIApiKey();
        if (!apiKey.isEmpty()) {
            String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "...";
            binding.editOpenaiKey.setHint("Current: " + maskedKey);
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
        
        // Load language
        String currentLanguage = settingsManager.getTranscriptLanguage();
        String[] codes = {
            "auto", "en", "es", "fr", "de", "it", "pt", "ru", "pl", "nl", "sv", "da", "no", 
            "fi", "hu", "cs", "sk", "ro", "bg", "hr", "sl", "et", "lv", "lt", "mt", "el", "tr", "uk"
        };
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLanguage)) {
                binding.spinnerLanguage.setSelection(i);
                break;
            }
        }
    }
    
    private void saveApiKey() {
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
                        
                    } else {
                        Log.w(TAG, "API key validation failed: " + message);
                        Toast.makeText(requireContext(), "API key validation failed: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    
    

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}