package ai.intelliswarm.meetingmate.ui.home;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.view.animation.AccelerateDecelerateInterpolator;
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
import java.util.Calendar;
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
import ai.intelliswarm.meetingmate.transcription.TranscriptionProvider;
import ai.intelliswarm.meetingmate.transcription.AndroidSpeechProvider;

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
    private List<CalendarService.CalendarSource> calendarSources = new ArrayList<>();
    private String selectedCalendarSource = "test";
    private ObjectAnimator gradientAnimator;
    
    private AndroidSpeechProvider androidSpeechProvider;
    private String liveTranscript = "";

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
            Log.d(TAG, "Recording state changed: isRecording = " + isRecording);
            
            if (!isRecording) {
                String filePath = intent.getStringExtra("filePath");
                long duration = intent.getLongExtra("duration", 0);
                Log.d(TAG, "Recording stopped - filePath: " + filePath + ", duration: " + duration);
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
        transcriptionManager = new TranscriptionManager(requireContext());
        
        // Get Android Speech Provider for live transcription
        androidSpeechProvider = (AndroidSpeechProvider) transcriptionManager.getProvider(
            TranscriptionProvider.ProviderType.ANDROID_SPEECH
        );
        
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
                binding.layoutCalendarControls.setVisibility(View.VISIBLE);
                binding.spinnerCalendarEvents.setVisibility(View.VISIBLE);
                loadCalendarSources();
            } else {
                binding.layoutCalendarControls.setVisibility(View.GONE);
                binding.spinnerCalendarEvents.setVisibility(View.GONE);
            }
        });
        
        // Refresh calendar button
        binding.buttonRefreshCalendar.setOnClickListener(v -> {
            Log.d(TAG, "Refresh calendar button clicked");
            refreshCalendarEvents();
        });
        
        // Calendar source selection
        binding.spinnerCalendarSource.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (calendarSources != null && position < calendarSources.size()) {
                    CalendarService.CalendarSource selected = calendarSources.get(position);
                    selectedCalendarSource = selected.type;
                    Log.d(TAG, "Calendar source selected: " + selected.name + " (" + selected.type + ")");
                    loadEventsForSelectedSource();
                }
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        // Handle calendar event selection
        binding.spinnerCalendarEvents.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (todayEvents != null && position < todayEvents.size()) {
                    CalendarService.EventInfo selected = todayEvents.get(position);
                    Log.d(TAG, "Calendar event selected: " + selected.title);
                }
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Do nothing
            }
        });

        // View recordings button
        binding.buttonViewRecordings.setOnClickListener(v -> {
            showPastRecordingsDialog();
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
        // Check if transcription provider is configured
        TranscriptionProvider.ProviderType selectedProvider = settingsManager.getSelectedTranscriptionProvider();
        TranscriptionProvider provider = transcriptionManager.getProvider(selectedProvider);
        
        if (provider != null && !provider.isConfigured()) {
            Toast.makeText(getContext(), 
                "Please configure " + selectedProvider.getDisplayName() + " in settings first", 
                Toast.LENGTH_LONG).show();
            return;
        }
        
        // Inform user about transcription approach
        if (selectedProvider == TranscriptionProvider.ProviderType.ANDROID_SPEECH) {
            if (androidSpeechProvider != null && androidSpeechProvider.isAvailable()) {
                Toast.makeText(getContext(), "Starting live transcription...", Toast.LENGTH_SHORT).show();
            } else {
                // Inform about fallback
                if (settingsManager.hasOpenAIApiKey()) {
                    Toast.makeText(getContext(), "Android Speech not available. Will use OpenAI for transcription.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "For transcription, please configure OpenAI Whisper in Settings.", Toast.LENGTH_LONG).show();
                }
            }
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
        liveTranscript = "";
        
        // Start live transcription if using Android Speech
        if (selectedProvider == TranscriptionProvider.ProviderType.ANDROID_SPEECH && androidSpeechProvider != null) {
            Log.d(TAG, "🎤 RECORDING STARTED - Attempting to start live transcription...");
            startLiveTranscription();
        } else {
            Log.w(TAG, "⚠️ Live transcription NOT started - Provider: " + selectedProvider + ", AndroidSpeechProvider: " + (androidSpeechProvider != null ? "available" : "null"));
        }
        
        updateRecordingUI(true);
        timerHandler.post(timerRunnable);
    }

    private void stopRecording() {
        if (recordingService != null) {
            recordingService.stopRecording();
        }
        
        // Stop live transcription if running
        if (androidSpeechProvider != null && androidSpeechProvider.isListening()) {
            Log.d(TAG, "🛑 RECORDING STOPPED - Stopping live transcription...");
            Log.d(TAG, "Current transcript before stopping: '" + liveTranscript + "'");
            androidSpeechProvider.stopLiveTranscription();
        } else {
            Log.w(TAG, "⚠️ Live transcription was not running when recording stopped");
            Log.w(TAG, "AndroidSpeechProvider: " + (androidSpeechProvider != null ? "available" : "null"));
            Log.w(TAG, "Is listening: " + (androidSpeechProvider != null ? androidSpeechProvider.isListening() : "N/A"));
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
            startGradientAnimation();
        } else {
            binding.buttonRecord.setText("Start");
            binding.buttonRecord.setEnabled(true);
            binding.layoutRecordingControls.setVisibility(View.GONE);
            binding.textRecordingStatus.setText("Ready to record");
            binding.textRecordingTime.setText("00:00:00");
            binding.editMeetingTitle.setEnabled(true);
            binding.checkboxLinkCalendar.setEnabled(true);
            stopGradientAnimation();
        }
    }

    private void processRecording(String audioFilePath, long duration) {
        Log.d(TAG, "Processing recording - audioFilePath: " + audioFilePath + ", duration: " + duration);
        
        if (audioFilePath == null || !new File(audioFilePath).exists()) {
            Log.e(TAG, "Recording file not found or null: " + audioFilePath);
            Toast.makeText(getContext(), "Recording failed - file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String meetingId = fileManager.generateMeetingId();
        String meetingTitle = binding.editMeetingTitle.getText().toString();
        if (meetingTitle.isEmpty()) {
            meetingTitle = "Meeting " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new Date());
        }
        
        Log.d(TAG, "Generated meeting - ID: " + meetingId + ", Title: " + meetingTitle);

        // Save audio file
        File audioFile = new File(audioFilePath);
        Log.d(TAG, "Original audio file size: " + audioFile.length() + " bytes");
        
        File savedAudioFile = fileManager.saveAudioFile(meetingId, audioFile);
        Log.d(TAG, "Saved audio file: " + (savedAudioFile != null ? savedAudioFile.getAbsolutePath() : "null"));

        // Get selected calendar event if any
        CalendarService.EventInfo selectedEvent = null;
        if (binding.checkboxLinkCalendar.isChecked() && 
            binding.spinnerCalendarEvents.getSelectedItem() != null) {
            selectedEvent = (CalendarService.EventInfo) binding.spinnerCalendarEvents.getSelectedItem();
        }

        // Process with selected transcription provider
        if (savedAudioFile != null) {
            Log.d(TAG, "📋 TRANSCRIPTION DEBUG:");
            Log.d(TAG, "   Selected provider: " + settingsManager.getSelectedTranscriptionProvider());
            Log.d(TAG, "   Live transcript length: " + liveTranscript.length());
            Log.d(TAG, "   Live transcript content: " + (liveTranscript.isEmpty() ? "EMPTY" : "'" + liveTranscript.substring(0, Math.min(100, liveTranscript.length())) + "...'")); 
            Log.d(TAG, "   AndroidSpeechProvider available: " + (androidSpeechProvider != null ? androidSpeechProvider.isAvailable() : "null"));
            processWithTranscription(meetingId, meetingTitle, savedAudioFile, selectedEvent);
        } else {
            Log.e(TAG, "Failed to save audio file - cannot process transcription");
            Toast.makeText(getContext(), "Failed to save recording", Toast.LENGTH_SHORT).show();
        }

        // Clear form
        binding.editMeetingTitle.setText("");
        binding.checkboxLinkCalendar.setChecked(false);
        
        Toast.makeText(getContext(), "Recording saved. Processing transcription...", 
            Toast.LENGTH_LONG).show();
    }

    private void processWithTranscription(String meetingId, String meetingTitle, 
                                        File audioFile, CalendarService.EventInfo calendarEvent) {
        Log.d(TAG, "Starting transcription processing for meeting: " + meetingTitle);
        
        TranscriptionProvider.ProviderType selectedProvider = settingsManager.getSelectedTranscriptionProvider();
        
        // For Android Speech, use the live transcript if available, otherwise fallback to OpenAI
        if (selectedProvider == TranscriptionProvider.ProviderType.ANDROID_SPEECH) {
            if (!liveTranscript.isEmpty()) {
                Log.d(TAG, "Using live transcript from Android Speech Recognition");
                processTranscriptionResult(meetingId, meetingTitle, liveTranscript, calendarEvent);
                return;
            } else {
                Log.w(TAG, "No live transcript available from Android Speech, checking for OpenAI fallback");
                
                // Check if OpenAI is available as fallback for file transcription
                if (settingsManager.hasOpenAIApiKey() && openAIService != null) {
                    Log.d(TAG, "Using OpenAI as fallback for file transcription");
                    processWithOpenAIFallback(meetingId, meetingTitle, audioFile, calendarEvent);
                    return;
                } else {
                    Log.w(TAG, "No transcription available - saving meeting without transcript");
                    
                    // Save the meeting even without transcription
                    Date meetingDate = new Date();
                    String noTranscriptMessage = "No transcript available. Android Speech Recognition requires live audio. Configure OpenAI Whisper in Settings for file transcription.";
                    
                    // Save meeting with placeholder transcript
                    fileManager.saveTranscript(meetingId, meetingTitle, noTranscriptMessage, meetingDate);
                    fileManager.saveMeetingMetadata(
                        meetingId, 
                        meetingTitle, 
                        meetingDate,
                        audioFile.getAbsolutePath(),
                        calendarEvent != null ? String.valueOf(calendarEvent.id) : null
                    );
                    
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), 
                            "Recording saved. Configure OpenAI Whisper in Settings for transcription.", 
                            Toast.LENGTH_LONG).show();
                        binding.textRecordingStatus.setText("Ready to record");
                        
                        // Show dialog with the saved meeting
                        showTranscriptDialog(meetingTitle, noTranscriptMessage, null);
                    });
                    return;
                }
            }
        }
        
        // Use TranscriptionManager for file-based transcription
        transcriptionManager.transcribe(audioFile, new TranscriptionProvider.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, String segments) {
                Log.d(TAG, "File transcription completed successfully");
                processTranscriptionResult(meetingId, meetingTitle, transcript, calendarEvent);
            }
            
            @Override
            public void onProgress(int progressPercent) {
                requireActivity().runOnUiThread(() -> {
                    binding.textRecordingStatus.setText("Transcribing... " + progressPercent + "%");
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Transcription failed: " + error);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), 
                        "Transcription failed: " + error, 
                        Toast.LENGTH_LONG).show();
                    binding.textRecordingStatus.setText("Ready to record");
                });
            }
        });
    }
    
    private void processTranscriptionResult(String meetingId, String meetingTitle, 
                                          String transcript, CalendarService.EventInfo calendarEvent) {
        Date meetingDate = new Date();
        
        // Save transcript
        fileManager.saveTranscript(meetingId, meetingTitle, transcript, meetingDate);
        
        // Generate summary using OpenAI if available and enabled
        if (openAIService != null && settingsManager.isAutoSummarizeEnabled()) {
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
                            null, // audioFile path not needed for metadata
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
                        Log.w(TAG, "Summary generation failed: " + error);
                        // Save without summary
                        saveMeetingWithoutSummary(meetingId, meetingTitle, transcript, meetingDate, calendarEvent);
                    }
                });
        } else {
            // Save without summary
            saveMeetingWithoutSummary(meetingId, meetingTitle, transcript, meetingDate, calendarEvent);
        }
    }
    
    private void saveMeetingWithoutSummary(String meetingId, String meetingTitle, String transcript, 
                                         Date meetingDate, CalendarService.EventInfo calendarEvent) {
        // Save metadata without summary
        fileManager.saveMeetingMetadata(
            meetingId, 
            meetingTitle, 
            meetingDate,
            null,
            calendarEvent != null ? String.valueOf(calendarEvent.id) : null
        );
        
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), 
                "Transcription completed!", 
                Toast.LENGTH_LONG).show();
                
            // Show transcript in dialog (no summary)
            showTranscriptDialog(meetingTitle, transcript, null);
        });
    }
    
    private void processWithOpenAIFallback(String meetingId, String meetingTitle, 
                                         File audioFile, CalendarService.EventInfo calendarEvent) {
        Log.d(TAG, "Using OpenAI as fallback for file transcription");
        
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), 
                "Android Speech not available for files. Using OpenAI Whisper...", 
                Toast.LENGTH_SHORT).show();
        });
        
        // Use OpenAI directly for file transcription
        openAIService.transcribeAudio(audioFile, new OpenAIService.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, org.json.JSONArray segments) {
                Log.d(TAG, "OpenAI fallback transcription completed successfully");
                processTranscriptionResult(meetingId, meetingTitle, transcript, calendarEvent);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "OpenAI fallback transcription failed: " + error);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), 
                        "Transcription failed: " + error, 
                        Toast.LENGTH_LONG).show();
                    binding.textRecordingStatus.setText("Ready to record");
                });
            }
        });
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

    private void loadCalendarSources() {
        Log.d(TAG, "Loading calendar sources...");
        calendarSources = calendarService.getAvailableCalendarSources();
        
        Log.d(TAG, "Found " + calendarSources.size() + " calendar sources");
        for (CalendarService.CalendarSource source : calendarSources) {
            Log.d(TAG, "Source: " + source.name + " (" + source.type + ")");
        }
        
        ArrayAdapter<CalendarService.CalendarSource> adapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            calendarSources
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCalendarSource.setAdapter(adapter);
        
        // Auto-select first source
        if (!calendarSources.isEmpty()) {
            selectedCalendarSource = calendarSources.get(0).type;
            loadEventsForSelectedSource();
        }
        
        Log.d(TAG, "Calendar sources loaded into spinner");
    }
    
    private void loadEventsForSelectedSource() {
        Log.d(TAG, "Loading events for source: " + selectedCalendarSource);
        todayEvents = calendarService.getEventsForSource(selectedCalendarSource);
        
        Log.d(TAG, "Found " + todayEvents.size() + " events for source " + selectedCalendarSource);
        for (CalendarService.EventInfo event : todayEvents) {
            Log.d(TAG, "Event: " + event.title + " at " + event.startTime);
        }
        
        // Add manual event option if not test events
        if (!"test".equals(selectedCalendarSource)) {
            todayEvents.add(createManualEvent());
        }
        
        updateEventsSpinner();
    }
    
    private void refreshCalendarEvents() {
        Log.d(TAG, "Refreshing calendar events...");
        Toast.makeText(getContext(), "Refreshing calendar...", Toast.LENGTH_SHORT).show();
        
        if ("test".equals(selectedCalendarSource)) {
            // For test events, try to create real calendar events first
            Log.d(TAG, "Creating test calendar events...");
            boolean created = calendarService.createTestEvents();
            if (created) {
                Toast.makeText(getContext(), "Test calendar events created!", Toast.LENGTH_SHORT).show();
                // Reload sources to refresh available calendars
                loadCalendarSources();
            } else {
                Toast.makeText(getContext(), "Using demo events (no calendar account found)", Toast.LENGTH_LONG).show();
                loadEventsForSelectedSource();
            }
        } else {
            // Refresh events for current source
            loadEventsForSelectedSource();
        }
    }
    
    private void updateEventsSpinner() {
        ArrayAdapter<CalendarService.EventInfo> adapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            todayEvents
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCalendarEvents.setAdapter(adapter);
        
        Log.d(TAG, "Events spinner updated with " + todayEvents.size() + " events");
    }
    
    private void loadCalendarEvents() {
        // Legacy method - now calls loadCalendarSources for backward compatibility
        loadCalendarSources();
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

    private void showPastRecordingsDialog() {
        Log.d(TAG, "Showing past recordings dialog");
        
        // Get all meetings (you can also search by title)
        List<MeetingFileManager.MeetingInfo> meetings = new ArrayList<>();
        
        // Try to get today's meetings first
        Date today = new Date();
        meetings.addAll(fileManager.getMeetingsForDate(today));
        
        // Also try searching for all meetings with any title
        List<MeetingFileManager.MeetingInfo> allMeetings = fileManager.searchMeetingsByTitle("");
        for (MeetingFileManager.MeetingInfo meeting : allMeetings) {
            boolean alreadyAdded = false;
            for (MeetingFileManager.MeetingInfo existing : meetings) {
                if (existing.meetingId.equals(meeting.meetingId)) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                meetings.add(meeting);
            }
        }
        
        Log.d(TAG, "Found " + meetings.size() + " total meetings");
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Past Recordings");
        
        if (meetings.isEmpty()) {
            // No recordings found
            android.widget.TextView textView = new android.widget.TextView(requireContext());
            textView.setPadding(50, 50, 50, 50);
            textView.setText("No recordings found for today.\n\nRecord a meeting by pressing the record button and speaking.");
            textView.setTextSize(14);
            builder.setView(textView);
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        } else {
            // Sort meetings by date (most recent first)
            meetings.sort((m1, m2) -> m2.date.compareTo(m1.date));
            
            // Create a list of meeting titles with dates
            String[] meetingTitles = new String[meetings.size()];
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
            
            for (int i = 0; i < meetings.size(); i++) {
                MeetingFileManager.MeetingInfo meeting = meetings.get(i);
                meetingTitles[i] = dateFormat.format(meeting.date) + " - " + meeting.title;
            }
            
            // Show list of meetings
            builder.setItems(meetingTitles, (dialog, which) -> {
                MeetingFileManager.MeetingInfo selectedMeeting = meetings.get(which);
                showMeetingDetails(selectedMeeting);
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        }
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showMeetingDetails(MeetingFileManager.MeetingInfo meeting) {
        Log.d(TAG, "Showing details for meeting: " + meeting.title);
        
        // Load transcript and summary
        String transcript = fileManager.getTranscript(meeting.meetingId);
        String summary = fileManager.getSummary(meeting.meetingId);
        
        if (transcript == null && summary == null) {
            Toast.makeText(getContext(), "No transcript or summary found for this meeting", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show dialog with options to view transcript or add to calendar
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Meeting: " + meeting.title);
        
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());
        String message = "Date: " + dateFormat.format(meeting.date) + "\n\n" +
                        "Choose an action:";
        builder.setMessage(message);
        
        builder.setPositiveButton("View Transcript", (dialog, which) -> {
            showTranscriptDialog(meeting.title, 
                transcript != null ? transcript : "No transcript available",
                summary != null ? summary : "No summary available");
        });
        
        builder.setNegativeButton("Add to Calendar", (dialog, which) -> {
            showAddToCalendarDialog(meeting, transcript, summary);
        });
        
        builder.setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showAddToCalendarDialog(MeetingFileManager.MeetingInfo meeting, String transcript, String summary) {
        Log.d(TAG, "Showing add to calendar dialog for meeting: " + meeting.title);
        
        // Get available calendars
        List<CalendarService.CalendarInfo> calendars = calendarService.getAvailableCalendars();
        
        if (calendars.isEmpty()) {
            Toast.makeText(getContext(), "No calendars available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Add to Calendar");
        
        // Create array of calendar names
        String[] calendarNames = new String[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            CalendarService.CalendarInfo cal = calendars.get(i);
            calendarNames[i] = cal.displayName + " (" + cal.accountName + ")";
        }
        
        builder.setItems(calendarNames, (dialog, which) -> {
            CalendarService.CalendarInfo selectedCalendar = calendars.get(which);
            addMeetingToCalendar(selectedCalendar, meeting, transcript, summary);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void addMeetingToCalendar(CalendarService.CalendarInfo calendar, 
                                    MeetingFileManager.MeetingInfo meeting, 
                                    String transcript, String summary) {
        Log.d(TAG, "Adding meeting to calendar: " + calendar.displayName);
        
        // Create event description with transcript and summary
        StringBuilder description = new StringBuilder();
        description.append("Meeting recorded and transcribed by Meeting Mate\n\n");
        
        if (summary != null && !summary.isEmpty()) {
            description.append("SUMMARY:\n").append(summary).append("\n\n");
        }
        
        if (transcript != null && !transcript.isEmpty()) {
            description.append("TRANSCRIPT:\n").append(transcript);
        }
        
        // Calculate end time (assume 1 hour if we don't have duration info)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(meeting.date);
        Date startTime = cal.getTime();
        cal.add(java.util.Calendar.HOUR, 1);
        Date endTime = cal.getTime();
        
        // Add to calendar
        long eventId = calendarService.addMeetingToCalendar(
            calendar.id, 
            meeting.title, 
            description.toString(), 
            startTime, 
            endTime, 
            "Meeting Room"
        );
        
        if (eventId != -1) {
            // Also add meeting notes as extended properties
            String transcriptPath = transcript != null ? "stored_locally" : null;
            String summaryPath = summary != null ? "stored_locally" : null;
            
            calendarService.addMeetingNotes(eventId, meeting.meetingId, transcriptPath, summaryPath);
            
            Toast.makeText(getContext(), 
                "Meeting added to " + calendar.displayName + " calendar!", 
                Toast.LENGTH_LONG).show();
                
            Log.d(TAG, "Meeting successfully added to calendar with event ID: " + eventId);
        } else {
            Toast.makeText(getContext(), 
                "Failed to add meeting to calendar", 
                Toast.LENGTH_SHORT).show();
                
            Log.e(TAG, "Failed to add meeting to calendar");
        }
    }
    
    private List<CalendarService.EventInfo> createDemoEvents() {
        List<CalendarService.EventInfo> demoEvents = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        
        // Demo Event 1: Morning Standup
        CalendarService.EventInfo event1 = new CalendarService.EventInfo();
        event1.id = 1001;
        event1.title = "Morning Standup";
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        event1.startTime = cal.getTime();
        cal.add(Calendar.HOUR, 1);
        event1.endTime = cal.getTime();
        event1.description = "Daily team sync";
        event1.location = "Conference Room A";
        demoEvents.add(event1);
        
        // Demo Event 2: Project Review
        CalendarService.EventInfo event2 = new CalendarService.EventInfo();
        event2.id = 1002;
        event2.title = "Project Review";
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 30);
        event2.startTime = cal.getTime();
        cal.add(Calendar.HOUR, 1);
        event2.endTime = cal.getTime();
        event2.description = "Q4 project status review";
        event2.location = "Zoom Meeting";
        demoEvents.add(event2);
        
        // Demo Event 3: Client Call
        CalendarService.EventInfo event3 = new CalendarService.EventInfo();
        event3.id = 1003;
        event3.title = "Client Call";
        cal.set(Calendar.HOUR_OF_DAY, 16);
        cal.set(Calendar.MINUTE, 0);
        event3.startTime = cal.getTime();
        cal.add(Calendar.MINUTE, 30);
        event3.endTime = cal.getTime();
        event3.description = "Weekly sync with client";
        event3.location = "Phone";
        demoEvents.add(event3);
        
        Log.d(TAG, "Created " + demoEvents.size() + " demo events for testing");
        return demoEvents;
    }
    
    private CalendarService.EventInfo createManualEvent() {
        CalendarService.EventInfo manualEvent = new CalendarService.EventInfo();
        manualEvent.title = "Create new calendar event";
        manualEvent.id = -1;
        return manualEvent;
    }

    private void startLiveTranscription() {
        if (androidSpeechProvider == null) {
            Log.e(TAG, "❌ Android Speech Provider is null - not initialized properly");
            Toast.makeText(getContext(), "Speech recognition not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!androidSpeechProvider.isAvailable()) {
            Log.w(TAG, "❌ Android Speech Provider not available for live transcription");
            Log.w(TAG, "SpeechRecognizer.isRecognitionAvailable: " + 
                android.speech.SpeechRecognizer.isRecognitionAvailable(requireContext()));
            Toast.makeText(getContext(), "Speech recognition not available on this device. Install Google app or speech services.", Toast.LENGTH_LONG).show();
            return;
        }
        
        Log.d(TAG, "✅ Starting live transcription - all checks passed");
        
        androidSpeechProvider.startLiveTranscription(new TranscriptionProvider.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcript, String segments) {
                Log.d(TAG, "Live transcription completed: " + transcript);
                liveTranscript = transcript;
                
                requireActivity().runOnUiThread(() -> {
                    // Update UI with final transcript
                    binding.textRecordingStatus.setText("Transcription complete: " + transcript.length() + " chars");
                });
            }
            
            @Override
            public void onProgress(int progressPercent) {
                // Not used for live transcription
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Live transcription error: " + error);
                requireActivity().runOnUiThread(() -> {
                    binding.textRecordingStatus.setText("Speech recognition error: " + error);
                    Toast.makeText(getContext(), "Speech recognition error: " + error, Toast.LENGTH_SHORT).show();
                });
                // Don't clear liveTranscript here - it might have partial content
            }
            
            @Override
            public void onPartialResult(String partialTranscript) {
                Log.d(TAG, "📝 Partial transcript received: '" + partialTranscript + "' (length: " + partialTranscript.length() + ")");
                
                // IMPORTANT: Update liveTranscript with partial results
                liveTranscript = partialTranscript;
                
                requireActivity().runOnUiThread(() -> {
                    // Show partial transcript in status
                    if (partialTranscript.length() > 50) {
                        binding.textRecordingStatus.setText("..." + partialTranscript.substring(partialTranscript.length() - 50));
                    } else if (!partialTranscript.isEmpty()) {
                        binding.textRecordingStatus.setText("📝 " + partialTranscript);
                    } else {
                        binding.textRecordingStatus.setText("🎤 Listening...");
                    }
                });
            }
        });
    }

    private void startGradientAnimation() {
        if (binding.gradientBackground != null) {
            binding.gradientBackground.setBackgroundResource(R.drawable.recording_gradient);
            binding.gradientBackground.setVisibility(View.VISIBLE);
            
            // Create fade-in animation
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(binding.gradientBackground, "alpha", 0f, 1f);
            fadeIn.setDuration(500);
            fadeIn.start();
            
            // Create pulsing animation
            gradientAnimator = ObjectAnimator.ofFloat(binding.gradientBackground, "alpha", 0.3f, 0.8f);
            gradientAnimator.setDuration(1500);
            gradientAnimator.setRepeatMode(ValueAnimator.REVERSE);
            gradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
            gradientAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            gradientAnimator.start();
            
            Log.d(TAG, "Gradient animation started");
        }
    }
    
    private void stopGradientAnimation() {
        if (binding.gradientBackground != null) {
            // Stop the pulsing animation
            if (gradientAnimator != null) {
                gradientAnimator.cancel();
                gradientAnimator = null;
            }
            
            // Create fade-out animation
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(binding.gradientBackground, "alpha", binding.gradientBackground.getAlpha(), 0f);
            fadeOut.setDuration(500);
            fadeOut.addListener(new android.animation.Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(android.animation.Animator animation) {}

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (binding != null && binding.gradientBackground != null) {
                        binding.gradientBackground.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {}

                @Override
                public void onAnimationRepeat(android.animation.Animator animation) {}
            });
            fadeOut.start();
            
            Log.d(TAG, "Gradient animation stopped");
        }
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
        
        // Clean up gradient animation
        if (gradientAnimator != null) {
            gradientAnimator.cancel();
            gradientAnimator = null;
        }
        
        binding = null;
    }
}