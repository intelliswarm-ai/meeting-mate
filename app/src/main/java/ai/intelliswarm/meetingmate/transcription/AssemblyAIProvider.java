package ai.intelliswarm.meetingmate.transcription;

import android.content.Context;
import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ai.intelliswarm.meetingmate.utils.SettingsManager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * AssemblyAI provider with real speaker diarization
 * This uses AssemblyAI's actual speaker identification which works much better than our attempts
 */
public class AssemblyAIProvider implements TranscriptionProvider {
    
    private static final String TAG = "AssemblyAIProvider";
    private static final String UPLOAD_URL = "https://api.assemblyai.com/v2/upload";
    private static final String TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript";
    
    private final Context context;
    private final OkHttpClient client;
    private Call currentCall;
    
    public AssemblyAIProvider(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // 5 minutes for processing
            .build();
    }
    
    @Override
    public ProviderType getType() {
        return ProviderType.ASSEMBLYAI_SPEAKER;
    }
    
    @Override
    public boolean isConfigured() {
        SettingsManager settings = SettingsManager.getInstance(context);
        return settings.getAssemblyAIApiKey() != null && !settings.getAssemblyAIApiKey().isEmpty();
    }
    
    @Override
    public String getConfigurationRequirement() {
        return "AssemblyAI API Key required for speaker diarization. Get one from https://www.assemblyai.com/";
    }
    
    @Override
    public void transcribe(File audioFile, TranscriptionCallback callback) {
        SettingsManager settings = SettingsManager.getInstance(context);
        String apiKey = settings.getAssemblyAIApiKey();
        
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("AssemblyAI API key not configured");
            return;
        }
        
        Log.d(TAG, "Starting AssemblyAI transcription with speaker diarization");
        
