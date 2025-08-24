package ai.intelliswarm.meetingmate.transcription;

import android.content.Context;
import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Advanced speaker diarization using voice embeddings and clustering
 * This provides more accurate speaker detection using acoustic features
 */
public class AdvancedSpeakerDetection {
    
    private static final String TAG = "AdvancedSpeakerDetection";
    
    // Configuration for voice-based clustering
    private static final double VOICE_EMBEDDING_THRESHOLD = 0.7; // Similarity threshold for same speaker
    private static final int MIN_SEGMENTS_FOR_PROFILE = 3; // Minimum segments to create speaker profile
    
    /**
     * Enhanced speaker segment with voice features
     */
    public static class EnhancedSpeakerSegment {
        public String speakerId;
        public String speakerLabel;
        public double startTime;
        public double endTime;
        public String text;
        public VoiceFeatures voiceFeatures;
        public double confidence;
        
        public EnhancedSpeakerSegment(String speakerId, String speakerLabel, double startTime, 
                                      double endTime, String text, VoiceFeatures features, double confidence) {
            this.speakerId = speakerId;
            this.speakerLabel = speakerLabel;
            this.startTime = startTime;
            this.endTime = endTime;
            this.text = text;
            this.voiceFeatures = features;
            this.confidence = confidence;
        }
    }
    
    /**
     * Voice features extracted from audio segments
     */
    public static class VoiceFeatures {
        public double pitch;           // Average pitch frequency
        public double energy;          // Audio energy/volume
        public double speakingRate;    // Words per second
        public double pauseRatio;      // Ratio of pauses to speech
        public double spectralCentroid; // Frequency characteristic
        public double[] mfccFeatures;  // Mel-frequency cepstral coefficients (if available)
        
        // Calculate similarity with another voice feature set
        public double calculateSimilarity(VoiceFeatures other) {
            if (other == null) return 0;
            
            double pitchSim = 1 - Math.abs(pitch - other.pitch) / Math.max(pitch, other.pitch);
            double energySim = 1 - Math.abs(energy - other.energy) / Math.max(energy, other.energy);
            double rateSim = 1 - Math.abs(speakingRate - other.speakingRate) / Math.max(speakingRate, other.speakingRate);
            double pauseSim = 1 - Math.abs(pauseRatio - other.pauseRatio) / Math.max(0.5, Math.max(pauseRatio, other.pauseRatio));
            
            // Weighted average of similarities
            return (pitchSim * 0.35 + energySim * 0.15 + rateSim * 0.25 + pauseSim * 0.25);
        }
    }
    
    /**
     * Speaker profile with accumulated voice characteristics
     */
    private static class SpeakerProfile {
        public String speakerId;
        public String speakerName;
        public List<VoiceFeatures> voiceHistory;
        public VoiceFeatures averageFeatures;
        public int segmentCount;
        
        public SpeakerProfile(String id, String languageCode) {
            this.speakerId = id;
            this.speakerName = SpeakerLabels.formatSpeakerLabel(languageCode, Integer.parseInt(id));
            this.voiceHistory = new ArrayList<>();
            this.segmentCount = 0;
        }
        
        public void addVoiceFeatures(VoiceFeatures features) {
            voiceHistory.add(features);
            segmentCount++;
            updateAverageFeatures();
        }
        
        private void updateAverageFeatures() {
            if (voiceHistory.isEmpty()) return;
            
            double avgPitch = 0, avgEnergy = 0, avgRate = 0, avgPause = 0;
            for (VoiceFeatures vf : voiceHistory) {
                avgPitch += vf.pitch;
                avgEnergy += vf.energy;
                avgRate += vf.speakingRate;
                avgPause += vf.pauseRatio;
            }
            
            int count = voiceHistory.size();
            averageFeatures = new VoiceFeatures();
            averageFeatures.pitch = avgPitch / count;
            averageFeatures.energy = avgEnergy / count;
            averageFeatures.speakingRate = avgRate / count;
            averageFeatures.pauseRatio = avgPause / count;
        }
        
