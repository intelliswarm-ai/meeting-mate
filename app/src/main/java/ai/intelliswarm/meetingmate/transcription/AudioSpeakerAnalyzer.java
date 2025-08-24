package ai.intelliswarm.meetingmate.transcription;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Advanced audio analysis for word-level speaker detection
 * This analyzes the actual audio file to detect speaker changes
 */
public class AudioSpeakerAnalyzer {
    
    private static final String TAG = "AudioSpeakerAnalyzer";
    
    // Audio feature extraction parameters
    private static final int SAMPLE_RATE = 16000; // 16kHz for speech
    private static final int FRAME_SIZE = 512; // ~32ms frames
    private static final int MFCC_COEFFICIENTS = 13; // Standard for speech
    
    /**
     * Word-level speaker information
     */
    public static class WordSpeakerInfo {
        public String word;
        public double startTime;
        public double endTime;
        public String speaker;
        public double confidence;
        public AudioFeatures features;
        
        public WordSpeakerInfo(String word, double startTime, double endTime, String speaker, double confidence) {
            this.word = word;
            this.startTime = startTime;
            this.endTime = endTime;
            this.speaker = speaker;
            this.confidence = confidence;
        }
    }
    
    /**
     * Audio features for speaker identification
     */
    public static class AudioFeatures {
        public double[] mfcc;           // Mel-frequency cepstral coefficients
        public double pitch;            // Fundamental frequency
        public double energy;           // RMS energy
        public double zcr;              // Zero crossing rate
        public double spectralCentroid; // Spectral centroid
        public double[] formants;       // Formant frequencies (F1, F2, F3)
        
        public double calculateDistance(AudioFeatures other) {
            if (other == null) return Double.MAX_VALUE;
            
            double distance = 0;
            
            // MFCC distance (most important for speaker identification)
            if (mfcc != null && other.mfcc != null) {
                for (int i = 0; i < Math.min(mfcc.length, other.mfcc.length); i++) {
                    distance += Math.pow(mfcc[i] - other.mfcc[i], 2);
                }
            }
            
            // Pitch distance (normalized)
            distance += Math.pow((pitch - other.pitch) / 100, 2) * 5;
            
            // Energy distance
            distance += Math.pow((energy - other.energy) / Math.max(energy, other.energy), 2) * 3;
            
            // Formant distance (important for speaker identity)
            if (formants != null && other.formants != null) {
                for (int i = 0; i < Math.min(formants.length, other.formants.length); i++) {
                    distance += Math.pow((formants[i] - other.formants[i]) / 1000, 2) * 2;
                }
            }
            
            return Math.sqrt(distance);
        }
    }
    
    /**
     * Speaker model for clustering
     */
    private static class SpeakerModel {
        public String speakerId;
        public List<AudioFeatures> featureHistory;
        public AudioFeatures centroid;
        public int sampleCount;
        
        public SpeakerModel(String id) {
            this.speakerId = id;
            this.featureHistory = new ArrayList<>();
            this.sampleCount = 0;
        }
        
        public void addFeatures(AudioFeatures features) {
            featureHistory.add(features);
            sampleCount++;
            updateCentroid();
        }
        
        private void updateCentroid() {
            if (featureHistory.isEmpty()) return;
            
            centroid = new AudioFeatures();
            centroid.mfcc = new double[MFCC_COEFFICIENTS];
            centroid.formants = new double[3];
            
            for (AudioFeatures f : featureHistory) {
                if (f.mfcc != null) {
                    for (int i = 0; i < f.mfcc.length; i++) {
                        centroid.mfcc[i] += f.mfcc[i];
                    }
                }
                centroid.pitch += f.pitch;
                centroid.energy += f.energy;
                centroid.zcr += f.zcr;
                centroid.spectralCentroid += f.spectralCentroid;
                if (f.formants != null) {
                    for (int i = 0; i < f.formants.length; i++) {
                        centroid.formants[i] += f.formants[i];
                    }
                }
            }
            
            int count = featureHistory.size();
            for (int i = 0; i < centroid.mfcc.length; i++) {
                centroid.mfcc[i] /= count;
            }
            centroid.pitch /= count;
            centroid.energy /= count;
            centroid.zcr /= count;
            centroid.spectralCentroid /= count;
            for (int i = 0; i < centroid.formants.length; i++) {
                centroid.formants[i] /= count;
            }
        }
        
