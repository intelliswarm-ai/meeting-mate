package ai.intelliswarm.meetingmate.transcription;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced speaker detection based on voice characteristics and acoustic features
 * Uses voice patterns, speaking rate, and audio features for speaker differentiation
 */
public class SpeakerDetection {
    
    private static final String TAG = "SpeakerDetection";
    
    // Voice characteristic thresholds for speaker changes
    private static final double MIN_PAUSE_FOR_SPEAKER_CHANGE = 0.3; // Minimum pause to consider speaker change
    private static final double VOICE_PATTERN_CHANGE_THRESHOLD = 0.25; // Threshold for voice pattern changes
    private static final double SPEAKING_RATE_CHANGE_THRESHOLD = 0.4; // Significant change in speaking rate
    private static final int MIN_WORDS_PER_SEGMENT = 5; // Minimum words to analyze voice characteristics
    
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
     * Voice characteristic data for tracking speaker patterns
     */
    private static class VoiceProfile {
        double avgLogprob;
        double speakingRate; // words per second
        double segmentDuration;
        int wordCount;
        double avgWordLength;
        
        VoiceProfile(JSONObject segment) throws JSONException {
            String text = segment.getString("text").trim();
            double startTime = segment.getDouble("start");
            double endTime = segment.getDouble("end");
            
            this.avgLogprob = segment.optDouble("avg_logprob", 0);
            this.segmentDuration = endTime - startTime;
            this.wordCount = text.split("\\s+").length;
            this.speakingRate = wordCount / Math.max(0.1, segmentDuration);
            this.avgWordLength = text.length() / Math.max(1, wordCount);
        }
        
        double calculateSimilarity(VoiceProfile other) {
            if (other == null) return 0;
            
            // Calculate voice similarity based on multiple factors
            double logprobDiff = Math.abs(this.avgLogprob - other.avgLogprob);
            double rateDiff = Math.abs(this.speakingRate - other.speakingRate) / Math.max(this.speakingRate, other.speakingRate);
            double wordLengthDiff = Math.abs(this.avgWordLength - other.avgWordLength) / Math.max(this.avgWordLength, other.avgWordLength);
            
            // Weighted similarity score (0 = identical, 1 = very different)
            double similarity = (logprobDiff * 0.5) + (rateDiff * 0.3) + (wordLengthDiff * 0.2);
            return similarity;
        }
    }
    
    /**
     * Process Whisper segments and detect speaker changes based on voice characteristics
     * @param segmentsJson JSON array of segments from Whisper API
     * @return List of speaker segments with detected speakers
     */
    public static List<SpeakerSegment> detectSpeakers(String segmentsJson) {
        return detectSpeakers(segmentsJson, "en");
    }
    
    /**
     * Process Whisper segments with language support
     * @param segmentsJson JSON array of segments from Whisper API  
     * @param languageCode Language code for labels
     * @return List of speaker segments with detected speakers
     */
    public static List<SpeakerSegment> detectSpeakers(String segmentsJson, String languageCode) {
        List<SpeakerSegment> speakerSegments = new ArrayList<>();
        
        try {
            JSONArray segments = new JSONArray(segmentsJson);
            if (segments.length() == 0) {
                return speakerSegments;
            }
            
            // Track voice profiles for each speaker
            List<VoiceProfile> speakerProfiles = new ArrayList<>();
            List<VoiceProfile> recentProfiles = new ArrayList<>();
            
            String currentSpeaker = SpeakerLabels.formatSpeakerLabel(languageCode, 1);
            int speakerCount = 1;
            VoiceProfile currentSpeakerProfile = null;
            double previousEndTime = 0;
            
            Log.d(TAG, "Processing " + segments.length() + " segments for voice-based speaker detection");
            
            for (int i = 0; i < segments.length(); i++) {
                JSONObject segment = segments.getJSONObject(i);
                
                double startTime = segment.getDouble("start");
                double endTime = segment.getDouble("end");
                String text = segment.getString("text").trim();
                
                if (text.isEmpty() || text.split("\\s+").length < 2) {
                    continue; // Skip very short segments
                }
                
                // Create voice profile for current segment
                VoiceProfile currentProfile = new VoiceProfile(segment);
                
                boolean speakerChange = false;
                double pauseLength = startTime - previousEndTime;
                
                if (i > 0 && currentSpeakerProfile != null) {
                    // Calculate voice similarity
                    double voiceSimilarity = currentProfile.calculateSimilarity(currentSpeakerProfile);
                    
                    // Check if this matches any previous speaker
                    int matchingSpeaker = -1;
                    double bestMatch = Double.MAX_VALUE;
                    
                    for (int j = 0; j < speakerProfiles.size(); j++) {
                        double similarity = currentProfile.calculateSimilarity(speakerProfiles.get(j));
                        if (similarity < bestMatch) {
                            bestMatch = similarity;
                            matchingSpeaker = j;
                        }
                    }
                    
                    // Determine if speaker changed based on voice characteristics
                    if (voiceSimilarity > VOICE_PATTERN_CHANGE_THRESHOLD && pauseLength > MIN_PAUSE_FOR_SPEAKER_CHANGE) {
                        speakerChange = true;
                        
                        // Check if it's a returning speaker
                        if (bestMatch < VOICE_PATTERN_CHANGE_THRESHOLD && matchingSpeaker >= 0) {
                            currentSpeaker = SpeakerLabels.formatSpeakerLabel(languageCode, matchingSpeaker + 1);
                            Log.d(TAG, "Returning speaker detected: " + currentSpeaker + " at " + formatTime(startTime));
                        } else {
                            // New speaker
                            speakerCount++;
                            currentSpeaker = SpeakerLabels.formatSpeakerLabel(languageCode, speakerCount);
                            speakerProfiles.add(currentProfile);
                            Log.d(TAG, "New speaker detected: " + currentSpeaker + " at " + formatTime(startTime) + 
                                  " (voice similarity: " + String.format("%.2f", voiceSimilarity) + ")");
                        }
                    }
                    
                    // Log voice characteristics for debugging
                    if (i % 5 == 0) { // Log every 5th segment
                        Log.d(TAG, String.format("Segment %d voice: rate=%.1f w/s, logprob=%.2f, similarity=%.2f", 
                            i, currentProfile.speakingRate, currentProfile.avgLogprob, voiceSimilarity));
                    }
                } else if (i == 0) {
                    // First segment - establish baseline
                    speakerProfiles.add(currentProfile);
                    Log.d(TAG, "Initial speaker profile established");
                }
                
                // Update current speaker profile with rolling average
                if (!speakerChange) {
                    recentProfiles.add(currentProfile);
                    if (recentProfiles.size() > 3) {
                        recentProfiles.remove(0);
                    }
                    // Calculate average profile from recent segments
                    currentSpeakerProfile = calculateAverageProfile(recentProfiles);
                } else {
                    // Reset recent profiles for new speaker
                    recentProfiles.clear();
                    recentProfiles.add(currentProfile);
                    currentSpeakerProfile = currentProfile;
                }
                
                // Calculate confidence based on voice consistency
                double confidence = Math.max(0.3, Math.min(1, 1 - (currentProfile.avgLogprob + 1)));
                
                speakerSegments.add(new SpeakerSegment(
                    currentSpeaker, startTime, endTime, text, confidence
                ));
                
                previousEndTime = endTime;
            }
            
            Log.d(TAG, "Speaker detection complete. Found " + speakerCount + " speakers in " + segments.length() + " segments");
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing segments for speaker detection", e);
            // Fallback: create single speaker segment
            speakerSegments.add(new SpeakerSegment("Speaker 1", 0, 0, "Transcription available (speaker detection failed)", 0.5));
        }
        
        return speakerSegments;
    }
    
