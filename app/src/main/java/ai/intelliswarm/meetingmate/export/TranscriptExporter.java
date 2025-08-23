package ai.intelliswarm.meetingmate.export;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Export transcripts to multiple formats: TXT, PDF, DOCX, SRT, VTT, MD
 * All export formats available for free - no subscription needed!
 */
public class TranscriptExporter {
    
    private final Context context;
    private static final String EXPORT_DIR = "MeetingMate/Exports";
    
    public TranscriptExporter(Context context) {
        this.context = context;
    }
    
    /**
     * Export to plain text format
     */
    public File exportToTXT(String transcript, String meetingTitle) throws IOException {
        File exportDir = getExportDirectory();
        String fileName = sanitizeFileName(meetingTitle) + "_" + getTimestamp() + ".txt";
        File file = new File(exportDir, fileName);
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Meeting: " + meetingTitle + "\n");
            writer.write("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()) + "\n");
            writer.write("=" + "=".repeat(50) + "\n\n");
            writer.write(transcript);
        }
        
        return file;
    }
    
    /**
     * Export to PDF format with formatting
     */
    public File exportToPDF(String transcript, String meetingTitle, TranscriptSegments segments) throws Exception {
        File exportDir = getExportDirectory();
        String fileName = sanitizeFileName(meetingTitle) + "_" + getTimestamp() + ".pdf";
        File file = new File(exportDir, fileName);
        
        com.itextpdf.text.Document document = new com.itextpdf.text.Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();
        
        // Add title
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Paragraph title = new Paragraph(meetingTitle, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        
        // Add metadata
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY);
        Paragraph meta = new Paragraph("Generated on " + new Date() + " by MeetingMate", metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        document.add(meta);
        document.add(new Paragraph("\n"));
        
        // Add transcript with speaker formatting
        if (segments != null && segments.hasSegments()) {
            for (TranscriptSegments.Segment segment : segments.getSegments()) {
                // Speaker label
                Font speakerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
                Paragraph speaker = new Paragraph(segment.getSpeakerLabel() + ":", speakerFont);
                document.add(speaker);
                
                // Transcript text
                Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
                Paragraph text = new Paragraph(segment.getText(), textFont);
                text.setIndentationLeft(20);
                document.add(text);
                document.add(new Paragraph("\n"));
            }
        } else {
            // Plain transcript
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Paragraph text = new Paragraph(transcript, textFont);
            document.add(text);
        }
        
        document.close();
        return file;
    }
    
    /**
     * Export to DOCX format (Word document)
     */
    public File exportToDOCX(String transcript, String meetingTitle, TranscriptSegments segments) throws Exception {
        File exportDir = getExportDirectory();
        String fileName = sanitizeFileName(meetingTitle) + "_" + getTimestamp() + ".docx";
        File file = new File(exportDir, fileName);
        
        XWPFDocument document = new XWPFDocument();
        
        // Add title
        XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(meetingTitle);
        titleRun.setBold(true);
        titleRun.setFontSize(18);
        
        // Add date
        XWPFParagraph datePara = document.createParagraph();
        datePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun dateRun = datePara.createRun();
        dateRun.setText("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        dateRun.setFontSize(10);
        dateRun.setColor("808080");
        
        // Add blank line
        document.createParagraph();
        
        // Add transcript with formatting
        if (segments != null && segments.hasSegments()) {
            for (TranscriptSegments.Segment segment : segments.getSegments()) {
                // Speaker paragraph
                XWPFParagraph speakerPara = document.createParagraph();
                XWPFRun speakerRun = speakerPara.createRun();
                speakerRun.setText(segment.getSpeakerLabel() + ":");
                speakerRun.setBold(true);
                speakerRun.setFontSize(12);
                
                // Text paragraph
                XWPFParagraph textPara = document.createParagraph();
                textPara.setIndentationLeft(400);
                XWPFRun textRun = textPara.createRun();
                textRun.setText(segment.getText());
                textRun.setFontSize(11);
            }
        } else {
            // Plain transcript
            XWPFParagraph para = document.createParagraph();
            XWPFRun run = para.createRun();
            run.setText(transcript);
            run.setFontSize(11);
        }
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            document.write(out);
        }
        document.close();
        
        return file;
    }
    
    /**
     * Export to SRT subtitle format
     */
    public File exportToSRT(String transcript, String meetingTitle, TranscriptSegments segments) throws IOException {
        File exportDir = getExportDirectory();
        String fileName = sanitizeFileName(meetingTitle) + "_" + getTimestamp() + ".srt";
        File file = new File(exportDir, fileName);
        
        try (FileWriter writer = new FileWriter(file)) {
            if (segments != null && segments.hasTimestamps()) {
                int counter = 1;
                for (TranscriptSegments.Segment segment : segments.getSegments()) {
                    // Subtitle number
                    writer.write(counter + "\n");
                    
                    // Timestamp (format: 00:00:00,000 --> 00:00:05,000)
                    writer.write(formatSRTTime(segment.getStartTime()) + " --> " + 
                               formatSRTTime(segment.getEndTime()) + "\n");
                    
                    // Text (with speaker if available)
                    String text = segment.hasSpeaker() ? 
                        "[" + segment.getSpeakerLabel() + "] " + segment.getText() : 
                        segment.getText();
                    writer.write(text + "\n\n");
                    
                    counter++;
                }
            } else {
                // Create fake timestamps for plain transcript
                String[] sentences = transcript.split("\\. ");
                int timeOffset = 0;
                for (int i = 0; i < sentences.length; i++) {
                    writer.write((i + 1) + "\n");
                    writer.write(formatSRTTime(timeOffset) + " --> " + 
                               formatSRTTime(timeOffset + 5000) + "\n");
                    writer.write(sentences[i].trim() + "\n\n");
                    timeOffset += 5000; // 5 seconds per sentence
                }
            }
        }
        
        return file;
    }
    
    /**
     * Export to WebVTT format (for web video)
     */
    public File exportToVTT(String transcript, String meetingTitle, TranscriptSegments segments) throws IOException {
        File exportDir = getExportDirectory();
        String fileName = sanitizeFileName(meetingTitle) + "_" + getTimestamp() + ".vtt";
        File file = new File(exportDir, fileName);
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("WEBVTT\n\n");
            
            if (segments != null && segments.hasTimestamps()) {
                for (TranscriptSegments.Segment segment : segments.getSegments()) {
                    // Timestamp
                    writer.write(formatVTTTime(segment.getStartTime()) + " --> " + 
                               formatVTTTime(segment.getEndTime()) + "\n");
                    
                    // Text with speaker
                    if (segment.hasSpeaker()) {
                        writer.write("<v " + segment.getSpeakerLabel() + ">");
                    }
                    writer.write(segment.getText());
                    if (segment.hasSpeaker()) {
                        writer.write("</v>");
                    }
                    writer.write("\n\n");
                }
            } else {
                // Simple conversion without timestamps
                writer.write("00:00:00.000 --> 99:59:59.999\n");
                writer.write(transcript + "\n");
            }
        }
        
        return file;
    }
    
