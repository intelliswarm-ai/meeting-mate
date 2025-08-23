package ai.intelliswarm.meetingmate.ui.home;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ai.intelliswarm.meetingmate.R;
import ai.intelliswarm.meetingmate.data.MeetingFileManager;
import ai.intelliswarm.meetingmate.databinding.FragmentHomeBinding;
import ai.intelliswarm.meetingmate.service.AudioRecordingService;
import ai.intelliswarm.meetingmate.service.CalendarService;
import ai.intelliswarm.meetingmate.service.OpenAIService;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import ai.intelliswarm.meetingmate.transcription.TranscriptionManager;

public class HomeFragment extends Fragment {
    
    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;
    private AudioRecordingService recordingService;
    private boolean isServiceBound = false;
    private boolean isRecording = false;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private long recordingStartTime;
    
    private MeetingFileManager fileManager;
    private CalendarService calendarService;
    private OpenAIService openAIService;
    private SettingsManager settingsManager;
    private TranscriptionManager transcriptionManager;
    
    private List<CalendarService.EventInfo> todayEvents = new ArrayList<>();

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioRecordingService.LocalBinder binder = (AudioRecordingService.LocalBinder) service;
            recordingService = binder.getService();
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    private BroadcastReceiver recordingStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isRecording = intent.getBooleanExtra("isRecording", false);
            
            if (!isRecording) {
                String filePath = intent.getStringExtra("filePath");
                long duration = intent.getLongExtra("duration", 0);
                processRecording(filePath, duration);
            }
            
            updateRecordingUI(isRecording);
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize services
        fileManager = new MeetingFileManager(requireContext());
        calendarService = new CalendarService(requireContext());
        settingsManager = SettingsManager.getInstance(requireContext());
        
        if (settingsManager.hasOpenAIApiKey()) {
            Log.d(TAG, "Initializing OpenAI service with saved API key");
            openAIService = new OpenAIService(settingsManager.getOpenAIApiKey());
        } else {
            Log.w(TAG, "No OpenAI API key found - OpenAI service not initialized");
        }

        setupUI();
        requestPermissions();
        
