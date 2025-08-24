package ai.intelliswarm.meetingmate.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Environment;
import androidx.core.app.NotificationCompat;
import ai.intelliswarm.meetingmate.MainActivity;
import ai.intelliswarm.meetingmate.R;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudioRecordingService extends Service {
    private static final String CHANNEL_ID = "MeetingMateRecording";
    private static final int NOTIFICATION_ID = 1;
    
    private MediaRecorder mediaRecorder;
    private String currentFilePath;
    private boolean isRecording = false;
    private long recordingStartTime;
    
    // Store last recording info for backup access
    private String lastRecordingPath;
    private long lastRecordingDuration;
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public AudioRecordingService getService() {
            return AudioRecordingService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "START_RECORDING":
                    startRecording();
                    break;
                case "STOP_RECORDING":
                    stopRecording();
                    break;
            }
        }
        return START_STICKY;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Meeting Recording",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when a meeting is being recorded");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        Intent stopIntent = new Intent(this, AudioRecordingService.class);
        stopIntent.setAction("STOP_RECORDING");
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meeting Recording")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopPendingIntent)
            .setOngoing(true);
        
        return builder.build();
    }
    
    public void startRecording() {
        if (isRecording) {
            return;
        }
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification("Recording meeting..."));
        
        // Setup file path
        File audioDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS), "MeetingMate/Audio");
        
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        currentFilePath = new File(audioDir, "recording_" + timestamp + ".m4a").getAbsolutePath();
        
        // Setup MediaRecorder
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setOutputFile(currentFilePath);
        
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            
            // Broadcast recording started
            Intent intent = new Intent("RECORDING_STATE_CHANGED");
            intent.putExtra("isRecording", true);
            sendBroadcast(intent);
            
        } catch (IOException e) {
            e.printStackTrace();
            stopForeground(true);
        }
    }
    
    public void stopRecording() {
        if (!isRecording || mediaRecorder == null) {
            return;
        }
        
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            
            long recordingDuration = System.currentTimeMillis() - recordingStartTime;
            
            // Store last recording info for backup access
            lastRecordingPath = currentFilePath;
            lastRecordingDuration = recordingDuration;
            
            // Broadcast recording stopped
            Intent intent = new Intent("RECORDING_STATE_CHANGED");
            intent.putExtra("isRecording", false);
            intent.putExtra("filePath", currentFilePath);
            intent.putExtra("duration", recordingDuration);
            sendBroadcast(intent);
            
        } catch (RuntimeException e) {
            e.printStackTrace();
            // Delete corrupted file
            if (currentFilePath != null) {
                File file = new File(currentFilePath);
                if (file.exists()) {
                    file.delete();
                }
            }
        } finally {
            stopForeground(true);
            stopSelf();
        }
    }
    
    public void pauseRecording() {
        if (isRecording && mediaRecorder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.pause();
            
            // Update notification
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification("Recording paused"));
            }
        }
    }
    
    public void resumeRecording() {
        if (isRecording && mediaRecorder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.resume();
            
            // Update notification
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification("Recording meeting..."));
            }
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public String getCurrentFilePath() {
        return currentFilePath;
    }
    
    public long getRecordingDuration() {
        if (isRecording) {
            return System.currentTimeMillis() - recordingStartTime;
        }
        return 0;
    }
    
    // Methods for backup access to last recording info
    public String getLastRecordingPath() {
        return lastRecordingPath;
    }
    
    public long getLastRecordingDuration() {
        return lastRecordingDuration;
    }
    
    @Override
    public void onDestroy() {
        if (isRecording) {
            stopRecording();
        }
        super.onDestroy();
    }
}