package ai.intelliswarm.meetingmate;

import android.Manifest;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import ai.intelliswarm.meetingmate.databinding.ActivityMainBinding;
import ai.intelliswarm.meetingmate.analytics.CrashAnalytics;
import ai.intelliswarm.meetingmate.analytics.AppLogger;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            AppLogger.lifecycle("MainActivity", "onCreate");

            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            // Set up the toolbar as the action bar
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            
            AppLogger.d(TAG, "Toolbar set as ActionBar");

            BottomNavigationView navView = findViewById(R.id.nav_view);
            AppLogger.d(TAG, "Found BottomNavigationView: " + (navView != null));
            
            // Passing each menu ID as a set of Ids because each
            // menu should be considered as top level destinations.
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                    .build();
                    
            AppLogger.d(TAG, "AppBarConfiguration created");
            
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            AppLogger.d(TAG, "NavController found: " + (navController != null));
            
            // Add navigation listener to debug navigation changes
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                AppLogger.d(TAG, "Navigation changed to: " + destination.getLabel());
            });
            
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            AppLogger.d(TAG, "ActionBar navigation setup completed");
            
            NavigationUI.setupWithNavController(binding.navView, navController);
            AppLogger.d(TAG, "BottomNavigation setup completed");
            
            // Add click listener to bottom navigation for debugging
            navView.setOnItemSelectedListener(item -> {
                AppLogger.d(TAG, "Bottom nav item selected: " + item.getTitle() + " (ID: " + item.getItemId() + ")");
                
                // Visual feedback - show toast when tab is pressed
                Toast.makeText(this, "Switching to: " + item.getTitle(), Toast.LENGTH_SHORT).show();
                
                // Try manual navigation as backup
                try {
                    boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                    AppLogger.d(TAG, "Navigation handled: " + handled);
                    
                    if (!handled) {
                        // Manual navigation fallback
                        AppLogger.w(TAG, "Navigation failed, trying manual navigation");
                        navController.navigate(item.getItemId());
                    }
                    
                    return true;
                } catch (Exception e) {
                    AppLogger.e(TAG, "Navigation error", e);
                    Toast.makeText(this, "Navigation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return false;
                }
            });
            
            AppLogger.d(TAG, "Navigation setup completed successfully");
            
            // Request essential permissions immediately
            requestEssentialPermissions();
            
            // Add diagnostic check after a delay
            new android.os.Handler().postDelayed(() -> {
                try {
                    AppLogger.d(TAG, "=== NAVIGATION DIAGNOSTIC ===");
                    AppLogger.d(TAG, "Current destination: " + navController.getCurrentDestination().getLabel());
                    AppLogger.d(TAG, "NavController graph: " + navController.getGraph().getDisplayName());
                    AppLogger.d(TAG, "Available destinations: " + navController.getGraph().getNodes().size());
                    
                    // Show diagnostic info to user
                    Toast.makeText(this, "Navigation setup complete. Current: " + 
                        navController.getCurrentDestination().getLabel(), Toast.LENGTH_LONG).show();
                        
                } catch (Exception e) {
                    AppLogger.e(TAG, "Navigation diagnostic failed", e);
                    Toast.makeText(this, "Navigation diagnostic error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 2000); // 2 second delay
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error in MainActivity onCreate", e);
            CrashAnalytics.logError(TAG, "Failed to initialize MainActivity", e);
        }
    }

    private void requestEssentialPermissions() {
        AppLogger.d(TAG, "Requesting essential permissions");
        
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.READ_CALENDAR);
        permissions.add(Manifest.permission.WRITE_CALENDAR);
        
        // For Android 11+ (API 30+), we don't need WRITE_EXTERNAL_STORAGE for app-specific storage
        // But for older versions, we still request it
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            AppLogger.d(TAG, "Added storage permissions for Android 10 and below");
        } else {
            AppLogger.d(TAG, "Skipping storage permissions for Android 11+ (using scoped storage)");
        }

        Dexter.withContext(this)
            .withPermissions(permissions)
            .withListener(new MultiplePermissionsListener() {
                @Override
                public void onPermissionsChecked(MultiplePermissionsReport report) {
                    if (report.areAllPermissionsGranted()) {
                        AppLogger.i(TAG, "All essential permissions granted");
                        Toast.makeText(MainActivity.this, "Permissions granted - app is ready!", Toast.LENGTH_SHORT).show();
                    } else {
                        AppLogger.w(TAG, "Some permissions denied");
                        
                        StringBuilder deniedPerms = new StringBuilder();
                        for (PermissionDeniedResponse deniedResponse : report.getDeniedPermissionResponses()) {
                            AppLogger.w(TAG, "Permission denied: " + deniedResponse.getPermissionName());
                            deniedPerms.append(deniedResponse.getPermissionName()).append(", ");
                        }
                        
                        Toast.makeText(MainActivity.this, 
                            "Some permissions denied. App may not work properly.", 
                            Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onPermissionRationaleShouldBeShown(
                    List<PermissionRequest> permissions, PermissionToken token) {
                    AppLogger.d(TAG, "Showing permission rationale");
                    token.continuePermissionRequest();
                }
            }).check();
    }

}