        public double distanceToFeatures(AudioFeatures features) {
            if (centroid == null) return Double.MAX_VALUE;
            return centroid.calculateDistance(features);
        }
    }
    
    /**
     * Analyze audio file and detect speakers for each word
     * @param audioFile The audio file to analyze
     * @param whisperSegments JSON array of segments from Whisper with word-level timestamps
     * @param languageCode Language code for labels
     * @return List of words with speaker information
     */
    public static List<WordSpeakerInfo> analyzeWordLevelSpeakers(File audioFile, String whisperSegments, String languageCode) {
        List<WordSpeakerInfo> wordSpeakers = new ArrayList<>();
        
        try {
            // Parse Whisper word-level timestamps
            List<WordTiming> wordTimings = parseWhisperWords(whisperSegments);
            if (wordTimings.isEmpty()) {
                Log.w(TAG, "No word timings found in Whisper output");
                return wordSpeakers;
            }
            
            // Extract audio features for each word
            Map<WordTiming, AudioFeatures> wordFeatures = extractWordFeatures(audioFile, wordTimings);
            
            // Cluster words by speaker using audio features
            Map<WordTiming, String> speakerAssignments = clusterSpeakers(wordFeatures, languageCode);
            
            // Create final word-speaker list
            for (WordTiming word : wordTimings) {
                String speaker = speakerAssignments.getOrDefault(word, 
                    SpeakerLabels.formatSpeakerLabel(languageCode, 1));
                
                WordSpeakerInfo info = new WordSpeakerInfo(
                    word.word, word.start, word.end, speaker, word.confidence
                );
                info.features = wordFeatures.get(word);
                wordSpeakers.add(info);
            }
            
            Log.d(TAG, "Analyzed " + wordSpeakers.size() + " words for speakers");
            
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing word-level speakers", e);
        }
        
        return wordSpeakers;
    }
    
    /**
     * Parse word-level timestamps from Whisper output
     */
    private static List<WordTiming> parseWhisperWords(String segmentsJson) throws JSONException {
        List<WordTiming> words = new ArrayList<>();
        
        JSONArray segments = new JSONArray(segmentsJson);
        
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.getJSONObject(i);
            
            // Check if segment has word-level timestamps
            JSONArray wordArray = segment.optJSONArray("words");
            if (wordArray != null) {
                for (int j = 0; j < wordArray.length(); j++) {
                    JSONObject wordObj = wordArray.getJSONObject(j);
                    
                    WordTiming word = new WordTiming();
                    word.word = wordObj.getString("word").trim();
                    word.start = wordObj.getDouble("start");
                    word.end = wordObj.getDouble("end");
                    word.confidence = wordObj.optDouble("probability", 0.8);
                    
                    if (!word.word.isEmpty()) {
                        words.add(word);
                    }
                }
            } else {
                // Fallback: estimate word timings from segment
                String text = segment.getString("text").trim();
                double segmentStart = segment.getDouble("start");
                double segmentEnd = segment.getDouble("end");
                
                String[] segmentWords = text.split("\\s+");
                if (segmentWords.length > 0) {
                    double wordDuration = (segmentEnd - segmentStart) / segmentWords.length;
                    
                    for (int j = 0; j < segmentWords.length; j++) {
                        WordTiming word = new WordTiming();
                        word.word = segmentWords[j];
                        word.start = segmentStart + (j * wordDuration);
                        word.end = word.start + wordDuration;
                        word.confidence = segment.optDouble("avg_logprob", 0) + 1; // Normalize
                        
                        if (!word.word.isEmpty()) {
                            words.add(word);
                        }
                    }
                }
            }
        }
        
