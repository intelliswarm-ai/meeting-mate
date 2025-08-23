package ai.intelliswarm.meetingmate.ui.calendar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import ai.intelliswarm.meetingmate.R;
import ai.intelliswarm.meetingmate.service.CalendarService;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class CalendarEventsAdapter extends RecyclerView.Adapter<CalendarEventsAdapter.EventViewHolder> {

    private List<CalendarService.EventInfo> events;
    private final OnEventClickListener onEventClickListener;
    private final SimpleDateFormat timeFormat;

    public interface OnEventClickListener {
        void onEventClick(CalendarService.EventInfo event);
    }

    public CalendarEventsAdapter(List<CalendarService.EventInfo> events, OnEventClickListener listener) {
        this.events = events;
        this.onEventClickListener = listener;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        CalendarService.EventInfo event = events.get(position);
        holder.bind(event, onEventClickListener);
    }

    @Override
    public int getItemCount() {
        return events != null ? events.size() : 0;
    }

    public void updateEvents(List<CalendarService.EventInfo> newEvents) {
        this.events = newEvents;
        notifyDataSetChanged();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView titleText;
        private final TextView timeText;
        private final TextView locationText;
        private final TextView descriptionText;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_event);
            titleText = itemView.findViewById(R.id.text_event_title);
            timeText = itemView.findViewById(R.id.text_event_time);
            locationText = itemView.findViewById(R.id.text_event_location);
            descriptionText = itemView.findViewById(R.id.text_event_description);
        }

        public void bind(CalendarService.EventInfo event, OnEventClickListener listener) {
            titleText.setText(event.title != null ? event.title : "Untitled Event");
            
            String timeText = "";
            if (event.startTime != null) {
                timeText = "⏰ " + timeFormat.format(event.startTime);
                if (event.endTime != null) {
                    timeText += " - " + timeFormat.format(event.endTime);
                }
            }
            this.timeText.setText(timeText);
            
            if (event.location != null && !event.location.trim().isEmpty()) {
                locationText.setText("📍 " + event.location);
                locationText.setVisibility(View.VISIBLE);
            } else {
                locationText.setVisibility(View.GONE);
            }
            
            if (event.description != null && !event.description.trim().isEmpty()) {
                descriptionText.setText(event.description);
                descriptionText.setVisibility(View.VISIBLE);
            } else {
                descriptionText.setVisibility(View.GONE);
            }
            
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEventClick(event);
                }
            });
        }
    }
}