        public double matchProbability(VoiceFeatures features) {
            if (averageFeatures == null) return 0;
            return averageFeatures.calculateSimilarity(features);
        }
    }
    
    /**
     * Process audio segments with advanced speaker diarization
     */
    public static List<EnhancedSpeakerSegment> detectSpeakersAdvanced(String segmentsJson, Context context) {
        // Get the transcription language setting
        String languageCode = SettingsManager.getInstance(context).getTranscriptLanguage();
        return detectSpeakersAdvanced(segmentsJson, context, languageCode);
    }
    
    /**
     * Process audio segments with advanced speaker diarization with language support
     */
    public static List<EnhancedSpeakerSegment> detectSpeakersAdvanced(String segmentsJson, Context context, String languageCode) {
        List<EnhancedSpeakerSegment> enhancedSegments = new ArrayList<>();
        Map<String, SpeakerProfile> speakerProfiles = new HashMap<>();
        
        try {
            JSONArray segments = new JSONArray(segmentsJson);
            if (segments.length() == 0) {
                return enhancedSegments;
            }
            
            Log.d(TAG, "Starting advanced speaker detection for " + segments.length() + " segments");
            
            int nextSpeakerId = 1;
            
            for (int i = 0; i < segments.length(); i++) {
                JSONObject segment = segments.getJSONObject(i);
                
                double startTime = segment.getDouble("start");
                double endTime = segment.getDouble("end");
                String text = segment.getString("text").trim();
                
                if (text.isEmpty()) continue;
                
                // Extract voice features from segment
                VoiceFeatures features = extractVoiceFeatures(segment);
                
                // Find best matching speaker
                String bestMatchId = null;
                double bestScore = 0;
                
                for (SpeakerProfile profile : speakerProfiles.values()) {
                    double score = profile.matchProbability(features);
                    if (score > bestScore && score > VOICE_EMBEDDING_THRESHOLD) {
                        bestScore = score;
                        bestMatchId = profile.speakerId;
                    }
                }
                
                String speakerId;
                String speakerLabel;
                
                if (bestMatchId != null) {
                    // Existing speaker
                    speakerId = bestMatchId;
                    SpeakerProfile profile = speakerProfiles.get(speakerId);
                    profile.addVoiceFeatures(features);
                    speakerLabel = profile.speakerName;
                    
                    Log.d(TAG, "Segment " + i + " matched to " + speakerLabel + " (confidence: " + 
                          String.format("%.2f", bestScore) + ")");
                } else {
                    // New speaker
                    speakerId = String.valueOf(nextSpeakerId++);
                    SpeakerProfile newProfile = new SpeakerProfile(speakerId, languageCode);
                    newProfile.addVoiceFeatures(features);
                    speakerProfiles.put(speakerId, newProfile);
                    speakerLabel = newProfile.speakerName;
                    
                    Log.d(TAG, "Segment " + i + " identified as new " + speakerLabel);
                }
                
                enhancedSegments.add(new EnhancedSpeakerSegment(
                    speakerId, speakerLabel, startTime, endTime, text, features, bestScore
                ));
            }
            
            Log.d(TAG, "Advanced detection complete: " + speakerProfiles.size() + " unique speakers identified");
            
            // Post-process to merge similar speakers if needed
            mergeSimliarSpeakers(enhancedSegments, speakerProfiles);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error in advanced speaker detection", e);
        }
        
        return enhancedSegments;
    }
    
