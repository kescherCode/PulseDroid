package ru.dront78.pulsedroid;

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
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

public class PulsePlaybackService extends Service implements PulsePlaybackWorker.Listener {
    /**
     * Unique Identification Number for the Notification.
     */
    private static final int NOTIFICATION = R.string.playback_service_started;

    private final IBinder binder = new LocalBinder();

    private Handler handler = new Handler();
    private NotificationManager notifManager;
    private PowerManager.WakeLock wakeLock;

    @NonNull
    public String server = "";
    @NonNull
    public String port = "";
    @Nullable
    private PulsePlaybackWorker playWorker = null;
    @Nullable
    private Thread playWorkerThread;

    private final MutableLiveData<PlayState> playState = new MutableLiveData<>();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        handler = new Handler();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        playState.setValue(PlayState.STOPPED);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        assert pm != null;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulse");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
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
        Toast.makeText(this, R.string.playback_service_stopped, Toast.LENGTH_SHORT).show();
        stop();
    }

    @Override
    public void onPlaybackError(@NonNull PulsePlaybackWorker worker, @NonNull Throwable t) {
        if (worker == playWorker) {
            playState.setValue(PlayState.STOPPED);
            stopForeground(true);
            stopSelf();
        }
    }

    @Override
    public void onPlaybackStarted(@NonNull PulsePlaybackWorker worker) {
        if (worker == playWorker) {
            playState.setValue(PlayState.STARTED);
        }
    }

    @Override
    public void onPlaybackStopped(@NonNull PulsePlaybackWorker worker) {
        if (worker == playWorker) {
            playState.setValue(PlayState.STOPPED);
            stopForeground(true);
            stopSelf();
        }
    }

    private NotificationCompat.Builder buildNotification() {
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, PulseDroidActivity.class), 0);

        // Set the icon, scrolling text and timestamp
        return new NotificationCompat.Builder(this, getString(R.string.service_notification_channel))
                .setContentTitle(getText(R.string.playback_service_label))
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_pulse);
    }

    @MainThread
    public void play() {
        if (!isStartable()) {
            throw new IllegalStateException("Cannot start with playState == " + getPlayState());
        }
        if (playWorker != null) {
            stopWorker();
        }
        Toast.makeText(this, R.string.playback_service_playing, Toast.LENGTH_SHORT).show();
        playWorker = new PulsePlaybackWorker(server, port, wakeLock, handler, this);
        playWorkerThread = new Thread(playWorker);

        playState.setValue(PlayState.STARTING);

        playWorkerThread.start();

        startForeground(NOTIFICATION, buildNotification()
                .setContentText(getText(R.string.playback_service_started))
                .build());
        // allow running in the background when service gets unbound
        startService(new Intent(this, PulsePlaybackService.class));
    }

    @MainThread
    public void stop() {
        if (getPlayState().isActive()) {
            playState.setValue(PlayState.STOPPING);
            Toast.makeText(this, R.string.playback_service_paused, Toast.LENGTH_SHORT).show();
        }
        stopWorker();
    }

    @MainThread
    private void stopWorker() {
        if (playWorker != null) {
            playWorker.stop();
        }
        if (playWorkerThread != null) {
            playWorkerThread.interrupt();
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
        return playWorker == null ? null : playWorker.getError();
    }

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        PulsePlaybackService getService() {
            return PulsePlaybackService.this;
        }
    }

}
