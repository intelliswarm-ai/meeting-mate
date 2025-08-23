package ai.intelliswarm.meetingmate.analytics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import ai.intelliswarm.meetingmate.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogFilesAdapter extends RecyclerView.Adapter<LogFilesAdapter.LogFileViewHolder> {

    private List<LogViewerActivity.LogFileInfo> logFiles;
    private final OnLogFileClickListener onLogFileClickListener;
    private final SimpleDateFormat dateFormat;

    public interface OnLogFileClickListener {
        void onLogFileClick(LogViewerActivity.LogFileInfo logFileInfo);
    }

    public LogFilesAdapter(List<LogViewerActivity.LogFileInfo> logFiles, OnLogFileClickListener listener) {
        this.logFiles = logFiles;
        this.onLogFileClickListener = listener;
        this.dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public LogFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_file, parent, false);
        return new LogFileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogFileViewHolder holder, int position) {
        LogViewerActivity.LogFileInfo logFile = logFiles.get(position);
        holder.bind(logFile, onLogFileClickListener);
    }

    @Override
    public int getItemCount() {
        return logFiles != null ? logFiles.size() : 0;
    }

    public void updateLogFiles(List<LogViewerActivity.LogFileInfo> newLogFiles) {
        this.logFiles = newLogFiles;
        notifyDataSetChanged();
    }

    class LogFileViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView fileNameText;
        private final TextView fileTypeText;
        private final TextView fileSizeText;
        private final TextView lastModifiedText;

        public LogFileViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_log_file);
            fileNameText = itemView.findViewById(R.id.text_file_name);
            fileTypeText = itemView.findViewById(R.id.text_file_type);
            fileSizeText = itemView.findViewById(R.id.text_file_size);
            lastModifiedText = itemView.findViewById(R.id.text_last_modified);
        }

        public void bind(LogViewerActivity.LogFileInfo logFileInfo, OnLogFileClickListener listener) {
            fileNameText.setText(logFileInfo.name);
            fileTypeText.setText(logFileInfo.type);
            fileSizeText.setText(formatFileSize(logFileInfo.size));
            lastModifiedText.setText("Modified: " + dateFormat.format(new Date(logFileInfo.lastModified)));
            
            // Set different colors for different log types
            if (logFileInfo.type.contains("Crash")) {
                cardView.setCardBackgroundColor(itemView.getContext().getColor(android.R.color.holo_red_light));
                fileTypeText.setTextColor(itemView.getContext().getColor(android.R.color.white));
            } else if (logFileInfo.type.contains("Session")) {
                cardView.setCardBackgroundColor(itemView.getContext().getColor(android.R.color.holo_blue_light));
                fileTypeText.setTextColor(itemView.getContext().getColor(android.R.color.white));
            } else {
                cardView.setCardBackgroundColor(itemView.getContext().getColor(android.R.color.background_light));
                fileTypeText.setTextColor(itemView.getContext().getColor(android.R.color.black));
            }
            
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLogFileClick(logFileInfo);
                }
            });
        }
        
        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp - 1) + "";
            return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }
    }
}