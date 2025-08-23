package ai.intelliswarm.meetingmate.analytics;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLogger {
    
    private static final String TAG = "AppLogger";
    private static final String LOG_FILE_NAME = "app_debug.log";
    private static final int MAX_LOG_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    
    private static AppLogger instance;
    private static boolean initialized = false;
    
    private final Context context;
    private final File logFile;
    private final SimpleDateFormat timestampFormat;
    private final ExecutorService executor;
    private FileWriter logWriter;
    
    private AppLogger(Context context) {
        this.context = context.getApplicationContext();
        this.timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        this.executor = Executors.newSingleThreadExecutor();
        
        File logDir = new File(context.getFilesDir(), "analytics");
        logDir.mkdirs();
        this.logFile = new File(logDir, LOG_FILE_NAME);
        
        initializeLogFile();
    }
    
    public static synchronized void initialize(Context context) {
        if (!initialized) {
            instance = new AppLogger(context);
            initialized = true;
            Log.i(TAG, "AppLogger initialized, log file: " + instance.logFile.getAbsolutePath());
        }
    }
    
    private void initializeLogFile() {
        try {
            // Check if log file is too large and rotate it
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                rotateLogFile();
            }
            
            logWriter = new FileWriter(logFile, true);
            
            // Write session start marker
            writeToFile("=== NEW SESSION ===", "SESSION", "Session started at " + new Date());
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize log file", e);
        }
    }
    
    private void rotateLogFile() {
        try {
            // Backup current log file
            File backupFile = new File(logFile.getParent(), "app_debug_backup.log");
            if (backupFile.exists()) {
                backupFile.delete();
            }
            logFile.renameTo(backupFile);
            
            Log.i(TAG, "Log file rotated, backup created");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to rotate log file", e);
        }
    }
    
    // Public logging methods
    public static void v(String tag, String message) {
        Log.v(tag, message);
        if (initialized && instance != null) {
            instance.writeToFileAsync("VERBOSE", tag, message);
        }
    }
    
    public static void d(String tag, String message) {
        Log.d(tag, message);
        if (initialized && instance != null) {
            instance.writeToFileAsync("DEBUG", tag, message);
        }
    }
    
    public static void i(String tag, String message) {
        Log.i(tag, message);
        if (initialized && instance != null) {
            instance.writeToFileAsync("INFO", tag, message);
        }
    }
    
    public static void w(String tag, String message) {
        Log.w(tag, message);
        if (initialized && instance != null) {
            instance.writeToFileAsync("WARN", tag, message);
        }
    }
    
    public static void w(String tag, String message, Throwable throwable) {
        Log.w(tag, message, throwable);
        if (initialized && instance != null) {
            instance.writeToFileAsync("WARN", tag, message + "\\n" + Log.getStackTraceString(throwable));
        }
    }
    
    public static void e(String tag, String message) {
        Log.e(tag, message);
        if (initialized && instance != null) {
            instance.writeToFileAsync("ERROR", tag, message);
        }
    }
    
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
        if (initialized && instance != null) {
            instance.writeToFileAsync("ERROR", tag, message + "\\n" + Log.getStackTraceString(throwable));
        }
    }
    
    // Special logging methods for analytics
    public static void lifecycle(String component, String event) {
        String message = component + " -> " + event;
        i("LIFECYCLE", message);
    }
    
    public static void userAction(String screen, String action, String details) {
        String message = "Screen: " + screen + ", Action: " + action;
        if (details != null && !details.isEmpty()) {
            message += ", Details: " + details;
        }
        i("USER_ACTION", message);
    }
    
    public static void performance(String operation, long startTime, long endTime) {
        long duration = endTime - startTime;
        i("PERFORMANCE", operation + " completed in " + duration + "ms");
    }
    
    public static void networkRequest(String url, String method, int responseCode, long duration) {
        String message = method + " " + url + " -> " + responseCode + " (" + duration + "ms)";
        i("NETWORK", message);
    }
    
    private void writeToFileAsync(String level, String tag, String message) {
        executor.execute(() -> writeToFile(level, tag, message));
    }
    
    private synchronized void writeToFile(String level, String tag, String message) {
        if (logWriter != null) {
            try {
                String timestamp = timestampFormat.format(new Date());
                String logLine = String.format("%s [%s] %s: %s\\n", timestamp, level, tag, message);
                
                logWriter.write(logLine);
                logWriter.flush();
                
            } catch (IOException e) {
                Log.e(TAG, "Failed to write to log file", e);
            }
        }
    }
    
    public static File getLogFile() {
        if (initialized && instance != null) {
            return instance.logFile;
        }
        return null;
    }
    
    public static File getLogDirectory() {
        if (initialized && instance != null) {
            return instance.logFile.getParentFile();
        }
        return null;
    }
    
    public static void flush() {
        if (initialized && instance != null) {
            instance.executor.execute(() -> {
                try {
                    if (instance.logWriter != null) {
                        instance.logWriter.flush();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to flush log writer", e);
                }
            });
        }
    }
    
    public static void close() {
        if (initialized && instance != null) {
            instance.executor.execute(() -> {
                try {
                    if (instance.logWriter != null) {
                        instance.writeToFile("SESSION", "SESSION", "Session ended at " + new Date());
                        instance.logWriter.close();
                        instance.logWriter = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close log writer", e);
                }
                instance.executor.shutdown();
            });
            initialized = false;
        }
    }
}