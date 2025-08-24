package ai.intelliswarm.meetingmate.analytics;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import ai.intelliswarm.meetingmate.R;
import ai.intelliswarm.meetingmate.utils.SettingsManager;
import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Debug activity to validate the complete recording-to-transcription flow
 * This can be launched for testing purposes
 */
public class ValidationActivity extends Activity {
    
    private TextView logTextView;
    private ScrollView scrollView;
    private Button runValidationButton;
    private ExecutorService executor;
    private Handler mainHandler;
    
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(SettingsManager.applyLanguage(newBase));
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create simple layout programmatically
        ScrollView scrollView = new ScrollView(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        // Title
        TextView titleView = new TextView(this);
        titleView.setText("Flow Validation Tool");
        titleView.setTextSize(20);
        titleView.setPadding(0, 0, 0, 20);
        layout.addView(titleView);
        
        // Run button
        runValidationButton = new Button(this);
        runValidationButton.setText("Run Complete Validation");
        runValidationButton.setOnClickListener(v -> runValidation());
        layout.addView(runValidationButton);
        
        // Log output
        logTextView = new TextView(this);
        logTextView.setText("Tap 'Run Complete Validation' to start testing...\\n");
        logTextView.setTextSize(12);
        logTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logTextView.setPadding(10, 20, 10, 10);
        logTextView.setBackgroundColor(android.graphics.Color.parseColor("#f5f5f5"));
        layout.addView(logTextView);
        
        scrollView.addView(layout);
        setContentView(scrollView);
        
        this.scrollView = scrollView;
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize logging
        AppLogger.initialize(this);
    }
    
    private void runValidation() {
        runValidationButton.setEnabled(false);
        logTextView.setText("Starting validation...\\n");
        
        executor.execute(() -> {
            FlowValidator validator = new FlowValidator(this);
            
            try {
                updateLog("=== MEETING MATE FLOW VALIDATION ===\\n");
                updateLog("Time: " + new java.util.Date().toString() + "\\n\\n");
                
                FlowValidator.ValidationResult[] results = validator.validateCompleteFlow();
                
                String[] stepNames = {
                    "1. System Configuration",
                    "2. File System Access",
                    "3. Test Audio Creation",
                    "4. Whisper API Integration"
                };
                
                int passed = 0;
                for (int i = 0; i < results.length; i++) {
                    FlowValidator.ValidationResult result = results[i];
                    String status = result.success ? "✅ PASS" : "❌ FAIL";
                    
                    updateLog(String.format("%s: %s\\n", stepNames[i], status));
                    updateLog("   " + result.message + "\\n");
                    
                    if (result.details != null) {
                        updateLog("   Details: " + result.details + "\\n");
                    }
                    updateLog("\\n");
                    
                    if (result.success) {
                        passed++;
                    }
                    
                    // Small delay for readability
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                updateLog(String.format("📊 SUMMARY: %d/%d tests passed (%.1f%%)\\n\\n", 
                    passed, results.length, (passed * 100.0 / results.length)));
                
                if (passed == results.length) {
                    updateLog("🎉 ALL TESTS PASSED - Recording-to-transcription flow is ready!\\n");
                    mainHandler.post(() -> {
                        Toast.makeText(this, "All validations passed!", Toast.LENGTH_LONG).show();
                    });
                } else if (passed >= 2) {
                    updateLog("⚠️  PARTIAL SUCCESS - Basic functionality working\\n");
                    updateLog("   Check failed tests above for issues\\n");
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Basic functionality working, see log for details", Toast.LENGTH_LONG).show();
                    });
                } else {
                    updateLog("❌ VALIDATION FAILED - Major issues detected\\n");
                    updateLog("   Please check configuration and permissions\\n");
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Major issues detected - check log", Toast.LENGTH_LONG).show();
                    });
                }
                
                updateLog("\\n=== VALIDATION COMPLETE ===\\n");
                updateLog("\\nTo test with real audio recording:\\n");
                updateLog("1. Ensure OpenAI API key is configured\\n");
                updateLog("2. Record a short audio clip\\n");
                updateLog("3. Check app logs for TranscriptionFlow entries\\n");
                updateLog("4. Verify transcript files are created in storage\\n");
                
            } catch (Exception e) {
                updateLog("❌ VALIDATION ERROR: " + e.getMessage() + "\\n");
                AppLogger.e("ValidationActivity", "Validation failed with exception", e);
                
                mainHandler.post(() -> {
                    Toast.makeText(this, "Validation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                mainHandler.post(() -> {
                    runValidationButton.setEnabled(true);
                });
            }
        });
    }
    
    private void updateLog(String message) {
        mainHandler.post(() -> {
            logTextView.append(message);
            // Auto-scroll to bottom
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}