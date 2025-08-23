package ai.intelliswarm.meetingmate;

import android.os.Bundle;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import ai.intelliswarm.meetingmate.databinding.ActivityMainBinding;
import ai.intelliswarm.meetingmate.analytics.CrashAnalytics;
import ai.intelliswarm.meetingmate.analytics.AppLogger;

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
                return NavigationUI.onNavDestinationSelected(item, navController);
            });
            
            AppLogger.d(TAG, "Navigation setup completed successfully");
            
        } catch (Exception e) {
            AppLogger.e(TAG, "Error in MainActivity onCreate", e);
            CrashAnalytics.logError(TAG, "Failed to initialize MainActivity", e);
        }
    }

}