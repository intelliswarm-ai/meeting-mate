package ai.intelliswarm.meetingmate.ui.transcription;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;
import ai.intelliswarm.meetingmate.R;
import ai.intelliswarm.meetingmate.data.MeetingFileManager;
import ai.intelliswarm.meetingmate.service.AudioRecordingService;
import ai.intelliswarm.meetingmate.service.OpenAIService;
import ai.intelliswarm.meetingmate.analytics.AppLogger;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TranscriptLinkActivity extends AppCompatActivity {
    
    private static final String TAG = "TranscriptLinkActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private MaterialTextView eventTitleText;
    private MaterialTextView eventTimeText;
    private MaterialTextView eventLocationText;
    private MaterialTextView eventDescriptionText;
    private RecyclerView transcriptsRecyclerView;
    private TranscriptFilesAdapter transcriptAdapter;
    private MeetingFileManager meetingFileManager;
    private SettingsManager settingsManager;
    private OpenAIService openAIService;
    
    // Recording controls
    private MaterialButton startRecordingButton;
    private MaterialButton stopRecordingButton;
    private MaterialTextView recordingStatusText;
    
    // Recording service
    private AudioRecordingService audioService;
    private boolean isServiceBound = false;
    private Handler recordingHandler;
    private Runnable recordingUpdateRunnable;
    
    private String eventId;
    private String eventTitle;
    private String eventDescription;
    private String eventLocation;
    private long eventStartTime;
    private long eventEndTime;
    
    private SimpleDateFormat dateTimeFormat;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transcript_link);
        
        dateTimeFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm", Locale.getDefault());
        meetingFileManager = new MeetingFileManager(this);
        settingsManager = SettingsManager.getInstance(this);
        
        // Initialize OpenAI service if API key is available
        if (settingsManager.hasOpenAIApiKey()) {
            openAIService = new OpenAIService(settingsManager.getOpenAIApiKey());
            AppLogger.d(TAG, "OpenAI service initialized");
        } else {
            AppLogger.w(TAG, "OpenAI service not initialized - no API key");
        }
        
        initializeViews();
        extractEventData();
        setupToolbar();
        setupRecyclerView();
        displayEventInfo();
        loadAvailableTranscripts();
        updateRecordingUIState();
        
        recordingHandler = new Handler();
        
        AppLogger.lifecycle("TranscriptLinkActivity", "onCreate");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateRecordingUIState();
    }
    
    private void updateRecordingUIState() {
        if (settingsManager != null && !settingsManager.hasOpenAIApiKey()) {
            recordingStatusText.setText("⚠️ Configure OpenAI API key in Settings to enable recording");
            startRecordingButton.setEnabled(false);
            openAIService = null; // Clear service if no API key
        } else {
            // Initialize or reinitialize OpenAI service if API key is now available
            if (openAIService == null && settingsManager.hasOpenAIApiKey()) {
                openAIService = new OpenAIService(settingsManager.getOpenAIApiKey());
                AppLogger.d(TAG, "OpenAI service reinitialized");
            }
            recordingStatusText.setText("Ready to record");
            startRecordingButton.setEnabled(true);
        }
    }
    
    private void initializeViews() {
        eventTitleText = findViewById(R.id.text_event_title);
        eventTimeText = findViewById(R.id.text_event_time);
        eventLocationText = findViewById(R.id.text_event_location);
        eventDescriptionText = findViewById(R.id.text_event_description);
        transcriptsRecyclerView = findViewById(R.id.recycler_transcripts);
        
        // Recording controls
        startRecordingButton = findViewById(R.id.button_start_recording);
        stopRecordingButton = findViewById(R.id.button_stop_recording);
        recordingStatusText = findViewById(R.id.text_recording_status);
        
        setupRecordingControls();
    }
    
    private void extractEventData() {
        Intent intent = getIntent();
        eventId = intent.getStringExtra("event_id");
        eventTitle = intent.getStringExtra("event_title");
        eventDescription = intent.getStringExtra("event_description");
        eventLocation = intent.getStringExtra("event_location");
        eventStartTime = intent.getLongExtra("event_start_time", 0);
        eventEndTime = intent.getLongExtra("event_end_time", 0);
        
        Log.d(TAG, "Event data extracted - ID: " + eventId + ", Title: " + eventTitle);
    }
    
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Link Transcript");
        }
    }
    
    private void setupRecyclerView() {
        transcriptAdapter = new TranscriptFilesAdapter(new ArrayList<>(), this::onTranscriptSelected);
        transcriptsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transcriptsRecyclerView.setAdapter(transcriptAdapter);
    }
    
    private void displayEventInfo() {
        eventTitleText.setText(eventTitle != null ? eventTitle : "Untitled Event");
        
        if (eventStartTime > 0) {
            String timeText = dateTimeFormat.format(new Date(eventStartTime));
            if (eventEndTime > 0) {
                SimpleDateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                timeText += " - " + timeOnlyFormat.format(new Date(eventEndTime));
            }
            eventTimeText.setText(timeText);
        } else {
            eventTimeText.setText("Time not specified");
        }
        
        if (eventLocation != null && !eventLocation.trim().isEmpty()) {
            eventLocationText.setText("📍 " + eventLocation);
            eventLocationText.setVisibility(MaterialTextView.VISIBLE);
        } else {
            eventLocationText.setVisibility(MaterialTextView.GONE);
        }
        
        if (eventDescription != null && !eventDescription.trim().isEmpty()) {
            eventDescriptionText.setText(eventDescription);
            eventDescriptionText.setVisibility(MaterialTextView.VISIBLE);
        } else {
            eventDescriptionText.setVisibility(MaterialTextView.GONE);
        }
    }
    
    private void loadAvailableTranscripts() {
        Log.d(TAG, "Loading available transcript files");
        
        new Thread(() -> {
            try {
                List<File> transcriptFiles = meetingFileManager.getAllTranscriptFiles();
                List<TranscriptFileInfo> transcriptInfos = new ArrayList<>();
                
                for (File file : transcriptFiles) {
                    TranscriptFileInfo info = new TranscriptFileInfo();
                    info.file = file;
                    info.fileName = file.getName();
                    info.lastModified = new Date(file.lastModified());
                    info.fileSize = file.length();
                    transcriptInfos.add(info);
                }
                
                runOnUiThread(() -> {
                    transcriptAdapter.updateTranscripts(transcriptInfos);
                    String message = transcriptInfos.isEmpty() ? 
                        "No transcript files found" : 
                        "Found " + transcriptInfos.size() + " transcript file(s)";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading transcript files", e);
                runOnUiThread(() -> 
                    Toast.makeText(this, "Error loading transcripts: " + e.getMessage(), 
                                   Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    private void onTranscriptSelected(TranscriptFileInfo transcriptInfo) {
        Log.d(TAG, "Transcript selected: " + transcriptInfo.fileName + " for event: " + eventTitle);
        
        // Here you would implement the actual linking logic
        // For now, we'll show a success message and close the activity
        Toast.makeText(this, "Transcript '" + transcriptInfo.fileName + "' linked to event '" + eventTitle + "'", 
                       Toast.LENGTH_LONG).show();
        
        // In a real implementation, you would:
        // 1. Save the link in a database or shared preferences
        // 2. Update the transcript file with event metadata
        // 3. Possibly copy or move the file to an event-specific folder
        
        finish();
    }
    
    private void setupRecordingControls() {
        startRecordingButton.setOnClickListener(v -> {
            AppLogger.userAction("TranscriptLinkActivity", "start_recording_clicked", eventTitle);
            if (checkAudioPermissions()) {
                startRecording();
            } else {
                requestAudioPermissions();
            }
        });
        
        stopRecordingButton.setOnClickListener(v -> {
            AppLogger.userAction("TranscriptLinkActivity", "stop_recording_clicked", null);
            stopRecording();
        });
    }
    
    private boolean checkAudioPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestAudioPermissions() {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.RECORD_AUDIO}, 
            PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AppLogger.i(TAG, "Audio permission granted");
                startRecording();
            } else {
                AppLogger.w(TAG, "Audio permission denied");
                Toast.makeText(this, "Audio recording permission is required to record meetings", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void startRecording() {
        try {
            // Check if OpenAI API key is configured
            if (!settingsManager.hasOpenAIApiKey()) {
                AppLogger.w(TAG, "Cannot start recording - OpenAI API key not configured");
                Toast.makeText(this, 
                    "OpenAI API key not configured. Please set up your API key in Settings to enable transcription.", 
                    Toast.LENGTH_LONG).show();
                return;
            }
            
            // Bind to recording service
            Intent serviceIntent = new Intent(this, AudioRecordingService.class);
            bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
            
            // Update UI
            startRecordingButton.setEnabled(false);
            stopRecordingButton.setEnabled(true);
            recordingStatusText.setText("🔴 Recording in progress...");
            
            // Start recording timer
            startRecordingTimer();
            
            AppLogger.i(TAG, "Recording started for event: " + eventTitle);
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error starting recording", e);
            Toast.makeText(this, "Error starting recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            resetRecordingUI();
        }
    }
    
    private void stopRecording() {
        try {
            if (isServiceBound && audioService != null) {
                audioService.stopRecording();
                unbindService(serviceConnection);
                isServiceBound = false;
            }
            
            // Stop recording timer
            if (recordingHandler != null && recordingUpdateRunnable != null) {
                recordingHandler.removeCallbacks(recordingUpdateRunnable);
            }
            
            resetRecordingUI();
            recordingStatusText.setText("✅ Recording completed! Processing transcript...");
            
            AppLogger.i(TAG, "Recording stopped for event: " + eventTitle);
            Toast.makeText(this, "Recording stopped, processing transcript...", Toast.LENGTH_SHORT).show();
            
            // Get the recorded file and process with OpenAI
            if (audioService != null) {
                String filePath = audioService.getCurrentFilePath();
                if (filePath != null) {
                    File recordedFile = new File(filePath);
                    if (recordedFile.exists()) {
                        processRecordingWithOpenAI(recordedFile);
                    } else {
                        AppLogger.e(TAG, "Recorded file doesn't exist: " + filePath);
                        recordingStatusText.setText("❌ Recording file not found");
                    }
                } else {
                    AppLogger.e(TAG, "Recording file path is null");
                    recordingStatusText.setText("❌ Recording file path not available");
                }
            }
            
            // Refresh transcript list to show new recording
            loadAvailableTranscripts();
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error stopping recording", e);
            Toast.makeText(this, "Error stopping recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            resetRecordingUI();
        }
    }
    
    private void resetRecordingUI() {
        startRecordingButton.setEnabled(true);
        stopRecordingButton.setEnabled(false);
        recordingStatusText.setText("Ready to record");
    }
    
    private void startRecordingTimer() {
        recordingUpdateRunnable = new Runnable() {
            long startTime = System.currentTimeMillis();
            
            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - startTime;
                long seconds = (elapsedTime / 1000) % 60;
                long minutes = (elapsedTime / (1000 * 60)) % 60;
                long hours = (elapsedTime / (1000 * 60 * 60)) % 24;
                
                String timeString;
                if (hours > 0) {
                    timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                } else {
                    timeString = String.format("%02d:%02d", minutes, seconds);
                }
                
                recordingStatusText.setText("🔴 Recording: " + timeString);
                recordingHandler.postDelayed(this, 1000);
            }
        };
        recordingHandler.post(recordingUpdateRunnable);
    }
    
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioRecordingService.LocalBinder binder = (AudioRecordingService.LocalBinder) service;
            audioService = binder.getService();
            isServiceBound = true;
            
            // Start the actual recording
            audioService.startRecording();
            AppLogger.d(TAG, "Connected to AudioRecordingService");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioService = null;
            isServiceBound = false;
            AppLogger.d(TAG, "Disconnected from AudioRecordingService");
        }
    };
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up recording resources
        if (recordingHandler != null && recordingUpdateRunnable != null) {
            recordingHandler.removeCallbacks(recordingUpdateRunnable);
        }
        
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        AppLogger.lifecycle("TranscriptLinkActivity", "onDestroy");
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void processRecordingWithOpenAI(File audioFile) {
        if (openAIService == null) {
            AppLogger.e(TAG, "OpenAI service not available");
            recordingStatusText.setText("❌ OpenAI service not available");
            return;
        }
        
        AppLogger.d(TAG, "Starting OpenAI transcription for file: " + audioFile.getName());
        
        // Update UI to show processing
        recordingStatusText.setText("🔄 Transcribing with OpenAI...");
        
        // Call OpenAI transcription with callback
        openAIService.transcribeAudio(audioFile, new OpenAIService.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, org.json.JSONArray segments) {
                AppLogger.d(TAG, "OpenAI transcription completed successfully");
                runOnUiThread(() -> {
                    if (transcript != null && !transcript.trim().isEmpty()) {
                        // Save transcript file
                        // Use consistent meeting ID format like HomeFragment
                        String meetingId;
                        if (eventId != null && !eventId.trim().isEmpty()) {
                            meetingId = "event_" + eventId + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        } else {
                            // Use same format as recording tab for consistency
                            meetingId = meetingFileManager.generateMeetingId();
                        }
                        Date meetingDate = new Date();
                        
                        boolean saved = meetingFileManager.saveTranscript(meetingId, eventTitle, transcript, meetingDate);
                        if (saved) {
                            AppLogger.i(TAG, "Transcript saved successfully");
                            recordingStatusText.setText("✅ Transcript completed and saved!");
                            Toast.makeText(TranscriptLinkActivity.this, "Transcript completed successfully!", Toast.LENGTH_SHORT).show();
                            loadAvailableTranscripts(); // Refresh the list
                        } else {
                            AppLogger.e(TAG, "Failed to save transcript");
                            recordingStatusText.setText("❌ Error saving transcript");
                            Toast.makeText(TranscriptLinkActivity.this, "Error saving transcript", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        AppLogger.w(TAG, "Empty transcript received from OpenAI");
                        recordingStatusText.setText("❌ Empty transcript received");
                        Toast.makeText(TranscriptLinkActivity.this, "Transcription failed - empty result", Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                AppLogger.e(TAG, "OpenAI transcription failed: " + error);
                runOnUiThread(() -> {
                    recordingStatusText.setText("❌ Transcription failed");
                    Toast.makeText(TranscriptLinkActivity.this, "Transcription failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    public static class TranscriptFileInfo {
        public File file;
        public String fileName;
        public Date lastModified;
        public long fileSize;
    }
}