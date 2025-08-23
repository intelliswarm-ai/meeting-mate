package ai.intelliswarm.meetingmate.service;

import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OpenAIService {
    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String CHAT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final OkHttpClient client;
    private final String apiKey;
    
    public OpenAIService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }
    
    // Transcribe audio using Whisper API
    public void transcribeAudio(File audioFile, TranscriptionCallback callback) {
        RequestBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("file", audioFile.getName(),
                RequestBody.create(audioFile, MediaType.parse("audio/mpeg")))
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("language", "en")
            .build();
        
        Request request = new Request.Builder()
            .url(WHISPER_API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .post(requestBody)
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Transcription failed: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        String transcript = json.getString("text");
                        
                        // Get segments if available for timestamps
                        JSONArray segments = json.optJSONArray("segments");
                        
                        callback.onSuccess(transcript, segments);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse transcription response: " + e.getMessage());
                    }
                } else {
                    callback.onError("Transcription failed with code: " + response.code());
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
}