package com.ns.dev.jdkhandlookingdeep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class MediaScanService extends Service {

    private static final String TAG = "MediaScanService";
    private static final String CHANNEL_ID = "media_scan_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String SCAN_FILE = "media_list.json";

    private final IBinder binder = new LocalBinder();
    private List<MediaItem> mediaList = new ArrayList<>();

    public class LocalBinder extends Binder {
        public MediaScanService getService() {
            return MediaScanService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Scanning media..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start scanning in a background thread
        new Thread(() -> {
            scanMedia();
            saveMediaListToFile();
            stopSelf(); // Stop service when done
        }).start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void scanMedia() {
        mediaList.clear();
        scanAudio();
        scanVideo();
        Log.d(TAG, "Scanned " + mediaList.size() + " media files");
    }

    private void scanAudio() {
        ContentResolver resolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
        };
        String selection = MediaStore.Audio.Media.MIME_TYPE + " IN ('audio/mpeg', 'audio/mp4')";
        try (Cursor cursor = resolver.query(uri, projection, selection, null, null)) {
            if (cursor != null) {
                int titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int pathIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int mimeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                int durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
                while (cursor.moveToNext()) {
                    String title = cursor.getString(titleIdx);
                    String path = cursor.getString(pathIdx);
                    String mime = cursor.getString(mimeIdx);
                    long duration = cursor.getLong(durIdx);
                    long size = cursor.getLong(sizeIdx);
                    if (path != null && new File(path).exists()) {
                        mediaList.add(new MediaItem(title, path, mime, duration, size));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning audio", e);
        }
    }

    private void scanVideo() {
        ContentResolver resolver = getContentResolver();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
        };
        String selection = MediaStore.Video.Media.MIME_TYPE + " = 'video/mp4'";
        try (Cursor cursor = resolver.query(uri, projection, selection, null, null)) {
            if (cursor != null) {
                int titleIdx = cursor.getColumnIndex(MediaStore.Video.Media.TITLE);
                int pathIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                int mimeIdx = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE);
                int durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                int sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);
                while (cursor.moveToNext()) {
                    String title = cursor.getString(titleIdx);
                    String path = cursor.getString(pathIdx);
                    String mime = cursor.getString(mimeIdx);
                    long duration = cursor.getLong(durIdx);
                    long size = cursor.getLong(sizeIdx);
                    if (path != null && new File(path).exists()) {
                        mediaList.add(new MediaItem(title, path, mime, duration, size));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning video", e);
        }
    }

    private void saveMediaListToFile() {
        File file = new File(getFilesDir(), SCAN_FILE);
        MediaItem.saveToFile(mediaList, file);
    }

    public List<MediaItem> getMediaList() {
        return mediaList;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Scan Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Media Scanner")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentIntent(pendingIntent)
                .build();
    }
}
