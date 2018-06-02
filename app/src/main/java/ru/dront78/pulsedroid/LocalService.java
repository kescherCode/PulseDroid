package ru.dront78.pulsedroid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class LocalService extends Service implements PulseSoundThread.Listener {
    /**
     * Unique Identification Number for the Notification.
     */
    private static final int NOTIFICATION = R.string.local_service_started;

    private final IBinder mBinder = new LocalBinder();

    private Handler handler = new Handler();
    private NotificationManager notifManager;
    private PowerManager.WakeLock wakeLock;

    @NonNull
    public String server = "";
    @NonNull
    public String port = "";
    @Nullable
    private PulseSoundThread playThread = null;

    private final MutableLiveData<PlayState> playState = new MutableLiveData<>();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        handler = new Handler();
        notifManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        playState.setValue(PlayState.STOPPED);

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(true);
        } else {
            notifManager.cancel(NOTIFICATION);
        }

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
        stop();
    }

    @Override
    public void onPlaybackError(@NonNull PulseSoundThread thread, @NonNull Throwable t) {
        if (thread == playThread) {
            playState.setValue(PlayState.STOPPED);
        }
    }

    @Override
    public void onPlaybackStarted(@NonNull PulseSoundThread thread) {
        if (thread == playThread) {
            playState.setValue(PlayState.STARTED);
        }
    }

    @Override
    public void onPlaybackStopped(@NonNull PulseSoundThread thread) {
        if (thread == playThread) {
            playState.setValue(PlayState.STOPPED);
        }
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
        notifManager.notify(NOTIFICATION, notification);
    }

    public void play() {
        if (!isStartable()) {
            throw new IllegalStateException("Cannot start with playState == " + getPlayState());
        }
        if (playThread != null) {
            stopThread();
        }
        playState.setValue(PlayState.STARTING);
        Toast.makeText(this, R.string.local_service_playing, Toast.LENGTH_SHORT).show();
        playThread = new PulseSoundThread(server, port, wakeLock, handler, this);
        new Thread(playThread).start();
    }

    public void stop() {
        if (getPlayState().isActive()) {
            playState.setValue(PlayState.STOPPING);
            Toast.makeText(this, R.string.local_service_paused, Toast.LENGTH_SHORT).show();
        }
        stopThread();
    }

    private void stopThread() {
        if (playThread != null) {
            playThread.stop();
        }
    }

    public boolean isStartable() {
        return getPlayState() == PlayState.STOPPED;
    }

    public PlayState getPlayState() {
        return playState.getValue();
    }

    @NonNull
    public LiveData<PlayState> playState() {
        return playState;
    }

    public Throwable getError() {
        return playThread == null ? null : playThread.getError();
    }

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

}
