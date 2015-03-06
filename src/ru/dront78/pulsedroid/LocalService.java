package ru.dront78.pulsedroid;

import android.app.Service;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;
import android.widget.Toast;
import android.app.Notification;
import android.app.PendingIntent;
import ru.dront78.pulsedroid.PulseDroid;
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;

public class LocalService extends Service {
    private NotificationManager mNM;

    PowerManager.WakeLock wl = null;
	public String server = "";
	public String port = "";
	PulseSoundThread playThread = null;
	boolean playState = false;

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
        Notification notification = new Notification.Builder(this)
			.setContentTitle("PulseDroid")
			.setContentText("Pulse Running")
			.build();
        startForeground(NOTIFICATION, notification);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "pulse");
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

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification.Builder(this)
			.setContentTitle("PulseDroid")
			.setContentText(text)
			.build();

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
																new Intent(this, PulseDroid.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.local_service_label),
										text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

	public void play() {
		playState = true;
		if (null != playThread) {
			stop();
		}
        Toast.makeText(this, R.string.local_service_playing, Toast.LENGTH_SHORT).show();
		playThread = new PulseSoundThread(server, port);
        playThread.wl = wl;
		new Thread(playThread).start();
	}

	public void stop() {
		playState = false;
        Toast.makeText(this, R.string.local_service_paused, Toast.LENGTH_SHORT).show();
		if (null != playThread) {
			playThread.Terminate();
			playThread = null;
		}
	}

}