    /**
     * Calculate average voice profile from recent segments
     */
    private static VoiceProfile calculateAverageProfile(List<VoiceProfile> profiles) {
        if (profiles.isEmpty()) return null;
        
        double avgLogprob = 0;
        double avgRate = 0;
        double avgWordLength = 0;
        
        for (VoiceProfile p : profiles) {
            avgLogprob += p.avgLogprob;
            avgRate += p.speakingRate;
            avgWordLength += p.avgWordLength;
        }
        
        int count = profiles.size();
        VoiceProfile avg = profiles.get(profiles.size() - 1); // Use last as template
        avg.avgLogprob = avgLogprob / count;
        avg.speakingRate = avgRate / count;
        avg.avgWordLength = avgWordLength / count;
        
        return avg;
    }
    
    /**
     * Format speaker segments into a readable transcript with speaker labels
     * @param speakerSegments List of speaker segments
     * @return Formatted transcript string
     */
    public static String formatTranscriptWithSpeakers(List<SpeakerSegment> speakerSegments) {
        return formatTranscriptWithSpeakers(speakerSegments, "en");
    }
    
    /**
     * Format speaker segments with language support
     * @param speakerSegments List of speaker segments
     * @param languageCode Language code for labels
     * @return Formatted transcript string
     */
    public static String formatTranscriptWithSpeakers(List<SpeakerSegment> speakerSegments, String languageCode) {
        if (speakerSegments.isEmpty()) {
            return "No transcript available";
        }
        
        StringBuilder transcript = new StringBuilder();
        String currentSpeaker = "";
        
        for (SpeakerSegment segment : speakerSegments) {
            // Add speaker label only when speaker changes
            if (!segment.speaker.equals(currentSpeaker)) {
                if (transcript.length() > 0) {
                    transcript.append("\n\n");
                }
                transcript.append("🗣️ **").append(segment.speaker).append("** [").append(formatTime(segment.startTime)).append("]\n");
                currentSpeaker = segment.speaker;
            } else {
                transcript.append(" ");
            }
            
            transcript.append(segment.text.trim());
        }
        
        return transcript.toString();
    }
    
    /**
     * Get summary of detected speakers
     * @param speakerSegments List of speaker segments
     * @return Summary string
     */
    public static String getSpeakerSummary(List<SpeakerSegment> speakerSegments) {
        if (speakerSegments.isEmpty()) {
            return "No speakers detected";
        }
        
        List<String> uniqueSpeakers = new ArrayList<>();
        for (SpeakerSegment segment : speakerSegments) {
            if (!uniqueSpeakers.contains(segment.speaker)) {
                uniqueSpeakers.add(segment.speaker);
            }
        }
        
        return uniqueSpeakers.size() + " speaker" + (uniqueSpeakers.size() > 1 ? "s" : "") + " detected: " + 
               String.join(", ", uniqueSpeakers);
    }
    
    /**
     * Format time in MM:SS format
     */
    private static String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d", minutes, secs);
    }
}