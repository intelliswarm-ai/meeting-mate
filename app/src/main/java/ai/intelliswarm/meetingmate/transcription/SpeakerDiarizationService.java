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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for speaker diarization using various approaches
 * Since OpenAI Whisper doesn't support speaker diarization natively,
 * this class provides alternative solutions
 */
public class SpeakerDiarizationService {
    
    private static final String TAG = "SpeakerDiarization";
    
    /**
     * Options for speaker diarization
     */
    public enum DiarizationMethod {
        NONE("No speaker detection"),
        SIMPLE_PAUSE("Simple pause-based detection"),
        VOICE_PATTERN("Voice pattern analysis"),
        ASSEMBLYAI("AssemblyAI API (requires key)"),
        PYANNOTE("Pyannote (server required)");
        
        private final String description;
        
        DiarizationMethod(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Speaker segment with timing and text
     */
    public static class SpeakerSegment {
        public String speaker;
        public double startTime;
        public double endTime;
        public String text;
        public double confidence;
        
        public SpeakerSegment(String speaker, double startTime, double endTime, String text, double confidence) {
            this.speaker = speaker;
            this.startTime = startTime;
            this.endTime = endTime;
            this.text = text;
            this.confidence = confidence;
        }
    }
    
    /**
     * Use AssemblyAI for speaker diarization (more accurate than Whisper alone)
     * AssemblyAI has built-in speaker diarization
     */
    public static class AssemblyAIDiarization {
        private static final String UPLOAD_URL = "https://api.assemblyai.com/v2/upload";
        private static final String TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript";
        private final OkHttpClient client;
        private final String apiKey;
        
        public AssemblyAIDiarization(String apiKey) {
            this.apiKey = apiKey;
            this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        }
        
        public List<SpeakerSegment> transcribeWithSpeakers(File audioFile, String languageCode) throws IOException, JSONException {
            // Step 1: Upload audio file
            String uploadUrl = uploadAudio(audioFile);
            
            // Step 2: Request transcription with speaker diarization
            String transcriptId = requestTranscription(uploadUrl, languageCode);
            
            // Step 3: Poll for results
            JSONObject result = pollForResult(transcriptId);
            
            // Step 4: Parse speaker segments
            return parseSpeakerSegments(result, languageCode);
        }
        
        private String uploadAudio(File audioFile) throws IOException {
            RequestBody requestBody = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
            
            Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .header("authorization", apiKey)
                .post(requestBody)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Upload failed: " + response.code());
                }
                
                JSONObject json = new JSONObject(response.body().string());
                return json.getString("upload_url");
            } catch (JSONException e) {
                throw new IOException("Invalid response: " + e.getMessage());
            }
        }
        
        private String requestTranscription(String audioUrl, String languageCode) throws IOException, JSONException {
            JSONObject requestJson = new JSONObject();
            requestJson.put("audio_url", audioUrl);
            requestJson.put("speaker_labels", true); // Enable speaker diarization
            
            // Map language code if needed
            if (languageCode != null && !languageCode.equals("auto")) {
                requestJson.put("language_code", mapLanguageCode(languageCode));
            }
            
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
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Transcription request failed: " + response.code());
                }
                
