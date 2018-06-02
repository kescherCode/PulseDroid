package ru.dront78.pulsedroid;

import android.app.Notification;
import android.app.Service;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Binder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import android.app.PendingIntent;

import android.os.PowerManager;

public class LocalService extends Service {
    private NotificationManager mNM;

    PowerManager.WakeLock wakeLock = null;
    public String server = "";
    public String port = "";
    private PulseSoundThread playThread = null;
    private boolean isPlaying = false;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        LocalService getService() {
            return LocalService.this;
        }
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
        Notification notification = new NotificationCompat.Builder(this, getString(R.string.service_notification_channel))
                .setContentTitle("PulseDroid")
                .setContentText("Pulse Running")
                .setSmallIcon(R.drawable.ic_pulse)
                .build();
        startForeground(NOTIFICATION, notification);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "pulse");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(true);
        }

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
        stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, PulseDroid.class), 0);

        // Set the icon, scrolling text and timestamp
        Notification notification = new NotificationCompat.Builder(this, getString(R.string.service_notification_channel))
                .setContentText(text)
                .setContentTitle(getText(R.string.local_service_label))
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_pulse)
                .build();

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    public void play() {
        isPlaying = true;
        if (playThread != null) {
            stop();
        }
        Toast.makeText(this, R.string.local_service_playing, Toast.LENGTH_SHORT).show();
        playThread = new PulseSoundThread(server, port, wakeLock);
        new Thread(playThread).start();
    }

    public void stop() {
        isPlaying = false;
        Toast.makeText(this, R.string.local_service_paused, Toast.LENGTH_SHORT).show();
        if (playThread != null) {
            playThread.terminate();
            playThread = null;
        }
    }

}
