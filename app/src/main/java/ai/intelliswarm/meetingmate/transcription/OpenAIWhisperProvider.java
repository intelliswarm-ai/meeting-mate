package ai.intelliswarm.meetingmate.transcription;

import android.content.Context;
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OpenAIWhisperProvider implements TranscriptionProvider {
    
    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final Context context;
    private final OkHttpClient client;
    private Call currentCall;
    
    public OpenAIWhisperProvider(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();
    }
    
    @Override
    public ProviderType getType() {
        return ProviderType.OPENAI_WHISPER;
    }
    
    @Override
    public boolean isConfigured() {
        SettingsManager settings = SettingsManager.getInstance(context);
        return settings.hasOpenAIApiKey();
    }
    
    @Override
    public String getConfigurationRequirement() {
        return "OpenAI API Key required. Get one from https://platform.openai.com/api-keys";
    }
    
    @Override
    public void transcribe(File audioFile, TranscriptionCallback callback) {
        SettingsManager settings = SettingsManager.getInstance(context);
        String apiKey = settings.getOpenAIApiKey();
        
        if (apiKey.isEmpty()) {
            callback.onError("OpenAI API key not configured");
            return;
        }
        
        callback.onProgress(10); // Starting upload
        
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("file", audioFile.getName(),
                RequestBody.create(audioFile, MediaType.parse("audio/mpeg")))
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .addFormDataPart("timestamp_granularities[]", "word");
        
        // Add language parameter only if not auto-detect
        String language = settings.getTranscriptLanguage();
        if (language != null && !language.isEmpty() && !language.equals("auto")) {
            requestBodyBuilder.addFormDataPart("language", language);
        }
        
        RequestBody requestBody = requestBodyBuilder.build();
        
        Request request = new Request.Builder()
            .url(WHISPER_API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .post(requestBody)
            .build();
        
        callback.onProgress(30); // Upload started
        
        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    callback.onError("Transcription cancelled");
                } else {
                    callback.onError("Network error: " + e.getMessage());
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                callback.onProgress(80); // Processing response
                
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        String transcript = json.getString("text");
                        
                        // Get segments for timestamps and speaker detection
                        String segments = "";
                        JSONArray segmentsArray = json.optJSONArray("segments");
                        if (segmentsArray != null) {
                            segments = segmentsArray.toString();
                            
                            // Apply advanced word-level speaker detection with language support
                            try {
                                SettingsManager settingsManager = SettingsManager.getInstance(context);
                                String transcriptLanguage = settingsManager.getTranscriptLanguage();
                                
                                // Use enhanced segment-based detection with language support
                                java.util.List<AdvancedSpeakerDetection.EnhancedSpeakerSegment> enhancedSegments = 
                                    AdvancedSpeakerDetection.detectSpeakersAdvanced(segments, context, transcriptLanguage);
                                
                                if (!enhancedSegments.isEmpty()) {
                                    String enhancedTranscript = AdvancedSpeakerDetection.formatEnhancedTranscript(enhancedSegments, transcriptLanguage);
                                    transcript = enhancedTranscript;
                                    android.util.Log.d("OpenAIWhisperProvider", "Using enhanced speaker detection");
                                } else {
                                    // Fallback to basic detection with language support
                                    java.util.List<SpeakerDetection.SpeakerSegment> speakerSegments = 
                                        SpeakerDetection.detectSpeakers(segments, transcriptLanguage);
                                    
                                    if (!speakerSegments.isEmpty()) {
                                        String speakerTranscript = SpeakerDetection.formatTranscriptWithSpeakers(speakerSegments, transcriptLanguage);
                                        transcript = speakerTranscript;
                                        android.util.Log.d("OpenAIWhisperProvider", "Using basic voice-based detection");
                                    }
                                }
                            } catch (Exception e) {
                                android.util.Log.w("OpenAIWhisperProvider", "Speaker detection failed, using plain transcript", e);
                            }
                        }
                        
                        callback.onProgress(100);
                        callback.onSuccess(transcript, segments);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse response: " + e.getMessage());
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    
                    try {
                        JSONObject error = new JSONObject(errorBody);
                        JSONObject errorDetails = error.optJSONObject("error");
                        if (errorDetails != null) {
                            String message = errorDetails.optString("message", "API Error");
                            String type = errorDetails.optString("type", "");
                            callback.onError("OpenAI Error: " + message + " (" + type + ")");
                        } else {
                            callback.onError("API Error: " + response.code());
                        }
                    } catch (JSONException e) {
                        callback.onError("API Error: " + response.code() + " - " + errorBody);
                    }
                }
            }
        });
    }
    
    @Override
    public String[] getSupportedFormats() {
        return new String[]{"mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm"};
    }
    
    @Override
    public int getMaxFileSizeMB() {
        return 25; // OpenAI's limit
    }
    
    @Override
    public void cancel() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }
}