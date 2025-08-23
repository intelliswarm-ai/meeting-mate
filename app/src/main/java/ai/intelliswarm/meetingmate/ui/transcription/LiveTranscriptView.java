package ai.intelliswarm.meetingmate.ui.transcription;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Custom view for displaying live transcription with professional features
 */
public class LiveTranscriptView extends NestedScrollView {
    
    private TextView transcriptTextView;
    private SpannableStringBuilder fullTranscript;
    private List<TranscriptSegment> segments;
    private SimpleDateFormat timeFormat;
    private boolean autoScroll = true;
    private int currentSpeaker = 0;
    private long sessionStartTime;
    
    // Colors for different speakers
    private static final int[] SPEAKER_COLORS = {
        Color.parseColor("#1976D2"), // Blue
        Color.parseColor("#388E3C"), // Green
        Color.parseColor("#F57C00"), // Orange
        Color.parseColor("#7B1FA2"), // Purple
        Color.parseColor("#C2185B"), // Pink
        Color.parseColor("#00796B")  // Teal
    };
    
    public LiveTranscriptView(Context context) {
        super(context);
        init();
    }
    
    public LiveTranscriptView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public LiveTranscriptView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // Create TextView for transcript
        transcriptTextView = new TextView(getContext());
        transcriptTextView.setPadding(32, 32, 32, 32);
        transcriptTextView.setTextSize(16f);
        transcriptTextView.setLineSpacing(8f, 1.2f);
        transcriptTextView.setTextColor(Color.parseColor("#212121"));
        
        // Add to ScrollView
        addView(transcriptTextView);
        
        // Initialize data structures
        fullTranscript = new SpannableStringBuilder();
        segments = new ArrayList<>();
        timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        sessionStartTime = System.currentTimeMillis();
        
