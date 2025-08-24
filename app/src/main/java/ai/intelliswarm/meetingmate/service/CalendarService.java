package ai.intelliswarm.meetingmate.service;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class CalendarService {
    private static final String TAG = "CalendarService";
    private final Context context;
    private final ContentResolver contentResolver;
    
    public CalendarService(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
    }
    
    // Check if we have calendar permissions
    private boolean hasCalendarPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
               == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    // Create test calendar events for demo/testing
    public boolean createTestEvents() {
        Log.d(TAG, "Creating test calendar events");
        
        // First, get the primary calendar
        long calendarId = getPrimaryCalendarId();
        if (calendarId == -1) {
            Log.e(TAG, "No calendar found to create test events");
            return false;
        }
        
        // Create several test events for today
        Calendar cal = Calendar.getInstance();
        boolean success = true;
        
        // Event 1: Morning meeting
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        Date start1 = cal.getTime();
        cal.add(Calendar.HOUR, 1);
        Date end1 = cal.getTime();
        long eventId1 = addMeetingToCalendar(calendarId, "Morning Standup", 
            "Daily team sync meeting", start1, end1, "Conference Room A");
        if (eventId1 == -1) success = false;
        
        // Event 2: Afternoon meeting
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 30);
        Date start2 = cal.getTime();
        cal.add(Calendar.HOUR, 1);
        Date end2 = cal.getTime();
        long eventId2 = addMeetingToCalendar(calendarId, "Project Review", 
            "Q4 project status review", start2, end2, "Zoom");
        if (eventId2 == -1) success = false;
        
        // Event 3: Late afternoon meeting
        cal.set(Calendar.HOUR_OF_DAY, 16);
        cal.set(Calendar.MINUTE, 0);
        Date start3 = cal.getTime();
        cal.add(Calendar.MINUTE, 30);
        Date end3 = cal.getTime();
        long eventId3 = addMeetingToCalendar(calendarId, "Client Call", 
            "Weekly sync with client", start3, end3, "Phone");
        if (eventId3 == -1) success = false;
        
        Log.d(TAG, "Test events created: " + success);
        return success;
    }
    
    // Get the primary calendar ID
    private long getPrimaryCalendarId() {
        String[] projection = new String[] {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        };
        
        try (Cursor cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null)) {
            
            if (cursor != null && cursor.moveToFirst()) {
                // Return first calendar found (usually the primary one)
                return cursor.getLong(0);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        }
        
        return -1;
    }
    
    // Get all available calendars
    public List<CalendarInfo> getAvailableCalendars() {
        List<CalendarInfo> calendars = new ArrayList<>();
        
        String[] projection = new String[] {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.ACCOUNT_TYPE
        };
        
        try (Cursor cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null)) {
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    CalendarInfo info = new CalendarInfo();
                    info.id = cursor.getLong(0);
                    info.accountName = cursor.getString(1);
                    info.displayName = cursor.getString(2);
                    info.ownerAccount = cursor.getString(3);
                    info.color = cursor.getInt(4);
                    info.accountType = cursor.getString(5);
                    calendars.add(info);
                    Log.d(TAG, "Found calendar: " + info.displayName + " (" + info.accountType + ")");
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        }
        
        return calendars;
    }
    
    // Get available calendar sources (Google, Local, etc.)
    public List<CalendarSource> getAvailableCalendarSources() {
        List<CalendarSource> sources = new ArrayList<>();
        List<CalendarInfo> calendars = getAvailableCalendars();
        
        // Group calendars by account type
        boolean hasGoogle = false;
        boolean hasLocal = false;
        boolean hasOther = false;
        
        for (CalendarInfo cal : calendars) {
            if (cal.accountType != null) {
                if (cal.accountType.contains("com.google")) {
                    hasGoogle = true;
                } else if (cal.accountType.contains("LOCAL") || cal.accountType.contains("local")) {
                    hasLocal = true;
                } else {
                    hasOther = true;
                }
            } else {
                hasLocal = true; // Assume local if no account type
            }
        }
        
        if (hasGoogle) {
            CalendarSource google = new CalendarSource();
            google.name = "Google Calendar";
            google.type = "google";
            google.icon = "📅";
            sources.add(google);
        }
        
        if (hasLocal) {
            CalendarSource local = new CalendarSource();
            local.name = "Local Calendar";
            local.type = "local";
            local.icon = "📱";
            sources.add(local);
        }
        
        if (hasOther) {
            CalendarSource other = new CalendarSource();
            other.name = "Other Calendars";
            other.type = "other";
            other.icon = "📋";
            sources.add(other);
        }
        
        // Always add test events option
        CalendarSource test = new CalendarSource();
        test.name = "📎 Load Test Events";
        test.type = "test";
        test.icon = "🧪";
        sources.add(test);
        
        Log.d(TAG, "Found " + sources.size() + " calendar sources");
        return sources;
    }
    
    // Get events for a specific calendar source
    public List<EventInfo> getEventsForSource(String sourceType) {
        Log.d(TAG, "Getting events for source: " + sourceType);
        
        if ("test".equals(sourceType)) {
            return createDemoEventsList();
        }
        
        List<CalendarInfo> calendars = getAvailableCalendars();
        List<CalendarInfo> sourceCalendars = new ArrayList<>();
        
        // Filter calendars by source type
        for (CalendarInfo cal : calendars) {
            boolean matches = false;
            
            if ("google".equals(sourceType) && cal.accountType != null && cal.accountType.contains("com.google")) {
                matches = true;
            } else if ("local".equals(sourceType) && (cal.accountType == null || 
                       cal.accountType.contains("LOCAL") || cal.accountType.contains("local"))) {
                matches = true;
            } else if ("other".equals(sourceType) && cal.accountType != null && 
                       !cal.accountType.contains("com.google") && 
                       !cal.accountType.contains("LOCAL") && !cal.accountType.contains("local")) {
                matches = true;
            }
            
            if (matches) {
                sourceCalendars.add(cal);
            }
        }
        
        // Get today's events from these calendars
        List<EventInfo> events = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startOfDay = calendar.getTimeInMillis();
        
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endOfDay = calendar.getTimeInMillis();
        
        for (CalendarInfo cal : sourceCalendars) {
            List<EventInfo> calendarEvents = getEventsForCalendar(cal.id, startOfDay, endOfDay);
            events.addAll(calendarEvents);
        }
        
        Log.d(TAG, "Found " + events.size() + " events for source " + sourceType);
        return events;
    }
    
    // Get events for a specific calendar
    public List<EventInfo> getEventsForCalendar(long calendarId, long startTime, long endTime) {
        List<EventInfo> events = new ArrayList<>();
        
        String[] projection = new String[] {
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID
        };
        
        String selection = "((" + CalendarContract.Events.DTSTART + " >= ?) AND (" 
                        + CalendarContract.Events.DTSTART + " <= ?) AND (" 
                        + CalendarContract.Events.CALENDAR_ID + " = ?))";
        String[] selectionArgs = new String[] {
            String.valueOf(startTime),
            String.valueOf(endTime),
            String.valueOf(calendarId)
        };
        
        try (Cursor cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            CalendarContract.Events.DTSTART + " ASC")) {
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    EventInfo info = new EventInfo();
                    info.id = cursor.getLong(0);
                    info.title = cursor.getString(1);
                    info.description = cursor.getString(2);
                    info.startTime = new Date(cursor.getLong(3));
                    info.endTime = new Date(cursor.getLong(4));
                    info.location = cursor.getString(5);
                    info.calendarId = cursor.getLong(6);
                    events.add(info);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        }
        
        return events;
    }
    
    // Create demo events list without adding to calendar
    private List<EventInfo> createDemoEventsList() {
        List<EventInfo> demoEvents = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        
        // Demo Event 1: Morning Standup
        EventInfo event1 = new EventInfo();
        event1.id = 1001;
        event1.title = "Morning Standup";
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        event1.startTime = cal.getTime();
        cal.add(Calendar.HOUR, 1);
        event1.endTime = cal.getTime();
        event1.description = "Daily team sync";
        event1.location = "Conference Room A";
        event1.hasMeetingNotes = true;
        event1.summary = "Team discussed progress on Q4 deliverables and current blockers";
        event1.keyPoints = "• Sprint 23 completed successfully\n• Performance improvements deployed\n• Testing phase scheduled for next week";
        event1.actionItems = "• John: Complete API documentation by Friday\n• Sarah: Schedule user testing session\n• Team: Review security checklist";
        demoEvents.add(event1);
        
        // Demo Event 2: Project Review
        EventInfo event2 = new EventInfo();
        event2.id = 1002;
        event2.title = "Project Review";
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 30);
        event2.startTime = cal.getTime();
        cal.add(Calendar.HOUR, 1);
        event2.endTime = cal.getTime();
        event2.description = "Q4 project status review";
        event2.location = "Zoom Meeting";
        event2.hasMeetingNotes = true;
        event2.summary = "Comprehensive review of Q4 project milestones and budget allocation";
        event2.keyPoints = "• 85% of deliverables completed on time\n• Budget utilization at 78%\n• Client satisfaction rating: 4.7/5";
        event2.actionItems = "• Finance: Prepare final Q4 budget report\n• PM: Schedule Q1 planning session\n• All: Submit time tracking data";
        demoEvents.add(event2);
        
        // Demo Event 3: Client Call
        EventInfo event3 = new EventInfo();
        event3.id = 1003;
        event3.title = "Client Call";
        cal.set(Calendar.HOUR_OF_DAY, 16);
        cal.set(Calendar.MINUTE, 0);
        event3.startTime = cal.getTime();
        cal.add(Calendar.MINUTE, 30);
        event3.endTime = cal.getTime();
        event3.description = "Weekly sync with client";
        event3.location = "Phone";
        demoEvents.add(event3);
        
        Log.d(TAG, "Created " + demoEvents.size() + " demo events");
        return demoEvents;
    }
    
    // Add meeting notes to calendar
    public long addMeetingToCalendar(long calendarId, String title, String summary, 
                                     Date startTime, Date endTime, String location) {
        ContentValues values = new ContentValues();
        
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DESCRIPTION, summary);
        values.put(CalendarContract.Events.EVENT_LOCATION, location);
        
        // Set times
        values.put(CalendarContract.Events.DTSTART, startTime.getTime());
        values.put(CalendarContract.Events.DTEND, endTime != null ? endTime.getTime() : startTime.getTime() + 3600000); // Default 1 hour
        
        // Set timezone
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        
        // Add extended properties for meeting notes
        values.put(CalendarContract.Events.HAS_EXTENDED_PROPERTIES, 1);
        
        try {
            Uri uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);
            if (uri != null) {
                return Long.parseLong(uri.getLastPathSegment());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        }
        
        return -1;
    }
    
    // Update existing calendar event with meeting notes
    public boolean updateCalendarEvent(long eventId, String summary) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DESCRIPTION, summary);
        
        Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        
        try {
            int rowsUpdated = contentResolver.update(updateUri, values, null, null);
            return rowsUpdated > 0;
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
            return false;
        }
    }
    
    // Get today's calendar events
    public List<EventInfo> getTodayEvents() {
        return getEventsForDate(new Date());
    }
    
    // Get calendar events for a specific date
    public List<EventInfo> getEventsForDate(Date date) {
        Log.d(TAG, "Getting calendar events for date: " + date);
        List<EventInfo> events = new ArrayList<>();
        
        try {
            // Check if we have calendar permissions
            if (!hasCalendarPermissions()) {
                Log.w(TAG, "Calendar permissions not granted, returning demo events");
                return createDemoEventsList();
            }
            
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startOfDay = calendar.getTimeInMillis();
            
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            long endOfDay = calendar.getTimeInMillis();
            
            Log.d(TAG, "Searching for events between " + new Date(startOfDay) + " and " + new Date(endOfDay));
            
            events = getEventsInRange(startOfDay, endOfDay);
            
            // If no real events found, add some demo events
            if (events.isEmpty()) {
                Log.d(TAG, "No real events found, adding demo events");
                events.addAll(createDemoEventsList());
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception accessing calendar", e);
            events = createDemoEventsList();
        } catch (Exception e) {
            Log.e(TAG, "Error getting calendar events", e);
            events = createDemoEventsList();
        }
        
        Log.d(TAG, "getEventsForDate returning " + events.size() + " events");
        return events;
    }
    
    // Helper method to check if two dates are on the same day
    private boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    // Get events in a specific time range
    public List<EventInfo> getEventsInRange(long startTime, long endTime) {
        List<EventInfo> events = new ArrayList<>();
        Log.d(TAG, "getEventsInRange called for range: " + startTime + " to " + endTime);
        
        // Check permissions first
        if (!hasCalendarPermissions()) {
            Log.w(TAG, "Calendar permissions not granted in getEventsInRange");
            return events; // Return empty list
        }
        
        String[] projection = new String[] {
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID
        };
        
        String selection = "((" + CalendarContract.Events.DTSTART + " >= ?) AND (" 
                        + CalendarContract.Events.DTSTART + " <= ?))";
        String[] selectionArgs = new String[] {
            String.valueOf(startTime),
            String.valueOf(endTime)
        };
        
        Log.d(TAG, "Querying calendar with selection: " + selection);
        Log.d(TAG, "Selection args: " + selectionArgs[0] + ", " + selectionArgs[1]);
        
        try (Cursor cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            CalendarContract.Events.DTSTART + " ASC")) {
            
            Log.d(TAG, "Calendar query executed, cursor: " + (cursor != null ? "not null" : "null"));
            
            if (cursor != null) {
                Log.d(TAG, "Cursor has " + cursor.getCount() + " rows");
                while (cursor.moveToNext()) {
                    EventInfo info = new EventInfo();
                    info.id = cursor.getLong(0);
                    info.title = cursor.getString(1);
                    info.description = cursor.getString(2);
                    info.startTime = new Date(cursor.getLong(3));
                    info.endTime = new Date(cursor.getLong(4));
                    info.location = cursor.getString(5);
                    info.calendarId = cursor.getLong(6);
                    events.add(info);
                    Log.d(TAG, "Found event: " + info.title + " at " + info.startTime);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        } catch (Exception e) {
            Log.e(TAG, "Error querying calendar events", e);
        }
        
        Log.d(TAG, "Returning " + events.size() + " events from getEventsInRange");
        return events;
    }
    
    // Get a specific event by ID
    public EventInfo getEvent(long eventId) {
        String[] projection = new String[] {
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID
        };
        
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        
        try (Cursor cursor = contentResolver.query(
            uri,
            projection,
            null,
            null,
            null)) {
            
            if (cursor != null && cursor.moveToFirst()) {
                EventInfo info = new EventInfo();
                info.id = cursor.getLong(0);
                info.title = cursor.getString(1);
                info.description = cursor.getString(2);
                info.startTime = new Date(cursor.getLong(3));
                info.endTime = new Date(cursor.getLong(4));
                info.location = cursor.getString(5);
                info.calendarId = cursor.getLong(6);
                return info;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        }
        
        return null;
    }
    
    // Add meeting notes as extended property
    public boolean addMeetingNotes(long eventId, String meetingId, String transcriptPath, String summaryPath) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.ExtendedProperties.EVENT_ID, eventId);
        values.put(CalendarContract.ExtendedProperties.NAME, "meeting_id");
        values.put(CalendarContract.ExtendedProperties.VALUE, meetingId);
        
        try {
            contentResolver.insert(CalendarContract.ExtendedProperties.CONTENT_URI, values);
            
            // Add transcript path
            values.clear();
            values.put(CalendarContract.ExtendedProperties.EVENT_ID, eventId);
            values.put(CalendarContract.ExtendedProperties.NAME, "transcript_path");
            values.put(CalendarContract.ExtendedProperties.VALUE, transcriptPath);
            contentResolver.insert(CalendarContract.ExtendedProperties.CONTENT_URI, values);
            
            // Add summary path
            values.clear();
            values.put(CalendarContract.ExtendedProperties.EVENT_ID, eventId);
            values.put(CalendarContract.ExtendedProperties.NAME, "summary_path");
            values.put(CalendarContract.ExtendedProperties.VALUE, summaryPath);
            contentResolver.insert(CalendarContract.ExtendedProperties.CONTENT_URI, values);
            
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
            return false;
        }
    }
    
    // Helper classes
    public static class CalendarInfo {
        public long id;
        public String accountName;
        public String displayName;
        public String ownerAccount;
        public int color;
        public String accountType;
        
        @Override
        public String toString() {
            return displayName != null ? displayName : accountName;
        }
    }
    
    public static class CalendarSource {
        public String name;
        public String type;
        public String icon;
        
        @Override
        public String toString() {
            return (icon != null ? icon + " " : "") + name;
        }
    }
    
    public static class EventInfo {
        public long id;
        public String title;
        public String description;
        public Date startTime;
        public Date endTime;
        public String location;
        public long calendarId;
        
        // Meeting summary fields
        public String summary;
        public String keyPoints;
        public String actionItems;
        public String transcriptPath;
        public boolean hasMeetingNotes;
        
        @Override
        public String toString() {
            if (title != null && !title.isEmpty()) {
                if (startTime != null) {
                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                    return timeFormat.format(startTime) + " - " + title;
                }
                return title;
            }
            return "Unnamed Event";
        }
    }
}