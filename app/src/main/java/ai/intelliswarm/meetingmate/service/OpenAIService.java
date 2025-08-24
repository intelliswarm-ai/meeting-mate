package ai.intelliswarm.meetingmate.service;

import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OpenAIService {
    private static final String TAG = "OpenAIService";
    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String CHAT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final OkHttpClient client;
    private final String apiKey;
    
    public OpenAIService(String apiKey) {
        this.apiKey = apiKey;
        Log.d(TAG, "OpenAIService created with API key: " + (apiKey != null && !apiKey.isEmpty() ? 
            apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "EMPTY"));
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }
    
    // Transcribe audio using Whisper API (defaults to auto-detect language)
    public void transcribeAudio(File audioFile, TranscriptionCallback callback) {
        transcribeAudio(audioFile, null, callback); // null = auto-detect
    }
    
    // Transcribe audio using Whisper API with specified language
    public void transcribeAudio(File audioFile, String language, TranscriptionCallback callback) {
        Log.d(TAG, "Starting audio transcription for file: " + audioFile.getName() + " (size: " + audioFile.length() + " bytes)");
        Log.d(TAG, "Language setting: " + (language != null ? language : "auto-detect"));
        
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("file", audioFile.getName(),
                RequestBody.create(audioFile, MediaType.parse("audio/mpeg")))
            .addFormDataPart("response_format", "verbose_json");
        
        // Add language parameter only if specified (null = auto-detect)
        if (language != null && !language.isEmpty() && !language.equals("auto")) {
            requestBodyBuilder.addFormDataPart("language", language);
        }
        
        RequestBody requestBody = requestBodyBuilder.build();
        
        Request request = new Request.Builder()
            .url(WHISPER_API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .post(requestBody)
            .build();
            
        Log.d(TAG, "Sending transcription request to OpenAI API");
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Transcription request failed: " + e.getMessage(), e);
                callback.onError("Transcription failed: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "Received transcription response with code: " + response.code());
                
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Transcription response received, parsing JSON...");
                        JSONObject json = new JSONObject(responseBody);
                        String transcript = json.getString("text");
                        
                        Log.d(TAG, "Transcription successful, text length: " + transcript.length());
                        
                        // Get segments if available for timestamps
                        JSONArray segments = json.optJSONArray("segments");
                        
                        callback.onSuccess(transcript, segments);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse transcription JSON response", e);
                        callback.onError("Failed to parse transcription response: " + e.getMessage());
                    }
                } else {
                    String errorMessage = "Transcription failed with code: " + response.code();
                    if (response.code() == 401) {
                        errorMessage += " - Invalid or missing API key. Please check your OpenAI API key in settings.";
                        Log.e(TAG, "401 Unauthorized - API key issue");
                    }
                    try {
                        String errorBody = response.body().string();
                        if (!errorBody.isEmpty()) {
                            Log.e(TAG, "API Error Body: " + errorBody);
                            errorMessage += " Error details: " + errorBody;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not read error body", e);
                    }
                    Log.e(TAG, "Transcription failed: " + errorMessage);
                    callback.onError(errorMessage);
                }
            }
        });
    }
    
    // Generate meeting summary using GPT
    public void generateSummary(String transcript, String meetingTitle, SummaryCallback callback) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", "gpt-4o-mini");
            
            JSONArray messages = new JSONArray();
            
            // System message
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a professional meeting assistant. Create concise, well-structured meeting summaries with key points, action items, and decisions made.");
            messages.put(systemMessage);
            
            // User message with transcript
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", "Please create a comprehensive meeting summary for the following transcript. " +
                "Meeting Title: " + meetingTitle + "\n\n" +
                "Format the summary with:\n" +
                "1. **Overview** - Brief meeting overview (2-3 sentences)\n" +
                "2. **Key Discussion Points** - Main topics discussed\n" +
                "3. **Decisions Made** - Any decisions reached\n" +
                "4. **Action Items** - Tasks assigned with owners if mentioned\n" +
                "5. **Next Steps** - Follow-up actions or future meetings\n\n" +
                "Transcript:\n" + transcript);
            messages.put(userMessage);
            
            requestJson.put("messages", messages);
            requestJson.put("temperature", 0.7);
            requestJson.put("max_tokens", 1000);
            
            RequestBody body = RequestBody.create(requestJson.toString(), JSON);
            
            Request request = new Request.Builder()
                .url(CHAT_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Summary generation failed: " + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);
                            JSONArray choices = json.getJSONArray("choices");
                            
                            if (choices.length() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                JSONObject message = choice.getJSONObject("message");
                                String summary = message.getString("content");
                                
                                callback.onSuccess(summary);
                            } else {
                                callback.onError("No summary generated");
                            }
                        } catch (JSONException e) {
                            callback.onError("Failed to parse summary response: " + e.getMessage());
                        }
                    } else {
                        callback.onError("Summary generation failed with code: " + response.code());
                    }
                }
            });
            
        } catch (JSONException e) {
            callback.onError("Failed to create request: " + e.getMessage());
        }
    }
    
    // Generate meeting title from transcript
    public void generateTitle(String transcriptSnippet, TitleCallback callback) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", "gpt-4o-mini");
            
            JSONArray messages = new JSONArray();
            
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Generate a concise, descriptive meeting title (max 50 characters) based on the transcript snippet provided.");
            messages.put(systemMessage);
            
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", "Generate a meeting title for this transcript snippet:\n" + transcriptSnippet);
            messages.put(userMessage);
            
            requestJson.put("messages", messages);
            requestJson.put("temperature", 0.5);
            requestJson.put("max_tokens", 20);
            
            RequestBody body = RequestBody.create(requestJson.toString(), JSON);
            
            Request request = new Request.Builder()
                .url(CHAT_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Title generation failed: " + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);
                            JSONArray choices = json.getJSONArray("choices");
                            
                            if (choices.length() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                JSONObject message = choice.getJSONObject("message");
                                String title = message.getString("content").trim();
                                
                                // Remove quotes if present
                                title = title.replaceAll("^\"|\"$", "");
                                
                                callback.onSuccess(title);
                            } else {
                                callback.onError("No title generated");
                            }
                        } catch (JSONException e) {
                            callback.onError("Failed to parse title response: " + e.getMessage());
                        }
                    } else {
                        callback.onError("Title generation failed with code: " + response.code());
                    }
                }
            });
            
        } catch (JSONException e) {
            callback.onError("Failed to create request: " + e.getMessage());
        }
    }
    
    // Validate API key by making a simple API call
    public void validateApiKey(ApiKeyValidationCallback callback) {
        Log.d(TAG, "Starting API key validation");
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", "gpt-4o-mini");
            
            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", "Test");
            messages.put(userMessage);
            
            requestJson.put("messages", messages);
            requestJson.put("max_tokens", 1);
            
            RequestBody body = RequestBody.create(requestJson.toString(), JSON);
            
            Request request = new Request.Builder()
                .url(CHAT_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
                
            Log.d(TAG, "Sending API key validation request to OpenAI");
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API key validation request failed: " + e.getMessage(), e);
                    callback.onValidationResult(false, "Network error: " + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "API key validation response received with code: " + response.code());
                    
                    if (response.isSuccessful()) {
                        Log.d(TAG, "API key validation successful");
                        callback.onValidationResult(true, "API key is valid");
                    } else {
                        String errorMessage;
                        if (response.code() == 401) {
                            errorMessage = "Invalid API key";
                            Log.e(TAG, "API key validation failed - 401 Unauthorized");
                        } else if (response.code() == 429) {
                            errorMessage = "API key is valid but rate limited";
                            Log.w(TAG, "API key validation rate limited but key is valid");
                            callback.onValidationResult(true, errorMessage);
                            return;
                        } else {
                            errorMessage = "API error (code: " + response.code() + ")";
                            Log.e(TAG, "API key validation failed with code: " + response.code());
                        }
                        
                        try {
                            String errorBody = response.body().string();
                            if (!errorBody.isEmpty()) {
                                Log.e(TAG, "API key validation error body: " + errorBody);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Could not read validation error body", e);
                        }
                        
                        callback.onValidationResult(false, errorMessage);
                    }
                }
            });
            
        } catch (JSONException e) {
            callback.onValidationResult(false, "Failed to create validation request: " + e.getMessage());
        }
    }
    
    // Callback interfaces
    public interface TranscriptionCallback {
        void onSuccess(String transcript, JSONArray segments);
        void onError(String error);
    }
    
    public interface SummaryCallback {
        void onSuccess(String summary);
        void onError(String error);
    }
    
    public interface TitleCallback {
        void onSuccess(String title);
        void onError(String error);
    }
    
    public interface ApiKeyValidationCallback {
        void onValidationResult(boolean isValid, String message);
    }
}