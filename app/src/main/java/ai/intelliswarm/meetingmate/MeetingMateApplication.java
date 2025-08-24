package ai.intelliswarm.meetingmate;

import android.app.Application;
import android.util.Log;
import android.content.Context;
import ai.intelliswarm.meetingmate.analytics.AppLogger;
import ai.intelliswarm.meetingmate.analytics.CrashAnalytics;
import ai.intelliswarm.meetingmate.utils.SettingsManager;

public class MeetingMateApplication extends Application {
    
    private static final String TAG = "MeetingMateApplication";
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(SettingsManager.applyLanguage(base));
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize crash analytics and logging as early as possible
        try {
            CrashAnalytics.initialize(this);
            AppLogger.i(TAG, "MeetingMate Application started");
            AppLogger.lifecycle("Application", "onCreate");
            
        } catch (Exception e) {
            // Fallback to standard logging if crash analytics fails
            Log.e(TAG, "Failed to initialize crash analytics", e);
        }
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        AppLogger.w(TAG, "Application received onLowMemory callback");
        AppLogger.lifecycle("Application", "onLowMemory");
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        AppLogger.w(TAG, "Application memory trimmed, level: " + level);
        AppLogger.lifecycle("Application", "onTrimMemory(level=" + level + ")");
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        AppLogger.lifecycle("Application", "onTerminate");
        AppLogger.close();
    }
}