                JSONObject json = new JSONObject(response.body().string());
                return json.getString("id");
            }
        }
        
        private JSONObject pollForResult(String transcriptId) throws IOException, JSONException {
            String url = TRANSCRIPT_URL + "/" + transcriptId;
            
            Request request = new Request.Builder()
                .url(url)
                .header("authorization", apiKey)
                .get()
                .build();
            
            // Poll for up to 5 minutes
            for (int i = 0; i < 60; i++) {
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Poll failed: " + response.code());
                    }
                    
                    JSONObject json = new JSONObject(response.body().string());
                    String status = json.getString("status");
                    
                    if ("completed".equals(status)) {
                        return json;
                    } else if ("error".equals(status)) {
                        throw new IOException("Transcription failed: " + json.optString("error"));
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
            
            throw new IOException("Transcription timeout");
        }
        
        private List<SpeakerSegment> parseSpeakerSegments(JSONObject result, String languageCode) throws JSONException {
            List<SpeakerSegment> segments = new ArrayList<>();
            
            JSONArray utterances = result.optJSONArray("utterances");
            if (utterances == null) {
                // Fallback to words with speaker labels
                JSONArray words = result.optJSONArray("words");
                if (words != null) {
                    return parseWordsWithSpeakers(words, languageCode);
                }
                return segments;
            }
            
            for (int i = 0; i < utterances.length(); i++) {
                JSONObject utterance = utterances.getJSONObject(i);
                
                String speaker = utterance.getString("speaker");
                String text = utterance.getString("text");
                double start = utterance.getDouble("start") / 1000.0; // Convert ms to seconds
                double end = utterance.getDouble("end") / 1000.0;
                double confidence = utterance.optDouble("confidence", 0.8);
                
                // Format speaker label with language support
                String speakerLabel = formatSpeakerLabel(speaker, languageCode);
                
                segments.add(new SpeakerSegment(speakerLabel, start, end, text, confidence));
            }
            
            return segments;
        }
        
        private List<SpeakerSegment> parseWordsWithSpeakers(JSONArray words, String languageCode) throws JSONException {
            List<SpeakerSegment> segments = new ArrayList<>();
            
            if (words.length() == 0) return segments;
            
            String currentSpeaker = null;
            StringBuilder currentText = new StringBuilder();
            double segmentStart = 0;
            double segmentEnd = 0;
            
            for (int i = 0; i < words.length(); i++) {
                JSONObject word = words.getJSONObject(i);
                
                String speaker = word.optString("speaker", "A");
                String text = word.getString("text");
                double start = word.getDouble("start") / 1000.0;
                double end = word.getDouble("end") / 1000.0;
                
                if (currentSpeaker == null) {
                    currentSpeaker = speaker;
                    segmentStart = start;
                }
                
                if (!speaker.equals(currentSpeaker)) {
                    // Speaker changed, save current segment
                    if (currentText.length() > 0) {
                        String speakerLabel = formatSpeakerLabel(currentSpeaker, languageCode);
                        segments.add(new SpeakerSegment(speakerLabel, segmentStart, segmentEnd, 
                            currentText.toString().trim(), 0.8));
                    }
                    
                    // Start new segment
                    currentSpeaker = speaker;
                    currentText = new StringBuilder();
                    segmentStart = start;
                }
                
                currentText.append(" ").append(text);
                segmentEnd = end;
            }
            
            // Add final segment
            if (currentText.length() > 0) {
                String speakerLabel = formatSpeakerLabel(currentSpeaker, languageCode);
                segments.add(new SpeakerSegment(speakerLabel, segmentStart, segmentEnd, 
                    currentText.toString().trim(), 0.8));
            }
            
            return segments;
        }
        
        private String formatSpeakerLabel(String speaker, String languageCode) {
            // Convert A, B, C to Speaker 1, 2, 3 with localization
            int speakerNum = 1;
            if (speaker.length() == 1 && Character.isLetter(speaker.charAt(0))) {
                speakerNum = speaker.charAt(0) - 'A' + 1;
            } else {
                try {
                    speakerNum = Integer.parseInt(speaker.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    speakerNum = 1;
                }
            }
            
            return SpeakerLabels.formatSpeakerLabel(languageCode, speakerNum);
        }
        
        private String mapLanguageCode(String whisperCode) {
            // Map Whisper language codes to AssemblyAI codes
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
                case "zh": return "zh";
                case "ja": return "ja";
                case "ko": return "ko";
                default: return "en"; // Fallback to English
            }
        }
    }
    
    /**
     * Format speaker segments into transcript
     */
    public static String formatTranscript(List<SpeakerSegment> segments) {
        if (segments.isEmpty()) return "";
        
        StringBuilder transcript = new StringBuilder();
        String currentSpeaker = "";
        
        for (SpeakerSegment segment : segments) {
            if (!segment.speaker.equals(currentSpeaker)) {
                if (transcript.length() > 0) {
                    transcript.append("\n\n");
                }
                transcript.append("**").append(segment.speaker).append("** [")
                         .append(formatTime(segment.startTime)).append("]\n");
                currentSpeaker = segment.speaker;
            } else {
                transcript.append(" ");
            }
            
            transcript.append(segment.text);
        }
        
        return transcript.toString();
    }
    
    private static String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d", minutes, secs);
    }
    
    /**
     * Simple implementation without external services
     * This is what we'll use by default since OpenAI doesn't support speaker diarization
     */
    public static String addSimpleSpeakerLabels(String transcript, String languageCode) {
        // For now, just return the transcript as-is
        // We could add simple paragraph-based speaker detection here
        return transcript;
    }
}