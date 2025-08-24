package ai.intelliswarm.meetingmate.analytics;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import ai.intelliswarm.meetingmate.R;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LogViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "LogViewerActivity";
    
    private MaterialTextView statusText;
    private MaterialButton refreshButton;
    private MaterialButton exportButton;
    private MaterialButton clearButton;
    private RecyclerView logFilesRecyclerView;
    private LogFilesAdapter logFilesAdapter;
    
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(SettingsManager.applyLanguage(newBase));
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);
        
        AppLogger.lifecycle("LogViewerActivity", "onCreate");
        
        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupButtons();
        refreshLogFiles();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.text_status);
        refreshButton = findViewById(R.id.button_refresh);
        exportButton = findViewById(R.id.button_export_logs);
        clearButton = findViewById(R.id.button_clear_logs);
        logFilesRecyclerView = findViewById(R.id.recycler_log_files);
    }
    
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Debug Logs & Analytics");
        }
    }
    
    private void setupRecyclerView() {
        logFilesAdapter = new LogFilesAdapter(new ArrayList<>(), this::onLogFileSelected);
        logFilesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logFilesRecyclerView.setAdapter(logFilesAdapter);
    }
    
    private void setupButtons() {
        refreshButton.setOnClickListener(v -> {
            AppLogger.userAction("LogViewerActivity", "refresh_clicked", null);
            refreshLogFiles();
        });
        
        exportButton.setOnClickListener(v -> {
            AppLogger.userAction("LogViewerActivity", "export_clicked", null);
            exportAllLogs();
        });
        
        clearButton.setOnClickListener(v -> {
            AppLogger.userAction("LogViewerActivity", "clear_clicked", null);
            clearAllLogs();
        });
    }
    
    private void refreshLogFiles() {
        AppLogger.d(TAG, "Refreshing log files");
        
        new Thread(() -> {
            try {
                List<LogFileInfo> logFiles = new ArrayList<>();
                
                // Get analytics directory
                File analyticsDir = AppLogger.getLogDirectory();
                if (analyticsDir != null && analyticsDir.exists()) {
                    
                    // Add session logs
                    File sessionLogsDir = new File(analyticsDir, "session_logs");
                    if (sessionLogsDir.exists()) {
                        File[] sessionFiles = sessionLogsDir.listFiles((dir, name) -> name.endsWith(".log"));
                        if (sessionFiles != null) {
                            for (File file : sessionFiles) {
                                LogFileInfo info = new LogFileInfo();
                                info.file = file;
                                info.name = file.getName();
                                info.type = "Session Log";
                                info.size = file.length();
                                info.lastModified = file.lastModified();
                                logFiles.add(info);
                            }
                        }
                    }
                    
                    // Add crash logs
                    File crashLogsDir = new File(analyticsDir, "crash_logs");
                    if (crashLogsDir.exists()) {
                        File[] crashFiles = crashLogsDir.listFiles((dir, name) -> name.endsWith(".log"));
                        if (crashFiles != null) {
                            for (File file : crashFiles) {
                                LogFileInfo info = new LogFileInfo();
                                info.file = file;
                                info.name = file.getName();
                                info.type = "Crash Report";
                                info.size = file.length();
                                info.lastModified = file.lastModified();
                                logFiles.add(info);
                            }
                        }
                    }
                    
                    // Add main app log
                    File appLog = new File(analyticsDir, "app_debug.log");
                    if (appLog.exists()) {
                        LogFileInfo info = new LogFileInfo();
                        info.file = appLog;
                        info.name = "app_debug.log";
                        info.type = "App Debug Log";
                        info.size = appLog.length();
                        info.lastModified = appLog.lastModified();
                        logFiles.add(info);
                    }
                    
                    // Add backup log if exists
                    File backupLog = new File(analyticsDir, "app_debug_backup.log");
                    if (backupLog.exists()) {
                        LogFileInfo info = new LogFileInfo();
                        info.file = backupLog;
                        info.name = "app_debug_backup.log";
                        info.type = "App Debug Backup";
                        info.size = backupLog.length();
                        info.lastModified = backupLog.lastModified();
                        logFiles.add(info);
                    }
                }
                
                // Sort by last modified (newest first)
                Collections.sort(logFiles, (a, b) -> Long.compare(b.lastModified, a.lastModified));
                
                runOnUiThread(() -> {
                    logFilesAdapter.updateLogFiles(logFiles);
                    statusText.setText("Found " + logFiles.size() + " log file(s)");
                    
                    long totalSize = logFiles.stream().mapToLong(info -> info.size).sum();
                    String sizeText = formatFileSize(totalSize);
                    statusText.append(" • Total size: " + sizeText);
                });
                
            } catch (Exception e) {
                AppLogger.e(TAG, "Error refreshing log files", e);
                runOnUiThread(() -> {
                    statusText.setText("Error loading log files");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void onLogFileSelected(LogFileInfo logFileInfo) {
        AppLogger.userAction("LogViewerActivity", "log_file_selected", logFileInfo.name);
        
        // Create intent to view/share the log file
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        try {
            Uri fileUri = FileProvider.getUriForFile(this, 
                getPackageName() + ".fileprovider", logFileInfo.file);
            intent.setDataAndType(fileUri, "text/plain");
            
            Intent chooser = Intent.createChooser(intent, "View log file with...");
            startActivity(chooser);
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error opening log file", e);
            Toast.makeText(this, "Error opening file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void exportAllLogs() {
        new Thread(() -> {
            try {
                File analyticsDir = AppLogger.getLogDirectory();
                if (analyticsDir == null || !analyticsDir.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "No logs directory found", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                // Create share intent for the entire analytics directory
                Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.setType("text/plain");
                
                ArrayList<Uri> logUris = new ArrayList<>();
                
                // Collect all log files
                File[] allFiles = analyticsDir.listFiles();
                if (allFiles != null) {
                    for (File file : allFiles) {
                        if (file.isFile() && file.getName().endsWith(".log")) {
                            Uri fileUri = FileProvider.getUriForFile(this, 
                                getPackageName() + ".fileprovider", file);
                            logUris.add(fileUri);
                        } else if (file.isDirectory()) {
                            File[] subFiles = file.listFiles((dir, name) -> name.endsWith(".log"));
                            if (subFiles != null) {
                                for (File subFile : subFiles) {
                                    Uri fileUri = FileProvider.getUriForFile(this, 
                                        getPackageName() + ".fileprovider", subFile);
                                    logUris.add(fileUri);
                                }
                            }
                        }
                    }
                }
                
                if (logUris.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "No log files to export", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, logUris);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "MeetingMate Debug Logs");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Debug logs and crash reports from MeetingMate app");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                runOnUiThread(() -> {
                    Intent chooser = Intent.createChooser(shareIntent, "Export logs to...");
                    startActivity(chooser);
                });
                
            } catch (Exception e) {
                AppLogger.e(TAG, "Error exporting logs", e);
                runOnUiThread(() -> Toast.makeText(this, "Error exporting logs: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    private void clearAllLogs() {
        new Thread(() -> {
            try {
                File analyticsDir = AppLogger.getLogDirectory();
                if (analyticsDir != null && analyticsDir.exists()) {
                    deleteRecursively(analyticsDir);
                    analyticsDir.mkdirs(); // Recreate the directory
                    
                    // Reinitialize logging
                    AppLogger.initialize(this);
                }
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "All logs cleared", Toast.LENGTH_SHORT).show();
                    refreshLogFiles();
                });
                
            } catch (Exception e) {
                AppLogger.e(TAG, "Error clearing logs", e);
                runOnUiThread(() -> Toast.makeText(this, "Error clearing logs: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLogger.lifecycle("LogViewerActivity", "onDestroy");
    }
    
    public static class LogFileInfo {
        public File file;
        public String name;
        public String type;
        public long size;
        public long lastModified;
    }
}