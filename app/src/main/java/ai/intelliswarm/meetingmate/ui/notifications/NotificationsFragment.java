package ai.intelliswarm.meetingmate.ui.notifications;

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
        NotificationsViewModel notificationsViewModel =
                new ViewModelProvider(this).get(NotificationsViewModel.class);

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize services
        settingsManager = SettingsManager.getInstance(requireContext());
        transcriptionManager = new TranscriptionManager(requireContext());

        setupUI();
        loadSettings();

        return root;
    }

    private void setupUI() {
        // Setup transcription provider spinner
        setupTranscriptionProviders();
        
        // Setup API key section
        binding.buttonSaveApiKey.setOnClickListener(v -> saveApiKey());
        
        // Setup provider configuration
        binding.buttonConfigureProvider.setOnClickListener(v -> configureProvider());
        
        // Setup local model download (initially hidden)
        binding.buttonDownloadModel.setOnClickListener(v -> downloadModel());
        binding.buttonDeleteModel.setOnClickListener(v -> deleteModel());
        
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
    
    private void setupTranscriptionProviders() {
        List<String> providerNames = new ArrayList<>();
        TranscriptionProvider[] providers = transcriptionManager.getAllProviders();
        
        for (TranscriptionProvider provider : providers) {
            String name = provider.getType().getDisplayName();
            if (!provider.isConfigured()) {
                name += " (Not configured)";
            }
            providerNames.add(name);
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
                    TranscriptionProvider.ProviderType[] types = TranscriptionProvider.ProviderType.values();
                    if (position < types.length) {
                        settingsManager.setSelectedTranscriptionProvider(types[position]);
                        updateProviderInfo(types[position]);
                    }
                }
                
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            }
        );
    }
    
    private void updateProviderInfo(TranscriptionProvider.ProviderType type) {
        TranscriptionProvider provider = transcriptionManager.getProvider(type);
        if (provider != null) {
            binding.textProviderInfo.setText(transcriptionManager.getProviderSetupInstructions(type));
            
            // Show/hide sections based on provider type
            binding.cardApiKey.setVisibility(
                type.requiresApiKey() ? View.VISIBLE : View.GONE
            );
            
            binding.cardLocalModel.setVisibility(
                type == TranscriptionProvider.ProviderType.LOCAL_WHISPER ? View.VISIBLE : View.GONE
            );
            
            if (type == TranscriptionProvider.ProviderType.LOCAL_WHISPER) {
                updateLocalModelStatus();
            }
        }
    }
    
    private void updateLocalModelStatus() {
        LocalWhisperProvider localProvider = (LocalWhisperProvider) transcriptionManager.getProvider(
            TranscriptionProvider.ProviderType.LOCAL_WHISPER
        );
        
        if (localProvider != null) {
            if (localProvider.isConfigured()) {
                binding.textModelStatus.setText("Model downloaded and ready");
                binding.buttonDownloadModel.setVisibility(View.GONE);
                binding.buttonDeleteModel.setVisibility(View.VISIBLE);
            } else {
                binding.textModelStatus.setText("Model not downloaded");
                binding.buttonDownloadModel.setVisibility(View.VISIBLE);
                binding.buttonDeleteModel.setVisibility(View.GONE);
            }
        }
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
        String[] languages = {"English (en)", "Spanish (es)", "French (fr)", "German (de)", "Italian (it)"};
        String[] codes = {"en", "es", "fr", "de", "it"};
        
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
        
        // Load selected provider
        TranscriptionProvider.ProviderType currentProvider = settingsManager.getSelectedTranscriptionProvider();
        TranscriptionProvider.ProviderType[] types = TranscriptionProvider.ProviderType.values();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == currentProvider) {
                binding.spinnerTranscriptionProvider.setSelection(i);
                updateProviderInfo(currentProvider);
                break;
            }
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
        String[] codes = {"en", "es", "fr", "de", "it"};
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
                        
                        // Refresh provider list to show configuration status
                        setupTranscriptionProviders();
                    } else {
                        Log.w(TAG, "API key validation failed: " + message);
                        Toast.makeText(requireContext(), "API key validation failed: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    
    private void configureProvider() {
        TranscriptionProvider.ProviderType selectedType = settingsManager.getSelectedTranscriptionProvider();
        
        if (selectedType.requiresApiKey() && !settingsManager.hasOpenAIApiKey()) {
            Toast.makeText(requireContext(), "Please save your API key first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(requireContext(), 
            selectedType.getDisplayName() + " is ready to use!", 
            Toast.LENGTH_SHORT).show();
    }
    
    private void downloadModel() {
        binding.progressModelDownload.setVisibility(View.VISIBLE);
        binding.textDownloadProgress.setVisibility(View.VISIBLE);
        binding.buttonDownloadModel.setEnabled(false);
        
        LocalWhisperProvider localProvider = (LocalWhisperProvider) transcriptionManager.getProvider(
            TranscriptionProvider.ProviderType.LOCAL_WHISPER
        );
        
        if (localProvider != null) {
            localProvider.downloadModel(new LocalWhisperProvider.ModelDownloadCallback() {
                @Override
                public void onProgress(int percent, String message) {
                    requireActivity().runOnUiThread(() -> {
                        binding.progressModelDownload.setProgress(percent);
                        binding.textDownloadProgress.setText(message);
                    });
                }
                
                @Override
                public void onSuccess(String message) {
                    requireActivity().runOnUiThread(() -> {
                        binding.progressModelDownload.setVisibility(View.GONE);
                        binding.textDownloadProgress.setVisibility(View.GONE);
                        binding.buttonDownloadModel.setEnabled(true);
                        updateLocalModelStatus();
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        binding.progressModelDownload.setVisibility(View.GONE);
                        binding.textDownloadProgress.setVisibility(View.GONE);
                        binding.buttonDownloadModel.setEnabled(true);
                        Toast.makeText(requireContext(), "Download failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }
    
    private void deleteModel() {
        // Implementation to delete local model files
        Toast.makeText(requireContext(), "Model deleted", Toast.LENGTH_SHORT).show();
        updateLocalModelStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}