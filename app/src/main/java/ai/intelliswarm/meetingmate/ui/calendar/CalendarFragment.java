package ai.intelliswarm.meetingmate.ui.calendar;

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
import ai.intelliswarm.meetingmate.ui.transcription.TranscriptLinkActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {
    
    private static final String TAG = "CalendarFragment";
    
    private MaterialTextView selectedDateText;
    private MaterialButton refreshButton;
    private MaterialButton selectDateButton;
    private RecyclerView eventsRecyclerView;
    private CalendarEventsAdapter eventsAdapter;
    private CalendarService calendarService;
    private Date selectedDate;
    private SimpleDateFormat dateFormat;
    
    public CalendarFragment() {
        // Required empty public constructor
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calendarService = new CalendarService(requireContext());
        selectedDate = new Date(); // Default to today
        dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_calendar, container, false);
        
        initializeViews(root);
        setupRecyclerView();
        setupClickListeners();
        updateDateDisplay();
        refreshEvents();
        
        return root;
    }
    
    private void initializeViews(View root) {
        selectedDateText = root.findViewById(R.id.text_selected_date);
        refreshButton = root.findViewById(R.id.button_refresh_events);
        selectDateButton = root.findViewById(R.id.button_select_date);
        eventsRecyclerView = root.findViewById(R.id.recycler_events);
    }
    
    private void setupRecyclerView() {
        eventsAdapter = new CalendarEventsAdapter(new ArrayList<>(), this::onEventSelected);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        eventsRecyclerView.setAdapter(eventsAdapter);
    }
    
    private void setupClickListeners() {
        refreshButton.setOnClickListener(v -> refreshEvents());
        selectDateButton.setOnClickListener(v -> showDatePicker());
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
                refreshEvents();
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
    
    private void refreshEvents() {
        Log.d(TAG, "Refreshing events for date: " + selectedDate);
        
        // Show loading state
        refreshButton.setEnabled(false);
        refreshButton.setText("Loading...");
        
        // Get events for the selected date
        new Thread(() -> {
            try {
                List<CalendarService.EventInfo> events = calendarService.getEventsForDate(selectedDate);
                
                // Update UI on main thread
                requireActivity().runOnUiThread(() -> {
                    eventsAdapter.updateEvents(events);
                    refreshButton.setEnabled(true);
                    refreshButton.setText("🔄 Refresh");
                    
                    String message = events.isEmpty() ? 
                        "No events found for this date" : 
                        "Found " + events.size() + " event(s)";
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing calendar events", e);
                requireActivity().runOnUiThread(() -> {
                    refreshButton.setEnabled(true);
                    refreshButton.setText("🔄 Refresh");
                    Toast.makeText(requireContext(), "Error loading events: " + e.getMessage(), 
                                   Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void onEventSelected(CalendarService.EventInfo event) {
        Log.d(TAG, "Event selected: " + event.title + " (ID: " + event.id + ")");
        
        // Launch transcript linking activity
        Intent intent = new Intent(requireContext(), TranscriptLinkActivity.class);
        intent.putExtra("event_id", event.id);
        intent.putExtra("event_title", event.title);
        intent.putExtra("event_description", event.description);
        intent.putExtra("event_location", event.location);
        intent.putExtra("event_start_time", event.startTime.getTime());
        if (event.endTime != null) {
            intent.putExtra("event_end_time", event.endTime.getTime());
        }
        startActivity(intent);
    }
}