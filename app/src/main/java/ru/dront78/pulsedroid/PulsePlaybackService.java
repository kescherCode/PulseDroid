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
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;

public class PulsePlaybackService extends Service implements PulsePlaybackWorker.Listener {
    /**
     * Unique ID for the Notification.
     */
    private static final int NOTIFICATION = R.string.playback_service_status;

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

        stop();
    }

    @Override
    public void onPlaybackError(@NonNull PulsePlaybackWorker worker, @NonNull Throwable t) {
        if (worker == playWorker) {
            notifyState(PlayState.STOPPED);
            stopForeground(true);
            stopSelf();
        }
    }

    @Override
    public void onPlaybackStarted(@NonNull PulsePlaybackWorker worker) {
        if (worker == playWorker) {
            notifyState(PlayState.STARTED);
        }
    }

    @Override
    public void onPlaybackStopped(@NonNull PulsePlaybackWorker worker) {
        if (worker == playWorker) {
            notifyState(PlayState.STOPPED);
            stopForeground(true);
            stopSelf();
        }
    }

    private NotificationCompat.Builder buildNotification(@StringRes int statusResId) {
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, PulseDroidActivity.class), 0);

        String text = getString(R.string.playback_service_status, getString(statusResId));

        return new NotificationCompat.Builder(this, getString(R.string.service_notification_channel))
                .setContentTitle(getText(R.string.playback_service_label))
                .setContentText(text)
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
        playWorker = new PulsePlaybackWorker(server, port, wakeLock, handler, this);
        playWorkerThread = new Thread(playWorker);

        Notification notif = buildNotification(R.string.playback_status_starting).build();
        startForeground(NOTIFICATION, notif);

        notifyState(PlayState.STARTING);

        playWorkerThread.start();

        // allow running in the background when service gets unbound
        startService(new Intent(this, PulsePlaybackService.class));
    }

    @MainThread
    public void stop() {
        if (getPlayState().isActive()) {
            notifyState(PlayState.STOPPING);
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

    private void notifyState(@NonNull PlayState state) {
        playState.setValue(state);
        int statusResId;
        switch (state) {
            case STOPPED:
                statusResId = R.string.playback_status_stopped;
                break;
            case STARTING:
                statusResId = R.string.playback_status_starting;
                break;
            case STARTED:
                statusResId = R.string.playback_status_playing;
                break;
            case STOPPING:
                statusResId = R.string.playback_status_stopping;
                break;
            default:
                throw new IllegalArgumentException();
        }
        Notification notif = buildNotification(statusResId).build();
        notifManager.notify(NOTIFICATION, notif);
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