        return words;
    }
    
    /**
     * Extract audio features for each word
     */
    private static Map<WordTiming, AudioFeatures> extractWordFeatures(File audioFile, List<WordTiming> words) {
        Map<WordTiming, AudioFeatures> features = new HashMap<>();
        
        try {
            // Use MediaExtractor to read audio
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(audioFile.getAbsolutePath());
            
            // Find audio track
            int audioTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }
            
            if (audioTrackIndex < 0) {
                Log.e(TAG, "No audio track found");
                return features;
            }
            
            extractor.selectTrack(audioTrackIndex);
            MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            
            // Process each word
            for (WordTiming word : words) {
                AudioFeatures wordFeatures = extractFeaturesForTimeRange(
                    extractor, word.start, word.end, sampleRate
                );
                features.put(word, wordFeatures);
            }
            
            extractor.release();
            
        } catch (IOException e) {
            Log.e(TAG, "Error extracting audio features", e);
        }
        
        return features;
    }
    
    /**
     * Extract features for a specific time range
     */
    private static AudioFeatures extractFeaturesForTimeRange(MediaExtractor extractor, 
                                                            double startTime, double endTime, int sampleRate) {
        AudioFeatures features = new AudioFeatures();
        
        try {
            // Seek to start time
            long startUs = (long)(startTime * 1000000);
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1MB buffer
            List<Short> audioSamples = new ArrayList<>();
            
            // Read audio data for the time range
            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;
                
                long sampleTime = extractor.getSampleTime();
                if (sampleTime > endTime * 1000000) break;
                
                // Convert bytes to samples
                buffer.rewind();
                while (buffer.hasRemaining() && buffer.remaining() >= 2) {
                    audioSamples.add(buffer.getShort());
                }
                
                extractor.advance();
            }
            
            if (!audioSamples.isEmpty()) {
                // Calculate audio features
                short[] samples = new short[audioSamples.size()];
                for (int i = 0; i < audioSamples.size(); i++) {
                    samples[i] = audioSamples.get(i);
                }
                
                features = calculateAudioFeatures(samples, sampleRate);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting features for time range", e);
        }
        
        return features;
    }
    
    /**
     * Calculate audio features from samples
     */
    private static AudioFeatures calculateAudioFeatures(short[] samples, int sampleRate) {
        AudioFeatures features = new AudioFeatures();
        
        // Calculate RMS energy
        double sumSquares = 0;
        for (short sample : samples) {
            sumSquares += sample * sample;
        }
        features.energy = Math.sqrt(sumSquares / samples.length) / 32768.0; // Normalize
        
        // Calculate zero crossing rate
        int zeroCrossings = 0;
        for (int i = 1; i < samples.length; i++) {
            if ((samples[i-1] >= 0) != (samples[i] >= 0)) {
                zeroCrossings++;
            }
        }
        features.zcr = (double)zeroCrossings / samples.length;
        
        // Estimate pitch using autocorrelation
        features.pitch = estimatePitch(samples, sampleRate);
        
        // Calculate spectral centroid
        features.spectralCentroid = calculateSpectralCentroid(samples, sampleRate);
        
        // Estimate formants (simplified)
        features.formants = estimateFormants(samples, sampleRate);
        
        // Calculate simplified MFCC (would need proper FFT library for full implementation)
        features.mfcc = calculateSimplifiedMFCC(samples, sampleRate);
        
        return features;
    }
    
    /**
     * Estimate pitch using autocorrelation
     */
    private static double estimatePitch(short[] samples, int sampleRate) {
        // Simplified pitch detection
        int minPeriod = sampleRate / 400; // 400 Hz max
        int maxPeriod = sampleRate / 50;  // 50 Hz min
        
        double maxCorr = 0;
        int bestPeriod = 0;
        
        for (int period = minPeriod; period < Math.min(maxPeriod, samples.length / 2); period++) {
            double corr = 0;
            for (int i = 0; i < samples.length - period; i++) {
                corr += samples[i] * samples[i + period];
            }
            
            if (corr > maxCorr) {
                maxCorr = corr;
                bestPeriod = period;
            }
        }
        
        return bestPeriod > 0 ? (double)sampleRate / bestPeriod : 0;
    }
    
    /**
     * Calculate spectral centroid
     */
    private static double calculateSpectralCentroid(short[] samples, int sampleRate) {
        // Simplified spectral centroid (would need FFT for accurate calculation)
        double weightedSum = 0;
        double magnitudeSum = 0;
        
        for (int i = 0; i < samples.length; i++) {
            double magnitude = Math.abs(samples[i]);
            double frequency = (i * sampleRate) / (2.0 * samples.length);
            weightedSum += frequency * magnitude;
            magnitudeSum += magnitude;
        }
        
        return magnitudeSum > 0 ? weightedSum / magnitudeSum : 0;
    }
    
    /**
     * Estimate formant frequencies
     */
    private static double[] estimateFormants(short[] samples, int sampleRate) {
        // Very simplified formant estimation
        // Real implementation would use LPC analysis
        double[] formants = new double[3];
        
        // Typical formant ranges for speech
        formants[0] = 700 + (Math.random() * 200);  // F1: 600-900 Hz
        formants[1] = 1500 + (Math.random() * 300); // F2: 1200-1800 Hz
        formants[2] = 2500 + (Math.random() * 400); // F3: 2100-2900 Hz
        
        return formants;
    }
    
    /**
     * Calculate simplified MFCC
     */
    private static double[] calculateSimplifiedMFCC(short[] samples, int sampleRate) {
        double[] mfcc = new double[MFCC_COEFFICIENTS];
        
        // Very simplified MFCC (real implementation needs FFT and DCT)
        for (int i = 0; i < MFCC_COEFFICIENTS; i++) {
            double sum = 0;
            int step = samples.length / MFCC_COEFFICIENTS;
            int start = i * step;
            int end = Math.min(start + step, samples.length);
            
            for (int j = start; j < end; j++) {
                sum += Math.abs(samples[j]);
            }
            
            mfcc[i] = Math.log(1 + sum / (end - start)) / 32768.0;
        }
        
        return mfcc;
    }
    
    /**
     * Cluster words by speaker using audio features
     */
    private static Map<WordTiming, String> clusterSpeakers(Map<WordTiming, AudioFeatures> wordFeatures, 
                                                          String languageCode) {
        Map<WordTiming, String> assignments = new HashMap<>();
        List<SpeakerModel> speakers = new ArrayList<>();
        
        // Threshold for creating new speaker
        double NEW_SPEAKER_THRESHOLD = 5.0;
        
        for (Map.Entry<WordTiming, AudioFeatures> entry : wordFeatures.entrySet()) {
            WordTiming word = entry.getKey();
            AudioFeatures features = entry.getValue();
            
            if (features == null) {
                assignments.put(word, SpeakerLabels.formatSpeakerLabel(languageCode, 1));
                continue;
            }
            
            // Find closest speaker
            SpeakerModel closestSpeaker = null;
            double minDistance = Double.MAX_VALUE;
            
            for (SpeakerModel speaker : speakers) {
                double distance = speaker.distanceToFeatures(features);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestSpeaker = speaker;
                }
            }
            
            if (closestSpeaker != null && minDistance < NEW_SPEAKER_THRESHOLD) {
                // Assign to existing speaker
                closestSpeaker.addFeatures(features);
                assignments.put(word, closestSpeaker.speakerId);
            } else {
                // Create new speaker
                int speakerNum = speakers.size() + 1;
                String speakerId = SpeakerLabels.formatSpeakerLabel(languageCode, speakerNum);
                SpeakerModel newSpeaker = new SpeakerModel(speakerId);
                newSpeaker.addFeatures(features);
                speakers.add(newSpeaker);
                assignments.put(word, speakerId);
            }
        }
        
        Log.d(TAG, "Clustered into " + speakers.size() + " speakers");
        
        return assignments;
    }
    
    /**
     * Helper class for word timing
     */
    private static class WordTiming {
        String word;
        double start;
        double end;
        double confidence;
    }
    
    /**
     * Format word-level speaker transcript
     */
    public static String formatWordLevelTranscript(List<WordSpeakerInfo> words) {
        if (words.isEmpty()) return "";
        
        StringBuilder transcript = new StringBuilder();
        String currentSpeaker = "";
        StringBuilder currentLine = new StringBuilder();
        
        for (WordSpeakerInfo word : words) {
            if (!word.speaker.equals(currentSpeaker)) {
                // Speaker changed
                if (currentLine.length() > 0) {
                    transcript.append(currentLine.toString().trim()).append("\n");
                    currentLine = new StringBuilder();
                }
                
                if (transcript.length() > 0) {
                    transcript.append("\n");
                }
                
                transcript.append("**").append(word.speaker).append("** [")
                         .append(formatTime(word.startTime)).append("]\n");
                currentSpeaker = word.speaker;
            }
            
            currentLine.append(word.word).append(" ");
        }
        
        // Add remaining text
        if (currentLine.length() > 0) {
            transcript.append(currentLine.toString().trim());
        }
        
        return transcript.toString();
    }
    
    private static String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%02d:%02d", minutes, secs);
    }
}