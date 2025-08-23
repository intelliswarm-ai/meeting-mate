package ai.intelliswarm.meetingmate.service;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
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
    
    // Get all available calendars
    public List<CalendarInfo> getAvailableCalendars() {
        List<CalendarInfo> calendars = new ArrayList<>();
        
        String[] projection = new String[] {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR
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
                    calendars.add(info);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted", e);
        }
        
        return calendars;
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
        Log.d(TAG, "Getting today's calendar events");
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startOfDay = calendar.getTimeInMillis();
        
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endOfDay = calendar.getTimeInMillis();
        
        Log.d(TAG, "Searching for events between " + new Date(startOfDay) + " and " + new Date(endOfDay));
        
        List<EventInfo> events = getEventsInRange(startOfDay, endOfDay);
        Log.d(TAG, "getTodayEvents returning " + events.size() + " events");
        
        return events;
    }
    
    // Get events in a specific time range
    public List<EventInfo> getEventsInRange(long startTime, long endTime) {
        List<EventInfo> events = new ArrayList<>();
        Log.d(TAG, "getEventsInRange called for range: " + startTime + " to " + endTime);
        
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
        
        @Override
        public String toString() {
            return displayName != null ? displayName : accountName;
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