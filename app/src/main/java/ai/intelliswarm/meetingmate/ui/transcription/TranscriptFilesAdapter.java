package ai.intelliswarm.meetingmate.ui.transcription;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import ai.intelliswarm.meetingmate.R;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TranscriptFilesAdapter extends RecyclerView.Adapter<TranscriptFilesAdapter.TranscriptViewHolder> {

    private List<TranscriptLinkActivity.TranscriptFileInfo> transcripts;
    private final OnTranscriptClickListener onTranscriptClickListener;
    private final SimpleDateFormat dateFormat;

    public interface OnTranscriptClickListener {
        void onTranscriptClick(TranscriptLinkActivity.TranscriptFileInfo transcriptInfo);
    }

    public TranscriptFilesAdapter(List<TranscriptLinkActivity.TranscriptFileInfo> transcripts, 
                                  OnTranscriptClickListener listener) {
        this.transcripts = transcripts;
        this.onTranscriptClickListener = listener;
        this.dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public TranscriptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcript_file, parent, false);
        return new TranscriptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TranscriptViewHolder holder, int position) {
        TranscriptLinkActivity.TranscriptFileInfo transcript = transcripts.get(position);
        holder.bind(transcript, onTranscriptClickListener);
    }

    @Override
    public int getItemCount() {
        return transcripts != null ? transcripts.size() : 0;
    }

    public void updateTranscripts(List<TranscriptLinkActivity.TranscriptFileInfo> newTranscripts) {
        this.transcripts = newTranscripts;
        notifyDataSetChanged();
    }

    class TranscriptViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView fileNameText;
        private final TextView lastModifiedText;
        private final TextView fileSizeText;

        public TranscriptViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_transcript);
            fileNameText = itemView.findViewById(R.id.text_file_name);
            lastModifiedText = itemView.findViewById(R.id.text_last_modified);
            fileSizeText = itemView.findViewById(R.id.text_file_size);
        }

        public void bind(TranscriptLinkActivity.TranscriptFileInfo transcriptInfo, 
                        OnTranscriptClickListener listener) {
            fileNameText.setText(transcriptInfo.fileName);
            lastModifiedText.setText("Modified: " + dateFormat.format(transcriptInfo.lastModified));
            
            String sizeText = formatFileSize(transcriptInfo.fileSize);
            fileSizeText.setText(sizeText);
            
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTranscriptClick(transcriptInfo);
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