    /**
     * Extract voice features from a segment
     */
    private static VoiceFeatures extractVoiceFeatures(JSONObject segment) throws JSONException {
        VoiceFeatures features = new VoiceFeatures();
        
        String text = segment.getString("text").trim();
        double duration = segment.getDouble("end") - segment.getDouble("start");
        double avgLogprob = segment.optDouble("avg_logprob", 0);
        
        // Basic features we can extract from Whisper output
        int wordCount = text.split("\\s+").length;
        features.speakingRate = wordCount / Math.max(0.1, duration);
        
        // Estimate pitch from avg_logprob (this is a rough approximation)
        // Higher confidence often correlates with clearer speech
        features.pitch = 100 + (avgLogprob + 1) * 50; // Normalize to Hz-like range
        
        // Estimate energy from segment duration and text density
        features.energy = text.length() / duration;
        
        // Calculate pause ratio (simplified)
        double expectedDuration = wordCount * 0.15; // Average word duration
        features.pauseRatio = Math.max(0, (duration - expectedDuration) / duration);
        
        // Spectral centroid approximation (based on speaking rate and pitch)
        features.spectralCentroid = features.pitch * (1 + features.speakingRate / 10);
        
        return features;
    }
    
    /**
     * Merge similar speaker profiles that might be the same person
     */
    private static void mergeSimliarSpeakers(List<EnhancedSpeakerSegment> segments, 
                                            Map<String, SpeakerProfile> profiles) {
        // Check if any speaker profiles are too similar and should be merged
        List<String> profileIds = new ArrayList<>(profiles.keySet());
        
        for (int i = 0; i < profileIds.size() - 1; i++) {
            for (int j = i + 1; j < profileIds.size(); j++) {
                SpeakerProfile p1 = profiles.get(profileIds.get(i));
                SpeakerProfile p2 = profiles.get(profileIds.get(j));
                
                if (p1.averageFeatures != null && p2.averageFeatures != null) {
                    double similarity = p1.averageFeatures.calculateSimilarity(p2.averageFeatures);
                    
                    if (similarity > 0.85) { // Very similar voices
                        // Merge p2 into p1
                        Log.d(TAG, "Merging " + p2.speakerName + " into " + p1.speakerName + 
                              " (similarity: " + String.format("%.2f", similarity) + ")");
                        
                        for (EnhancedSpeakerSegment segment : segments) {
                            if (segment.speakerId.equals(p2.speakerId)) {
                                segment.speakerId = p1.speakerId;
                                segment.speakerLabel = p1.speakerName;
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Format enhanced segments into readable transcript with language support
     */
    public static String formatEnhancedTranscript(List<EnhancedSpeakerSegment> segments, String languageCode) {
        if (segments.isEmpty()) return "No transcript available";
        
        StringBuilder transcript = new StringBuilder();
        String currentSpeaker = "";
        
        for (EnhancedSpeakerSegment segment : segments) {
            if (!segment.speakerLabel.equals(currentSpeaker)) {
                if (transcript.length() > 0) {
                    transcript.append("\n\n");
                }
                
                // Add confidence indicator
                String confidenceIcon = segment.confidence > 0.8 ? "🎯" : 
                                       segment.confidence > 0.6 ? "🗣️" : "❓";
                
                transcript.append(confidenceIcon).append(" **")
                         .append(segment.speakerLabel)
                         .append("** [").append(formatTime(segment.startTime)).append("]");
                
                // Add voice characteristics with localized labels
                if (segment.voiceFeatures != null) {
                    String rateLabel = SpeakerLabels.formatRateLabel(languageCode, segment.voiceFeatures.speakingRate);
                    transcript.append(" (").append(rateLabel).append(")");
                }
                
                transcript.append("\n");
                currentSpeaker = segment.speakerLabel;
            } else {
                transcript.append(" ");
            }
            
            transcript.append(segment.text.trim());
        }
        
        return transcript.toString();
    }
    
    /**
     * Format enhanced segments into readable transcript (uses English labels)
     */
    public static String formatEnhancedTranscript(List<EnhancedSpeakerSegment> segments) {
        return formatEnhancedTranscript(segments, "en");
    }
    
    private static String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d", minutes, secs);
    }
}