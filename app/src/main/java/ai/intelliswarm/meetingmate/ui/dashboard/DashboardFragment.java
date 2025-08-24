package ai.intelliswarm.meetingmate.ui.dashboard;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import ai.intelliswarm.meetingmate.R;
import ai.intelliswarm.meetingmate.service.CalendarService;
import ai.intelliswarm.meetingmate.ui.calendar.CalendarEventsAdapter;
import ai.intelliswarm.meetingmate.ui.transcription.TranscriptLinkActivity;
import ai.intelliswarm.meetingmate.databinding.FragmentDashboardBinding;
import ai.intelliswarm.meetingmate.analytics.AppLogger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {
    
    private static final String TAG = "DashboardFragment";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.d(TAG, "onCreate called");
    }
    
    @Override
    public void onStart() {
        super.onStart();
        AppLogger.d(TAG, "onStart called");
    }
    
    @Override
    public void onResume() {
        super.onResume();
        AppLogger.d(TAG, "onResume called - Fragment is now visible");
    }
    
    @Override
    public void onPause() {
        super.onPause();
        AppLogger.d(TAG, "onPause called");
    }
    
    @Override
    public void onStop() {
        super.onStop();
        AppLogger.d(TAG, "onStop called");
    }
    
    
    private FragmentDashboardBinding binding;
    private MaterialTextView selectedDateText;
    private MaterialButton refreshButton;
    private MaterialButton selectDateButton;
    private RecyclerView meetingsRecyclerView;
    private CalendarEventsAdapter meetingsAdapter;
    private CalendarService calendarService;
    private Date selectedDate;
    private SimpleDateFormat dateFormat;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        try {
            AppLogger.lifecycle("DashboardFragment", "onCreateView - STARTED");
            AppLogger.d(TAG, "=== DASHBOARD FRAGMENT CREATION ===");
            AppLogger.d(TAG, "Container: " + (container != null ? container.getClass().getName() : "null"));
            AppLogger.d(TAG, "SavedInstanceState: " + (savedInstanceState != null));
            
            binding = FragmentDashboardBinding.inflate(inflater, container, false);
            View root = binding.getRoot();
            
            AppLogger.d(TAG, "View inflated successfully");
            AppLogger.d(TAG, "Root view class: " + root.getClass().getName());
            AppLogger.d(TAG, "Fragment view inflated successfully");
            
            // Initialize services and date handling
            calendarService = new CalendarService(requireContext());
            selectedDate = new Date(); // Default to today
            dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
            AppLogger.d(TAG, "Services initialized");
            
            initializeViews(root);
            AppLogger.d(TAG, "Views initialized");
            
            setupRecyclerView();
            AppLogger.d(TAG, "RecyclerView setup completed");
            
            setupClickListeners();
            AppLogger.d(TAG, "Click listeners setup completed");
            
            updateDateDisplay();
            AppLogger.d(TAG, "Date display updated");
            
            refreshMeetings();
            AppLogger.d(TAG, "Meeting refresh initiated");
            
            AppLogger.i(TAG, "DashboardFragment initialized successfully");
            return root;
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error initializing DashboardFragment", e);
            
            // Return a simple error view if initialization fails
            android.widget.TextView errorView = new android.widget.TextView(requireContext());
            errorView.setText("Error loading meetings. Please check logs.");
            errorView.setGravity(android.view.Gravity.CENTER);
            errorView.setPadding(32, 32, 32, 32);
            return errorView;
        }
    }

    private void initializeViews(View root) {
        selectedDateText = binding.textSelectedDate;
        refreshButton = binding.buttonRefreshMeetings;
        selectDateButton = binding.buttonSelectDate;
        meetingsRecyclerView = binding.recyclerMeetings;
    }
    
    private void setupRecyclerView() {
        try {
            meetingsAdapter = new CalendarEventsAdapter(new ArrayList<>(), this::onMeetingSelected);
            meetingsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            meetingsRecyclerView.setAdapter(meetingsAdapter);
            AppLogger.d(TAG, "RecyclerView setup completed");
        } catch (Exception e) {
            AppLogger.e(TAG, "Error setting up RecyclerView", e);
        }
    }
    
    private void setupClickListeners() {
        try {
            refreshButton.setOnClickListener(v -> {
                AppLogger.userAction("DashboardFragment", "refresh_clicked", selectedDate.toString());
                refreshMeetings();
            });
            selectDateButton.setOnClickListener(v -> {
                AppLogger.userAction("DashboardFragment", "date_picker_clicked", null);
                showDatePicker();
            });
            AppLogger.d(TAG, "Click listeners setup completed");
        } catch (Exception e) {
            AppLogger.e(TAG, "Error setting up click listeners", e);
        }
    }
    
    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(selectedDate);
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireContext(),
            (view, year, monthOfYear, dayOfMonth) -> {
                Calendar newDate = Calendar.getInstance();
                newDate.set(year, monthOfYear, dayOfMonth);
                selectedDate = newDate.getTime();
                updateDateDisplay();
                refreshMeetings();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        
        datePickerDialog.show();
    }
    
    private void updateDateDisplay() {
        if (selectedDateText != null) {
            String formattedDate = dateFormat.format(selectedDate);
            
            // Add "Today" indicator if it's today's date
            Calendar today = Calendar.getInstance();
            Calendar selected = Calendar.getInstance();
            selected.setTime(selectedDate);
            
            if (today.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR)) {
                formattedDate += " (Today)";
            }
            
            selectedDateText.setText(formattedDate);
        }
    }
    
    private void refreshMeetings() {
        AppLogger.d(TAG, "Refreshing meetings for date: " + selectedDate);
        
        try {
            // Safety check
            if (calendarService == null) {
                AppLogger.e(TAG, "CalendarService is null, cannot refresh meetings");
                Toast.makeText(requireContext(), "Calendar service not available", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (meetingsAdapter == null) {
                AppLogger.e(TAG, "MeetingsAdapter is null, cannot refresh meetings");
                Toast.makeText(requireContext(), "Meetings display not available", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Show loading state
            refreshButton.setEnabled(false);
            refreshButton.setText("Loading...");
            
            // Get meetings for the selected date
            new Thread(() -> {
                try {
                    List<CalendarService.EventInfo> meetings = calendarService.getEventsForDate(selectedDate);
                    AppLogger.d(TAG, "Retrieved " + meetings.size() + " meetings");
                    
                    // Update UI on main thread
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        requireActivity().runOnUiThread(() -> {
                            try {
                                meetingsAdapter.updateEvents(meetings);
                                refreshButton.setEnabled(true);
                                refreshButton.setText("🔄 Refresh");
                                
                                String message = meetings.isEmpty() ? 
                                    "No meetings found for this date" : 
                                    "Found " + meetings.size() + " meeting(s)";
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                                
                                AppLogger.performance("refreshMeetings", System.currentTimeMillis() - System.currentTimeMillis(), System.currentTimeMillis());
                            } catch (Exception uiException) {
                                AppLogger.e(TAG, "Error updating UI after loading meetings", uiException);
                            }
                        });
                    }
                    
                } catch (Exception e) {
                    AppLogger.e(TAG, "Error refreshing meetings", e);
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        requireActivity().runOnUiThread(() -> {
                            refreshButton.setEnabled(true);
                            refreshButton.setText("🔄 Refresh");
                            Toast.makeText(requireContext(), "Error loading meetings: " + e.getMessage(), 
                                           Toast.LENGTH_LONG).show();
                        });
                    }
                }
            }).start();
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error in refreshMeetings method", e);
            refreshButton.setEnabled(true);
            refreshButton.setText("🔄 Refresh");
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void onMeetingSelected(CalendarService.EventInfo meeting) {
        Log.d(TAG, "Meeting selected: " + meeting.title + " (ID: " + meeting.id + ")");
        
        // Launch transcript linking activity
        Intent intent = new Intent(requireContext(), TranscriptLinkActivity.class);
        intent.putExtra("event_id", meeting.id);
        intent.putExtra("event_title", meeting.title);
        intent.putExtra("event_description", meeting.description);
        intent.putExtra("event_location", meeting.location);
        intent.putExtra("event_start_time", meeting.startTime.getTime());
        if (meeting.endTime != null) {
            intent.putExtra("event_end_time", meeting.endTime.getTime());
        }
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        AppLogger.lifecycle("DashboardFragment", "onDestroyView");
        
        try {
            // Clean up references to prevent memory leaks
            if (meetingsAdapter != null) {
                meetingsAdapter.updateEvents(new ArrayList<>());
            }
            meetingsAdapter = null;
            calendarService = null;
            selectedDate = null;
            dateFormat = null;
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error in onDestroyView", e);
        }
        
        binding = null;
    }
}