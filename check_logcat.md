# How to Check Logs in Android Studio Logcat

## Method 1: Logcat Window
1. Open Android Studio
2. Run the app on emulator/device
3. Open **View → Tool Windows → Logcat**
4. Filter by package: `ai.intelliswarm.meetingmate`
5. Look for these key tags:
   - `CrashAnalytics`
   - `AppLogger` 
   - `DashboardFragment`
   - `CalendarService`

## Method 2: ADB Command Line
```bash
# Filter logs for our app
adb logcat | grep "ai.intelliswarm.meetingmate"

# Or filter by specific tags
adb logcat -s CrashAnalytics,AppLogger,DashboardFragment,CalendarService

# Save logs to file
adb logcat > meetingmate_logs.txt
```

## What to Look For
- **Fatal crashes**: Look for "FATAL EXCEPTION" 
- **SecurityException**: Calendar permission issues
- **NullPointerException**: Missing null checks
- **ActivityNotFoundException**: Missing activity declarations
- **InflateException**: Layout inflation issues

## Key Log Tags to Monitor
- `MeetingMateApplication`: App startup issues
- `DashboardFragment`: Meeting tab problems  
- `CalendarService`: Calendar access errors
- `CrashAnalytics`: Crash reports
- `AppLogger`: General app flow