        // Set scroll behavior
        setFillViewport(true);
        setSmoothScrollingEnabled(true);
    }
    
    /**
     * Start a new transcription session
     */
    public void startSession() {
        sessionStartTime = System.currentTimeMillis();
        fullTranscript.clear();
        segments.clear();
        transcriptTextView.setText("");
        
        // Add session header
        addSystemMessage("📝 Recording started - Real-time transcription active");
    }
    
    /**
     * Add new transcribed text with speaker identification
     */
    public void addTranscript(String text, int speakerId, float confidence) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        // Create new segment
        TranscriptSegment segment = new TranscriptSegment();
        segment.text = text;
        segment.speakerId = speakerId;
        segment.timestamp = System.currentTimeMillis();
        segment.confidence = confidence;
        segments.add(segment);
        
        // Add timestamp if needed (every 30 seconds)
        if (shouldAddTimestamp()) {
            addTimestamp();
        }
        
        // Add speaker label if speaker changed
        if (speakerId != currentSpeaker) {
            addSpeakerLabel(speakerId);
            currentSpeaker = speakerId;
        }
        
        // Add the actual transcript text
        int startIndex = fullTranscript.length();
        fullTranscript.append(text).append(" ");
        
        // Apply styling based on confidence
        if (confidence < 0.5f) {
            // Low confidence - gray and italic
            fullTranscript.setSpan(
                new ForegroundColorSpan(Color.GRAY),
                startIndex, fullTranscript.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            fullTranscript.setSpan(
                new StyleSpan(Typeface.ITALIC),
                startIndex, fullTranscript.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        } else {
            // Normal confidence - speaker color
            fullTranscript.setSpan(
                new ForegroundColorSpan(SPEAKER_COLORS[speakerId % SPEAKER_COLORS.length]),
                startIndex, fullTranscript.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        
        // Update display
        updateDisplay();
    }
    
    /**
     * Add partial/interim results (shown in different style)
     */
    public void updatePartialTranscript(String partialText) {
        if (partialText == null || partialText.trim().isEmpty()) {
            return;
        }
        
        // Show partial text in gray italics
        SpannableStringBuilder displayText = new SpannableStringBuilder(fullTranscript);
        displayText.append("\n");
        
        int partialStart = displayText.length();
        displayText.append("💭 ").append(partialText);
        
        displayText.setSpan(
            new ForegroundColorSpan(Color.parseColor("#757575")),
            partialStart, displayText.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        displayText.setSpan(
            new StyleSpan(Typeface.ITALIC),
            partialStart, displayText.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        
        transcriptTextView.setText(displayText);
        scrollToBottom();
    }
    
    /**
     * Add a timestamp marker
     */
    private void addTimestamp() {
        String timestamp = timeFormat.format(new Date());
        int startIndex = fullTranscript.length();
        
        fullTranscript.append("\n\n[").append(timestamp).append("]\n");
        
        fullTranscript.setSpan(
            new ForegroundColorSpan(Color.parseColor("#9E9E9E")),
            startIndex, fullTranscript.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        fullTranscript.setSpan(
            new StyleSpan(Typeface.BOLD),
            startIndex, fullTranscript.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }
    
    /**
     * Add speaker label
     */
    private void addSpeakerLabel(int speakerId) {
        String speakerName = "Speaker " + (speakerId + 1);
        int startIndex = fullTranscript.length();
        
        fullTranscript.append("\n").append(speakerName).append(": ");
        
        fullTranscript.setSpan(
            new ForegroundColorSpan(SPEAKER_COLORS[speakerId % SPEAKER_COLORS.length]),
            startIndex, fullTranscript.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        fullTranscript.setSpan(
            new StyleSpan(Typeface.BOLD),
            startIndex, fullTranscript.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }
    
    /**
     * Add system message (recording started, paused, etc.)
     */
    public void addSystemMessage(String message) {
        int startIndex = fullTranscript.length();
        
        if (fullTranscript.length() > 0) {
            fullTranscript.append("\n\n");
        }
        fullTranscript.append(message).append("\n\n");
        
        fullTranscript.setSpan(
            new ForegroundColorSpan(Color.parseColor("#616161")),
            startIndex, fullTranscript.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        fullTranscript.setSpan(
            new StyleSpan(Typeface.ITALIC),
            startIndex, fullTranscript.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        
        updateDisplay();
    }
    
    /**
     * Check if we should add a timestamp (every 30 seconds)
     */
    private boolean shouldAddTimestamp() {
        if (segments.isEmpty()) {
            return false;
        }
        
        // Find last timestamp
        long lastTimestamp = sessionStartTime;
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i).isTimestamp) {
                lastTimestamp = segments.get(i).timestamp;
                break;
            }
        }
        
        // Add timestamp every 30 seconds
        return (System.currentTimeMillis() - lastTimestamp) > 30000;
    }
    
    /**
     * Update the display and scroll if needed
     */
    private void updateDisplay() {
        transcriptTextView.setText(fullTranscript);
        if (autoScroll) {
            scrollToBottom();
        }
    }
    
    /**
     * Scroll to bottom of transcript
     */
    private void scrollToBottom() {
        post(() -> fullScroll(ScrollView.FOCUS_DOWN));
    }
    
    /**
     * Get the full transcript as plain text
     */
    public String getPlainTranscript() {
        return fullTranscript.toString();
    }
    
    /**
     * Get transcript segments for further processing
     */
    public List<TranscriptSegment> getSegments() {
        return new ArrayList<>(segments);
    }
    
    /**
     * Set whether to auto-scroll to bottom
     */
    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
    }
    
    /**
     * Clear the transcript
     */
    public void clear() {
        fullTranscript.clear();
        segments.clear();
        transcriptTextView.setText("");
        currentSpeaker = 0;
    }
    
    /**
     * End the transcription session
     */
    public void endSession() {
        long duration = System.currentTimeMillis() - sessionStartTime;
        int minutes = (int) (duration / 60000);
        int seconds = (int) ((duration % 60000) / 1000);
        
        String message = String.format("✅ Recording ended - Duration: %d:%02d", minutes, seconds);
        addSystemMessage(message);
        
        // Add session statistics
        int wordCount = fullTranscript.toString().split("\\s+").length;
        int speakerCount = countUniqueSpeakers();
        
        String stats = String.format("📊 Statistics: %d words, %d speaker%s", 
            wordCount, speakerCount, speakerCount != 1 ? "s" : "");
        addSystemMessage(stats);
    }
    
    /**
     * Count unique speakers in the session
     */
    private int countUniqueSpeakers() {
        List<Integer> speakers = new ArrayList<>();
        for (TranscriptSegment segment : segments) {
            if (!speakers.contains(segment.speakerId)) {
                speakers.add(segment.speakerId);
            }
        }
        return speakers.size();
    }
    
    /**
     * Data class for transcript segments
     */
    public static class TranscriptSegment {
        public String text;
        public int speakerId;
        public long timestamp;
        public float confidence;
        public boolean isTimestamp;
        
        public TranscriptSegment() {
            this.isTimestamp = false;
        }
    }
}