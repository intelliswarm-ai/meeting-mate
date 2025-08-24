package ai.intelliswarm.meetingmate.ui.transcription;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import ai.intelliswarm.meetingmate.R;
import ai.intelliswarm.meetingmate.data.MeetingFileManager;
import ai.intelliswarm.meetingmate.export.TranscriptExporter;
import ai.intelliswarm.meetingmate.analytics.AppLogger;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TranscriptViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "TranscriptViewerActivity";
    
    private MaterialTextView titleText;
    private MaterialTextView dateText;
    private MaterialTextView transcriptContentText;
    private MaterialButton shareButton;
    private MaterialButton exportButton;
    private MaterialToolbar toolbar;
    
    private String meetingId;
    private String meetingTitle;
    private String transcriptContent;
    private Date meetingDate;
    private MeetingFileManager meetingFileManager;
    
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(SettingsManager.applyLanguage(newBase));
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transcript_viewer);
        
        meetingFileManager = new MeetingFileManager(this);
        
        initializeViews();
        loadTranscriptData();
        setupClickListeners();
        
        AppLogger.userAction(TAG, "transcript_viewed", meetingTitle);
    }
    
    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        titleText = findViewById(R.id.text_meeting_title);
        dateText = findViewById(R.id.text_meeting_date);
        transcriptContentText = findViewById(R.id.text_transcript_content);
        shareButton = findViewById(R.id.button_share);
        exportButton = findViewById(R.id.button_export);
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Meeting Transcript");
        }
    }
    
    private void loadTranscriptData() {
        Intent intent = getIntent();
        meetingId = intent.getStringExtra("meeting_id");
        meetingTitle = intent.getStringExtra("meeting_title");
        long dateMillis = intent.getLongExtra("meeting_date", System.currentTimeMillis());
        meetingDate = new Date(dateMillis);
        
        if (meetingId == null) {
            Toast.makeText(this, "Error: Missing meeting information", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Load transcript content
        transcriptContent = meetingFileManager.getTranscript(meetingId);
        if (transcriptContent == null || transcriptContent.trim().isEmpty()) {
            Toast.makeText(this, "Error: Transcript not found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Update UI
        titleText.setText(meetingTitle != null ? meetingTitle : "Untitled Meeting");
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault());
        dateText.setText(dateFormat.format(meetingDate));
        
        transcriptContentText.setText(transcriptContent);
    }
    
    private void setupClickListeners() {
        shareButton.setOnClickListener(v -> shareTranscript());
        exportButton.setOnClickListener(v -> exportTranscript());
    }
    
    private void shareTranscript() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Meeting Transcript: " + meetingTitle);
        shareIntent.putExtra(Intent.EXTRA_TEXT, formatTranscriptForShare());
        
        startActivity(Intent.createChooser(shareIntent, "Share Transcript"));
        
        AppLogger.userAction(TAG, "transcript_shared", meetingTitle);
    }
    
    private void exportTranscript() {
        try {
            TranscriptExporter exporter = new TranscriptExporter(this);
            boolean success = exporter.exportTranscript(meetingId, meetingTitle, transcriptContent, meetingDate);
            
            if (success) {
                Toast.makeText(this, "Transcript exported successfully!", Toast.LENGTH_SHORT).show();
                AppLogger.userAction(TAG, "transcript_exported", meetingTitle);
            } else {
                Toast.makeText(this, "Failed to export transcript", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Error exporting transcript", e);
            Toast.makeText(this, "Error exporting transcript: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private String formatTranscriptForShare() {
        StringBuilder formatted = new StringBuilder();
        formatted.append("Meeting: ").append(meetingTitle).append("\n");
        formatted.append("Date: ").append(new SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(meetingDate)).append("\n");
        formatted.append("Generated by Meeting Mate\n\n");
        formatted.append("--- TRANSCRIPT ---\n\n");
        formatted.append(transcriptContent);
        return formatted.toString();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}