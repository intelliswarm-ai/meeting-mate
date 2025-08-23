package ai.intelliswarm.meetingmate.transcription;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class AndroidSpeechProvider implements TranscriptionProvider {
    
    private static final String TAG = "AndroidSpeechProvider";
    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private TranscriptionCallback callback;
    private StringBuilder transcriptBuilder;
    private boolean isListening = false;
    
    public AndroidSpeechProvider(Context context) {
        this.context = context;
        this.transcriptBuilder = new StringBuilder();
    }
    
    @Override
    public ProviderType getType() {
        return ProviderType.ANDROID_SPEECH;
    }
    
    @Override
    public boolean isConfigured() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }
    
    @Override
    public String getConfigurationRequirement() {
        return isConfigured() ? "Ready to use" : "Speech recognition not available on this device";
    }
    
    @Override
    public void transcribe(File audioFile, TranscriptionCallback callback) {
        // For file-based transcription, we'll use a simpler approach
        this.callback = callback;
        
        Log.d(TAG, "Starting transcription for audio file: " + audioFile.getPath());
        
        if (!isConfigured()) {
            Log.e(TAG, "Speech recognition not available on this device");
            callback.onError("Speech recognition not available on this device");
            return;
        }
        
        // Note: Android SpeechRecognizer works best with live audio
        // For file-based transcription, we would need to play the file and capture it
        Log.w(TAG, "Android Speech Recognition does not support file transcription for: " + audioFile.getName());
        callback.onError("Android Speech Recognition only works with live recording. File transcription requires OpenAI Whisper (configure in Settings).");
    }
    
    @Override
    public String[] getSupportedFormats() {
        // Android Speech only supports live audio, not file formats
        return new String[]{"live"};
    }
    
    @Override
    public int getMaxFileSizeMB() {
        return 0; // Not applicable for live transcription
    }
    
    @Override
    public void cancel() {
        stopLiveTranscription();
    }
    
    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }
    
    // Method for live transcription during recording
    public void startLiveTranscription(TranscriptionCallback callback) {
        this.callback = callback;
        this.transcriptBuilder = new StringBuilder();
        
        Log.d(TAG, "Starting live speech recognition");
        
        if (!isAvailable()) {
            Log.e(TAG, "Speech recognition not available");
            callback.onError("Speech recognition not available on this device");
            return;
        }
        
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
                isListening = true;
            }
            
            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech");
            }
            
            @Override
            public void onRmsChanged(float rmsdB) {
                // RMS value for volume indication
            }
            
            @Override
            public void onBufferReceived(byte[] buffer) {
                // Audio buffer received
            }
            
            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "End of speech");
            }
            
            @Override
            public void onError(int error) {
                Log.e(TAG, "Speech recognition error: " + getErrorString(error));
                isListening = false;
                
                // Auto-restart for continuous recognition (except for certain errors)
                if (error != SpeechRecognizer.ERROR_CLIENT && 
                    error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
                    error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    
                    // Restart recognition after a short delay
                    android.os.Handler handler = new android.os.Handler();
                    handler.postDelayed(() -> {
                        if (speechRecognizer != null) {
                            startListening();
                        }
                    }, 1000);
                } else {
                    callback.onError("Speech recognition error: " + getErrorString(error));
                }
            }
            
            @Override
            public void onResults(Bundle results) {
                Log.d(TAG, "Speech recognition results received");
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    Log.d(TAG, "Recognized: " + recognizedText);
                    
                    // Append to transcript
                    if (transcriptBuilder.length() > 0) {
                        transcriptBuilder.append(" ");
                    }
                    transcriptBuilder.append(recognizedText);
                    
                    // Provide partial results
                    callback.onPartialResult(transcriptBuilder.toString());
                }
                
                isListening = false;
                
                // Restart for continuous recognition
                android.os.Handler handler = new android.os.Handler();
                handler.postDelayed(() -> {
                    if (speechRecognizer != null) {
                        startListening();
                    }
                }, 500);
            }
            
            @Override
            public void onPartialResults(Bundle partialResults) {
                Log.d(TAG, "Partial results received");
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                
                if (matches != null && !matches.isEmpty()) {
                    String partialText = matches.get(0);
                    Log.d(TAG, "Partial: " + partialText);
                    
                    // Show partial results
                    String fullTranscript = transcriptBuilder.toString();
                    if (fullTranscript.length() > 0) {
                        fullTranscript += " " + partialText;
                    } else {
                        fullTranscript = partialText;
                    }
                    callback.onPartialResult(fullTranscript);
                }
            }
            
            @Override
            public void onEvent(int eventType, Bundle params) {
                // Additional events
            }
        });
        
        startListening();
    }
    
    private void startListening() {
        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer not initialized");
            return;
        }
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        
        // Enable continuous recognition
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000);
        
        try {
            speechRecognizer.startListening(intent);
            Log.d(TAG, "Started listening for speech");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start listening", e);
            if (callback != null) {
                callback.onError("Failed to start speech recognition: " + e.getMessage());
            }
        }
    }
    
    public void stopLiveTranscription() {
        Log.d(TAG, "Stopping live transcription");
        isListening = false;
        
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        
        // Return final transcript
        if (callback != null && transcriptBuilder.length() > 0) {
            String finalTranscript = transcriptBuilder.toString().trim();
            Log.d(TAG, "Final transcript: " + finalTranscript);
            callback.onSuccess(finalTranscript, null); // No segments for Android Speech
        }
    }
    
    public boolean isListening() {
        return isListening;
    }
    
    public String getCurrentTranscript() {
        return transcriptBuilder.toString().trim();
    }
    
    private String getErrorString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Speech input timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognition service busy";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                return "Too many requests";
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
                return "Server disconnected";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                return "Language not supported";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                return "Language unavailable";
            case SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT:
                return "Cannot check support";
            default:
                return "Unknown error (" + error + ")";
        }
    }
    
    public void cleanup() {
        Log.d(TAG, "Cleaning up Android Speech Provider");
        stopLiveTranscription();
    }
}