        return root;
    }

    private void setupUI() {
        // Record button
        binding.buttonRecord.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        // Pause button
        binding.buttonPause.setOnClickListener(v -> {
            if (recordingService != null) {
                recordingService.pauseRecording();
                binding.buttonPause.setText("Resume");
            }
        });

        // Stop button
        binding.buttonStop.setOnClickListener(v -> stopRecording());

        // Calendar checkbox
        binding.checkboxLinkCalendar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Calendar events are loaded after permissions are granted
                binding.spinnerCalendarEvents.setVisibility(View.VISIBLE);
            } else {
                binding.spinnerCalendarEvents.setVisibility(View.GONE);
            }
        });

        // View recordings button
        binding.buttonViewRecordings.setOnClickListener(v -> {
            // Navigate to dashboard fragment
            // This would be implemented based on your navigation setup
            Toast.makeText(getContext(), "Navigate to recordings list", Toast.LENGTH_SHORT).show();
        });

        // Setup timer runnable
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsedMillis = System.currentTimeMillis() - recordingStartTime;
                    int seconds = (int) (elapsedMillis / 1000) % 60;
                    int minutes = (int) ((elapsedMillis / (1000 * 60)) % 60);
                    int hours = (int) ((elapsedMillis / (1000 * 60 * 60)) % 24);
                    
                    String time = String.format(Locale.getDefault(), "%02d:%02d:%02d", 
                        hours, minutes, seconds);
                    binding.textRecordingTime.setText(time);
                    
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        
        // Always request calendar permissions to show calendar events
        permissions.add(Manifest.permission.READ_CALENDAR);
        permissions.add(Manifest.permission.WRITE_CALENDAR);

        Dexter.withContext(getContext())
            .withPermissions(permissions)
            .withListener(new MultiplePermissionsListener() {
                @Override
                public void onPermissionsChecked(MultiplePermissionsReport report) {
                    if (report.areAllPermissionsGranted()) {
                        Log.d(TAG, "All permissions granted, loading calendar events");
                        loadCalendarEvents();
                    } else {
                        Toast.makeText(getContext(), 
                            "Please grant all permissions to use the app", 
                            Toast.LENGTH_LONG).show();
                        
                        // Check which permissions were denied
                        for (PermissionDeniedResponse deniedResponse : report.getDeniedPermissionResponses()) {
                            Log.w(TAG, "Permission denied: " + deniedResponse.getPermissionName());
                        }
                    }
                }

                @Override
                public void onPermissionRationaleShouldBeShown(
                    List<PermissionRequest> permissions, PermissionToken token) {
                    token.continuePermissionRequest();
                }
            }).check();
    }

    private void startRecording() {
        if (!settingsManager.hasOpenAIApiKey()) {
            Toast.makeText(getContext(), 
                "Please set your OpenAI API key in settings first", 
                Toast.LENGTH_LONG).show();
            return;
        }

        Intent serviceIntent = new Intent(getContext(), AudioRecordingService.class);
        serviceIntent.setAction("START_RECORDING");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }
        
        if (!isServiceBound) {
            requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
        
        isRecording = true;
        recordingStartTime = System.currentTimeMillis();
        updateRecordingUI(true);
        timerHandler.post(timerRunnable);
    }

    private void stopRecording() {
        if (recordingService != null) {
            recordingService.stopRecording();
        }
        
        isRecording = false;
        updateRecordingUI(false);
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateRecordingUI(boolean recording) {
        if (recording) {
            binding.buttonRecord.setText("Recording");
            binding.buttonRecord.setEnabled(false);
            binding.layoutRecordingControls.setVisibility(View.VISIBLE);
            binding.textRecordingStatus.setText("Recording in progress...");
            binding.editMeetingTitle.setEnabled(false);
            binding.checkboxLinkCalendar.setEnabled(false);
        } else {
            binding.buttonRecord.setText("Start");
            binding.buttonRecord.setEnabled(true);
            binding.layoutRecordingControls.setVisibility(View.GONE);
            binding.textRecordingStatus.setText("Ready to record");
            binding.textRecordingTime.setText("00:00:00");
            binding.editMeetingTitle.setEnabled(true);
            binding.checkboxLinkCalendar.setEnabled(true);
        }
    }

    private void processRecording(String audioFilePath, long duration) {
        if (audioFilePath == null || !new File(audioFilePath).exists()) {
            Toast.makeText(getContext(), "Recording failed", Toast.LENGTH_SHORT).show();
            return;
        }

        String meetingId = fileManager.generateMeetingId();
        String meetingTitle = binding.editMeetingTitle.getText().toString();
        if (meetingTitle.isEmpty()) {
            meetingTitle = "Meeting " + new Date().toString();
        }

        // Save audio file
        File audioFile = new File(audioFilePath);
        File savedAudioFile = fileManager.saveAudioFile(meetingId, audioFile);

        // Get selected calendar event if any
        CalendarService.EventInfo selectedEvent = null;
        if (binding.checkboxLinkCalendar.isChecked() && 
            binding.spinnerCalendarEvents.getSelectedItem() != null) {
            selectedEvent = (CalendarService.EventInfo) binding.spinnerCalendarEvents.getSelectedItem();
        }

        // Process with OpenAI
        if (openAIService != null && savedAudioFile != null) {
            processWithOpenAI(meetingId, meetingTitle, savedAudioFile, selectedEvent);
        }

        // Clear form
        binding.editMeetingTitle.setText("");
        binding.checkboxLinkCalendar.setChecked(false);
        
        Toast.makeText(getContext(), "Recording saved. Processing transcription...", 
            Toast.LENGTH_LONG).show();
    }

    private void processWithOpenAI(String meetingId, String meetingTitle, 
                                  File audioFile, CalendarService.EventInfo calendarEvent) {
        Log.d(TAG, "Starting OpenAI processing for meeting: " + meetingTitle);
        
        // Check if OpenAI service is available
        if (openAIService == null) {
            Log.e(TAG, "OpenAI service not available - not initialized");
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), 
                    "OpenAI service not available. Please configure your API key in settings.", 
                    Toast.LENGTH_LONG).show();
            });
            return;
        }
        
        // Double-check API key is still valid
        if (!settingsManager.hasOpenAIApiKey()) {
            Log.e(TAG, "OpenAI API key missing during processing");
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), 
                    "OpenAI API key missing. Please configure it in settings.", 
                    Toast.LENGTH_LONG).show();
            });
            return;
        }
        
        // Transcribe audio
        openAIService.transcribeAudio(audioFile, new OpenAIService.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, org.json.JSONArray segments) {
                // Save transcript
                Date meetingDate = new Date();
                fileManager.saveTranscript(meetingId, meetingTitle, transcript, meetingDate);
                
                // Generate summary
                openAIService.generateSummary(transcript, meetingTitle, 
                    new OpenAIService.SummaryCallback() {
                        @Override
                        public void onSuccess(String summary) {
                            // Save summary
                            fileManager.saveSummary(meetingId, meetingTitle, summary, meetingDate);
                            
                            // Update calendar if linked
                            if (calendarEvent != null) {
                                calendarService.updateCalendarEvent(calendarEvent.id, summary);
                            }
                            
                            // Save metadata
                            fileManager.saveMeetingMetadata(
                                meetingId, 
                                meetingTitle, 
                                meetingDate,
                                audioFile.getAbsolutePath(),
                                calendarEvent != null ? String.valueOf(calendarEvent.id) : null
                            );
                            
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), 
                                    "Meeting processed successfully!", 
                                    Toast.LENGTH_LONG).show();
                                    
                                // Show transcript in dialog
                                showTranscriptDialog(meetingTitle, transcript, summary);
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), 
                                    "Summary generation failed: " + error, 
                                    Toast.LENGTH_LONG).show();
                            });
                        }
                    });
            }
            
            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), 
                        "Transcription failed: " + error, 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadCalendarEvents() {
        Log.d(TAG, "Loading calendar events...");
        todayEvents = calendarService.getTodayEvents();
        
        Log.d(TAG, "Found " + todayEvents.size() + " calendar events for today");
        for (CalendarService.EventInfo event : todayEvents) {
            Log.d(TAG, "Event: " + event.title + " at " + event.startTime);
        }
        
        if (todayEvents.isEmpty()) {
            Log.d(TAG, "No calendar events found, adding manual event option");
            todayEvents.add(createManualEvent());
        }
        
        ArrayAdapter<CalendarService.EventInfo> adapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            todayEvents
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCalendarEvents.setAdapter(adapter);
        
        Log.d(TAG, "Calendar events loaded into spinner");
    }
    
    private void showTranscriptDialog(String meetingTitle, String transcript, String summary) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Meeting: " + meetingTitle);
        
        // Create scrollable text view
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        android.widget.TextView textView = new android.widget.TextView(requireContext());
        textView.setPadding(50, 50, 50, 50);
        textView.setTextSize(14);
        
        // Build content string
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("📝 TRANSCRIPT:\n\n").append(transcript);
        if (summary != null && !summary.isEmpty()) {
            contentBuilder.append("\n\n📊 SUMMARY:\n\n").append(summary);
        }
        final String content = contentBuilder.toString();
        textView.setText(content);
        
        scrollView.addView(textView);
        builder.setView(scrollView);
        
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        builder.setNegativeButton("Share", (dialog, which) -> {
            // Share functionality
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, content);
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Meeting: " + meetingTitle);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share transcript"));
        });
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make dialog larger
        if (dialog.getWindow() != null) {
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = (int)(getResources().getDisplayMetrics().heightPixels * 0.8);
            dialog.getWindow().setAttributes(lp);
        }
    }

    private CalendarService.EventInfo createManualEvent() {
        CalendarService.EventInfo manualEvent = new CalendarService.EventInfo();
        manualEvent.title = "Create new calendar event";
        manualEvent.id = -1;
        return manualEvent;
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("RECORDING_STATE_CHANGED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(recordingStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(recordingStateReceiver, filter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(recordingStateReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        timerHandler.removeCallbacks(timerRunnable);
        binding = null;
    }
}