        new Thread(() -> {
            try {
                // Step 1: Upload audio file
                callback.onProgress(10);
                String uploadUrl = uploadAudio(audioFile, apiKey);
                
                // Step 2: Request transcription with speaker diarization
                callback.onProgress(20);
                String transcriptId = requestTranscription(uploadUrl, apiKey);
                
                // Step 3: Poll for completion
                callback.onProgress(30);
                JSONObject result = pollForResult(transcriptId, apiKey, callback);
                
                // Step 4: Format the response with speakers
                callback.onProgress(90);
                String formattedTranscript = formatSpeakerTranscript(result);
                String segments = extractSegments(result);
                
                callback.onProgress(100);
                callback.onSuccess(formattedTranscript, segments);
                
            } catch (Exception e) {
                Log.e(TAG, "AssemblyAI transcription failed", e);
                callback.onError("Transcription failed: " + e.getMessage());
            }
        }).start();
    }
    
    private String uploadAudio(File audioFile, String apiKey) throws IOException {
        Log.d(TAG, "Uploading audio file: " + audioFile.getName());
        
        RequestBody requestBody = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        
        Request request = new Request.Builder()
            .url(UPLOAD_URL)
            .header("authorization", apiKey)
            .post(requestBody)
            .build();
        
        currentCall = client.newCall(request);
        try (Response response = currentCall.execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed: " + response.code() + " - " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);
            String uploadUrl = json.getString("upload_url");
            
            Log.d(TAG, "Audio uploaded successfully");
            return uploadUrl;
        } catch (JSONException e) {
            throw new IOException("Invalid upload response: " + e.getMessage());
        }
    }
    
    private String requestTranscription(String audioUrl, String apiKey) throws IOException, JSONException {
        Log.d(TAG, "Requesting transcription with speaker diarization");
        
        JSONObject requestJson = new JSONObject();
        requestJson.put("audio_url", audioUrl);
        
        // Enable speaker diarization - this is the key feature
        requestJson.put("speaker_labels", true);
        requestJson.put("speakers_expected", 2); // Optimize for 2-10 speakers
        
        // Language settings
        SettingsManager settings = SettingsManager.getInstance(context);
        String language = settings.getTranscriptLanguage();
        if (language != null && !language.equals("auto")) {
            requestJson.put("language_code", mapToAssemblyAILanguage(language));
        }
        
        // Enable other useful features
        requestJson.put("punctuate", true);
        requestJson.put("format_text", true);
        
        RequestBody body = RequestBody.create(
            requestJson.toString(),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(TRANSCRIPT_URL)
            .header("authorization", apiKey)
            .header("content-type", "application/json")
            .post(body)
            .build();
        
        currentCall = client.newCall(request);
        try (Response response = currentCall.execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Transcription request failed: " + response.code() + " - " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);
            String transcriptId = json.getString("id");
            
            Log.d(TAG, "Transcription requested, ID: " + transcriptId);
            return transcriptId;
        }
    }
    
    private JSONObject pollForResult(String transcriptId, String apiKey, TranscriptionCallback callback) 
            throws IOException, JSONException {
        Log.d(TAG, "Polling for transcription completion");
        
        String url = TRANSCRIPT_URL + "/" + transcriptId;
        Request request = new Request.Builder()
            .url(url)
            .header("authorization", apiKey)
            .get()
            .build();
        
        int progressStep = 30;
        
        // Poll for up to 10 minutes
        for (int attempt = 0; attempt < 120; attempt++) {
            currentCall = client.newCall(request);
            try (Response response = currentCall.execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Polling failed: " + response.code());
                }
                
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                String status = json.getString("status");
                
                Log.d(TAG, "Status: " + status + " (attempt " + (attempt + 1) + ")");
                
                if ("completed".equals(status)) {
                    Log.d(TAG, "Transcription completed successfully");
                    return json;
                } else if ("error".equals(status)) {
                    String error = json.optString("error", "Unknown error");
                    throw new IOException("Transcription failed: " + error);
                }
                
                // Update progress during processing
                if (progressStep < 85 && attempt % 10 == 0) {
                    progressStep += 5;
                    callback.onProgress(progressStep);
                }
                
                // Wait 5 seconds before next poll
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Polling interrupted");
                }
            }
        }
        
        throw new IOException("Transcription timeout - processing took too long");
    }
    
    private String formatSpeakerTranscript(JSONObject result) throws JSONException {
        StringBuilder transcript = new StringBuilder();
        
        // Get language for speaker labels
        SettingsManager settings = SettingsManager.getInstance(context);
        String language = settings.getTranscriptLanguage();
        if (language == null) language = "en";
        
        JSONArray utterances = result.optJSONArray("utterances");
        if (utterances == null) {
            // Fallback to plain text if no speaker data
            return result.optString("text", "Transcription completed");
        }
        
        String currentSpeaker = "";
        
        for (int i = 0; i < utterances.length(); i++) {
            JSONObject utterance = utterances.getJSONObject(i);
            
            String speaker = utterance.getString("speaker");
            String text = utterance.getString("text");
            double start = utterance.getDouble("start") / 1000.0; // Convert ms to seconds
            double confidence = utterance.optDouble("confidence", 0.9);
            
            // Convert speaker ID (A, B, C) to localized labels
            String speakerLabel = formatSpeakerLabel(speaker, language);
            
            if (!speakerLabel.equals(currentSpeaker)) {
                if (transcript.length() > 0) {
                    transcript.append("\n\n");
                }
                
                // Add confidence indicator
                String icon = confidence > 0.8 ? "🎯" : confidence > 0.6 ? "🗣️" : "❓";
                
                transcript.append(icon).append(" **")
                         .append(speakerLabel)
                         .append("** [").append(formatTime(start)).append("]\n");
                
                currentSpeaker = speakerLabel;
            } else {
                transcript.append(" ");
            }
            
            transcript.append(text.trim());
        }
        
        return transcript.toString();
    }
    
    private String extractSegments(JSONObject result) throws JSONException {
        // Return the utterances as segments for compatibility
        JSONArray utterances = result.optJSONArray("utterances");
        return utterances != null ? utterances.toString() : "[]";
    }
    
    private String formatSpeakerLabel(String speakerId, String language) {
        // Convert A, B, C, etc. to Speaker 1, 2, 3 with localization
        int speakerNum = 1;
        if (speakerId.length() == 1 && Character.isLetter(speakerId.charAt(0))) {
            speakerNum = speakerId.charAt(0) - 'A' + 1;
        }
        
        return SpeakerLabels.formatSpeakerLabel(language, speakerNum);
    }
    
    private String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d", minutes, secs);
    }
    
    private String mapToAssemblyAILanguage(String whisperCode) {
        // Map language codes to AssemblyAI format
        // AssemblyAI supports many European languages with speaker diarization
        switch (whisperCode) {
            case "en": return "en";
            case "es": return "es";
            case "fr": return "fr";
            case "de": return "de";
            case "it": return "it";
            case "pt": return "pt";
            case "nl": return "nl";
            case "pl": return "pl";
            case "ru": return "ru";
            case "el": return "el"; // Greek - full support
            case "sv": return "sv";
            case "da": return "da";
            case "no": return "no";
            case "fi": return "fi";
            case "cs": return "cs";
            case "sk": return "sk"; // Slovak
            case "ro": return "ro"; // Romanian  
            case "bg": return "bg"; // Bulgarian
            case "hr": return "hr"; // Croatian
            case "sl": return "sl"; // Slovenian
            case "et": return "et"; // Estonian
            case "lv": return "lv"; // Latvian
            case "lt": return "lt"; // Lithuanian
            case "hu": return "hu"; // Hungarian
            case "tr": return "tr"; // Turkish
            case "uk": return "uk"; // Ukrainian
            default: return "en"; // Fallback to English
        }
    }
    
    @Override
    public String[] getSupportedFormats() {
        return new String[]{"mp3", "mp4", "wav", "m4a", "flac", "ogg", "webm"};
    }
    
    @Override
    public int getMaxFileSizeMB() {
        return 200; // AssemblyAI supports larger files
    }
    
    @Override
    public void cancel() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }
}