    /**
     * Export to Markdown format
     */
    public File exportToMarkdown(String transcript, String meetingTitle, String summary, TranscriptSegments segments) throws IOException {
        File exportDir = getExportDirectory();
        String fileName = sanitizeFileName(meetingTitle) + "_" + getTimestamp() + ".md";
        File file = new File(exportDir, fileName);
        
        try (FileWriter writer = new FileWriter(file)) {
            // Header
            writer.write("# " + meetingTitle + "\n\n");
            writer.write("**Date:** " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()) + "\n\n");
            
            // Summary section if available
            if (summary != null && !summary.isEmpty()) {
                writer.write("## Summary\n\n");
                writer.write(summary + "\n\n");
            }
            
            // Transcript section
            writer.write("## Transcript\n\n");
            
            if (segments != null && segments.hasSegments()) {
                for (TranscriptSegments.Segment segment : segments.getSegments()) {
                    writer.write("**" + segment.getSpeakerLabel() + ":** ");
                    writer.write(segment.getText() + "\n\n");
                }
            } else {
                writer.write(transcript + "\n");
            }
            
            // Footer
            writer.write("\n---\n");
            writer.write("*Generated by MeetingMate - Professional Meeting Transcription*\n");
        }
        
        return file;
    }
    
    /**
     * Share exported file via Intent
     */
    public void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context, 
            context.getPackageName() + ".fileprovider", file);
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        context.startActivity(Intent.createChooser(shareIntent, "Share transcript"));
    }
    
    /**
     * Get or create export directory
     */
    private File getExportDirectory() {
        File documentsDir = new File(context.getExternalFilesDir(null), "Documents");
        File exportDir = new File(documentsDir, EXPORT_DIR);
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        return exportDir;
    }
    
    /**
     * Sanitize filename
     */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\-_]", "_");
    }
    
    /**
     * Get timestamp for filename
     */
    private String getTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }
    
    /**
     * Format time for SRT (00:00:00,000)
     */
    private String formatSRTTime(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms);
    }
    
    /**
     * Format time for VTT (00:00:00.000)
     */
    private String formatVTTTime(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }
    
    /**
     * Data class for transcript segments
     */
    public static class TranscriptSegments {
        private java.util.List<Segment> segments = new java.util.ArrayList<>();
        
        public void addSegment(Segment segment) {
            segments.add(segment);
        }
        
        public java.util.List<Segment> getSegments() {
            return segments;
        }
        
        public boolean hasSegments() {
            return !segments.isEmpty();
        }
        
        public boolean hasTimestamps() {
            return hasSegments() && segments.get(0).hasTimestamp();
        }
        
        public static class Segment {
            private String text;
            private String speakerLabel;
            private long startTime;
            private long endTime;
            
            public Segment(String text) {
                this.text = text;
            }
            
            public Segment(String text, String speakerLabel, long startTime, long endTime) {
                this.text = text;
                this.speakerLabel = speakerLabel;
                this.startTime = startTime;
                this.endTime = endTime;
            }
            
            public String getText() { return text; }
            public String getSpeakerLabel() { return speakerLabel != null ? speakerLabel : "Speaker"; }
            public long getStartTime() { return startTime; }
            public long getEndTime() { return endTime; }
            public boolean hasSpeaker() { return speakerLabel != null; }
            public boolean hasTimestamp() { return endTime > 0; }
        }
    }
}