package ai.intelliswarm.meetingmate.transcription;

import android.content.Context;
import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Generate meeting summaries, key points, and action items using OpenAI
 */
public class SummaryGenerator {
    
    private static final String TAG = "SummaryGenerator";
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    
    private final Context context;
    private final OkHttpClient client;
    
    public SummaryGenerator(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Meeting summary data
     */
    public static class MeetingSummary {
        public String summary;
        public String keyPoints;
        public String actionItems;
        public String decisions;
        public String nextSteps;
        
        public boolean isEmpty() {
            return (summary == null || summary.trim().isEmpty()) &&
                   (keyPoints == null || keyPoints.trim().isEmpty()) &&
                   (actionItems == null || actionItems.trim().isEmpty());
        }
    }
    
    /**
     * Generate comprehensive meeting summary from transcript
     */
    public void generateSummary(String transcript, String meetingTitle, SummaryCallback callback) {
        SettingsManager settings = SettingsManager.getInstance(context);
        String apiKey = settings.getOpenAIApiKey();
        
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("OpenAI API key not configured");
            return;
        }
        
        if (transcript == null || transcript.trim().isEmpty()) {
            callback.onError("Transcript is empty");
            return;
        }
        
        // Create the prompt for summary generation
        String prompt = createSummaryPrompt(transcript, meetingTitle);
        
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.3); // Lower temperature for more consistent summaries
            
            JSONArray messages = new JSONArray();
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a professional meeting assistant that creates concise, actionable meeting summaries.");
            messages.put(systemMessage);
            
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.put(userMessage);
            
            requestBody.put("messages", messages);
            
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                .url(OPENAI_CHAT_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Summary generation failed", e);
                    callback.onError("Network error: " + e.getMessage());
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
                                String content = message.getString("content");
                                
                                MeetingSummary summary = parseSummaryResponse(content);
                                callback.onSuccess(summary);
                            } else {
                                callback.onError("No summary generated");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing summary response", e);
                            callback.onError("Failed to parse response: " + e.getMessage());
                        }
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        Log.e(TAG, "Summary API error: " + response.code() + " - " + errorBody);
                        callback.onError("API Error: " + response.code());
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating summary request", e);
            callback.onError("Failed to create request: " + e.getMessage());
        }
    }
    
    /**
     * Create the prompt for summary generation
     */
    private String createSummaryPrompt(String transcript, String meetingTitle) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Please analyze the following meeting transcript and provide a comprehensive summary in the exact format below:\n\n");
        
        if (meetingTitle != null && !meetingTitle.trim().isEmpty()) {
            prompt.append("Meeting: ").append(meetingTitle).append("\n\n");
        }
        
        prompt.append("TRANSCRIPT:\n");
        prompt.append(transcript);
        prompt.append("\n\n");
        
        prompt.append("Please provide the analysis in this exact format:\n\n");
        prompt.append("SUMMARY:\n");
        prompt.append("[Provide a concise 2-3 sentence summary of the meeting's main purpose and outcomes]\n\n");
        
        prompt.append("KEY POINTS:\n");
        prompt.append("• [Important point 1]\n");
        prompt.append("• [Important point 2]\n");
        prompt.append("• [Important point 3]\n");
        prompt.append("[Add more points as needed]\n\n");
        
        prompt.append("ACTION ITEMS:\n");
        prompt.append("• [Person/Team]: [Specific action to take] - [Due date if mentioned]\n");
        prompt.append("• [Person/Team]: [Specific action to take] - [Due date if mentioned]\n");
        prompt.append("[Add more action items as needed]\n\n");
        
        prompt.append("DECISIONS MADE:\n");
        prompt.append("• [Decision 1]\n");
        prompt.append("• [Decision 2]\n");
        prompt.append("[Add more decisions if any were made]\n\n");
        
        prompt.append("NEXT STEPS:\n");
        prompt.append("• [Next step 1]\n");
        prompt.append("• [Next step 2]\n");
        prompt.append("[Add more next steps as needed]\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse the structured summary response
     */
    private MeetingSummary parseSummaryResponse(String content) {
        MeetingSummary summary = new MeetingSummary();
        
        try {
            // Split content by sections
            String[] sections = content.split("(?=SUMMARY:|KEY POINTS:|ACTION ITEMS:|DECISIONS MADE:|NEXT STEPS:)");
            
            for (String section : sections) {
                section = section.trim();
                
                if (section.startsWith("SUMMARY:")) {
                    summary.summary = extractSectionContent(section, "SUMMARY:");
                } else if (section.startsWith("KEY POINTS:")) {
                    summary.keyPoints = extractSectionContent(section, "KEY POINTS:");
                } else if (section.startsWith("ACTION ITEMS:")) {
                    summary.actionItems = extractSectionContent(section, "ACTION ITEMS:");
                } else if (section.startsWith("DECISIONS MADE:")) {
                    summary.decisions = extractSectionContent(section, "DECISIONS MADE:");
                } else if (section.startsWith("NEXT STEPS:")) {
                    summary.nextSteps = extractSectionContent(section, "NEXT STEPS:");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing summary response", e);
            // Fallback: use entire content as summary
            summary.summary = content;
        }
        
        // Ensure we have at least some content
        if (summary.isEmpty()) {
            summary.summary = content.length() > 0 ? content : "Summary generation completed";
        }
        
        return summary;
    }
    
    /**
     * Extract content for a specific section
     */
    private String extractSectionContent(String section, String header) {
        if (!section.startsWith(header)) return null;
        
        String content = section.substring(header.length()).trim();
        
        // Remove placeholder text in brackets
        content = content.replaceAll("\\[.*?\\]", "").trim();
        
        // Clean up empty bullet points
        String[] lines = content.split("\n");
        StringBuilder cleaned = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.equals("•") && !line.matches("•\\s*$")) {
                if (cleaned.length() > 0) {
                    cleaned.append("\n");
                }
                cleaned.append(line);
            }
        }
        
        return cleaned.toString().trim();
    }
    
    /**
     * Callback interface for summary generation
     */
    public interface SummaryCallback {
        void onSuccess(MeetingSummary summary);
        void onError(String error);
    }
}