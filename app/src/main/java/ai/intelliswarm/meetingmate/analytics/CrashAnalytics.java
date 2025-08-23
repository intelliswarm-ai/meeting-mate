package ai.intelliswarm.meetingmate.analytics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashAnalytics implements Thread.UncaughtExceptionHandler {
    
    private static final String TAG = "CrashAnalytics";
    private static final String CRASH_LOG_DIR = "crash_logs";
    private static final String SESSION_LOG_DIR = "session_logs";
    
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final SimpleDateFormat timestampFormat;
    private final File crashLogDir;
    private final File sessionLogDir;
    private FileWriter sessionLogWriter;
    
    public CrashAnalytics(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.timestampFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        
        // Create log directories
        File appDir = new File(context.getFilesDir(), "analytics");
        appDir.mkdirs();
        
        this.crashLogDir = new File(appDir, CRASH_LOG_DIR);
        this.sessionLogDir = new File(appDir, SESSION_LOG_DIR);
        
        crashLogDir.mkdirs();
        sessionLogDir.mkdirs();
        
        initializeSessionLogging();
    }
    
    public static void initialize(Context context) {
        CrashAnalytics crashAnalytics = new CrashAnalytics(context);
        Thread.setDefaultUncaughtExceptionHandler(crashAnalytics);
        
        // Initialize custom logger
        AppLogger.initialize(context);
        
        AppLogger.i(TAG, "CrashAnalytics initialized");
        AppLogger.i(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        AppLogger.i(TAG, "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            AppLogger.i(TAG, "App: " + pInfo.versionName + " (" + pInfo.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            AppLogger.e(TAG, "Could not get app version info", e);
        }
    }
    
    private void initializeSessionLogging() {
        try {
            String sessionFileName = "session_" + timestampFormat.format(new Date()) + ".log";
            File sessionFile = new File(sessionLogDir, sessionFileName);
            sessionLogWriter = new FileWriter(sessionFile, true);
            
            // Write session header
            sessionLogWriter.write("=== NEW SESSION STARTED ===\n");
            sessionLogWriter.write("Timestamp: " + new Date() + "\n");
            sessionLogWriter.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n");
            sessionLogWriter.write("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n");
            sessionLogWriter.write("================================\n\n");
            sessionLogWriter.flush();
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize session logging", e);
        }
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        try {
            // Log the crash
            AppLogger.e(TAG, "FATAL CRASH detected in thread: " + thread.getName(), exception);
            
            // Create detailed crash report
            String crashReport = generateCrashReport(thread, exception);
            
            // Save crash report to file
            saveCrashReport(crashReport);
            
            // Log system state
            logSystemState();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in crash handler", e);
        } finally {
            // Close session logging
            closeSessionLogging();
            
            // Call default handler to actually crash the app
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, exception);
            }
        }
    }
    
    private String generateCrashReport(Thread thread, Throwable exception) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== CRASH REPORT ===\n");
        report.append("Timestamp: ").append(new Date()).append("\n");
        report.append("Thread: ").append(thread.getName()).append("\n");
        report.append("Exception: ").append(exception.getClass().getSimpleName()).append("\n");
        report.append("Message: ").append(exception.getMessage()).append("\n");
        report.append("\n=== DEVICE INFO ===\n");
        report.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        report.append("Model: ").append(Build.MODEL).append("\n");
        report.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
        report.append("API Level: ").append(Build.VERSION.SDK_INT).append("\n");
        report.append("Build: ").append(Build.DISPLAY).append("\n");
        
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            report.append("\n=== APP INFO ===\n");
            report.append("Version Name: ").append(pInfo.versionName).append("\n");
            report.append("Version Code: ").append(pInfo.versionCode).append("\n");
            report.append("Package: ").append(pInfo.packageName).append("\n");
        } catch (PackageManager.NameNotFoundException e) {
            report.append("App info: Not available\n");
        }
        
        report.append("\n=== STACK TRACE ===\n");
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        exception.printStackTrace(printWriter);
        report.append(stringWriter.toString());
        
        // Add caused by chain
        Throwable cause = exception.getCause();
        while (cause != null) {
            report.append("\nCaused by: ");
            StringWriter causeWriter = new StringWriter();
            PrintWriter causePrintWriter = new PrintWriter(causeWriter);
            cause.printStackTrace(causePrintWriter);
            report.append(causeWriter.toString());
            cause = cause.getCause();
        }
        
        report.append("\n=== END CRASH REPORT ===\n");
        
        return report.toString();
    }
    
    private void saveCrashReport(String crashReport) {
        try {
            String fileName = "crash_" + timestampFormat.format(new Date()) + ".log";
            File crashFile = new File(crashLogDir, fileName);
            
            FileWriter writer = new FileWriter(crashFile);
            writer.write(crashReport);
            writer.close();
            
            Log.i(TAG, "Crash report saved to: " + crashFile.getAbsolutePath());
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to save crash report", e);
        }
    }
    
    private void logSystemState() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            AppLogger.i(TAG, "=== SYSTEM STATE AT CRASH ===");
            AppLogger.i(TAG, "Total Memory: " + (totalMemory / 1024 / 1024) + " MB");
            AppLogger.i(TAG, "Used Memory: " + (usedMemory / 1024 / 1024) + " MB");
            AppLogger.i(TAG, "Free Memory: " + (freeMemory / 1024 / 1024) + " MB");
            AppLogger.i(TAG, "Available Processors: " + runtime.availableProcessors());
            
        } catch (Exception e) {
            Log.e(TAG, "Error logging system state", e);
        }
    }
    
    private void closeSessionLogging() {
        if (sessionLogWriter != null) {
            try {
                sessionLogWriter.write("\n=== SESSION ENDED (CRASH) ===\n");
                sessionLogWriter.write("Timestamp: " + new Date() + "\n");
                sessionLogWriter.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing session log", e);
            }
        }
    }
    
    // Static methods for manual crash reporting
    public static void logEvent(String event, String details) {
        AppLogger.i("EVENT", event + ": " + details);
    }
    
    public static void logUserAction(String action, String screen) {
        AppLogger.i("USER_ACTION", "Screen: " + screen + ", Action: " + action);
    }
    
    public static void logPerformance(String operation, long durationMs) {
        AppLogger.i("PERFORMANCE", operation + " took " + durationMs + "ms");
    }
    
    public static void logError(String tag, String message, Throwable throwable) {
        AppLogger.e(tag, message, throwable);
    }
}