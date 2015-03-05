package ru.dront78.pulsedroid;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CheckBox;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.widget.Toast;
import android.app.Notification;
import android.content.Intent;

import ru.dront78.pulsedroid.LocalService;


public class PulseDroid extends Activity {
	/** Called when the activity is first created. */

	boolean mIsBound = false;
	Button playButton = null;
	private LocalService mBoundService;
	CheckBox autoStartCheckBox = null;

	private ServiceConnection mConnection = new ServiceConnection() {
			public void onServiceConnected(ComponentName className, IBinder service) {
				// This is called when the connection with the service has been
				// established, giving us the service object we can use to
				// interact with the service.  Because we have bound to a explicit
				// service that we know is running in our own process, we can
				// cast its IBinder to a concrete class and directly access it.
				mBoundService = ((LocalService.LocalBinder)service).getService();

				// Tell the user about this for our demo.
				Toast.makeText(PulseDroid.this, R.string.local_service_connected,
							   Toast.LENGTH_SHORT).show();
				if (autoStartCheckBox.isChecked()) {
					play();
				}
			}

			public void onServiceDisconnected(ComponentName className) {
				// This is called when the connection with the service has been
				// unexpectedly disconnected -- that is, its process crashed.
				// Because it is running in our same process, we should never
				// see this happen.
				mBoundService = null;
				Toast.makeText(PulseDroid.this, R.string.local_service_disconnected,
							   Toast.LENGTH_SHORT).show();
			}
		};

	void doBindService() {
		// Establish a connection with the service.  We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		bindService(new Intent(PulseDroid.this,
							   LocalService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void doUnbindService() {
		if (mIsBound) {
			// Detach our existing connection.
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	public void play () {
		final Button playButton = (Button) findViewById(R.id.ButtonPlay);
		final SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = sharedPref.edit();
		final EditText server = (EditText) findViewById(R.id.EditTextServer);
		final EditText port = (EditText) findViewById(R.id.EditTextPort);

		editor.putString("server", server.getText().toString());
		editor.putString("port", port.getText().toString());
		editor.putBoolean("auto_start", autoStartCheckBox.isChecked());
		editor.commit();
		mBoundService.port = port.getText().toString();
		mBoundService.server = server.getText().toString();

		playButton.setText("Stop");
		mBoundService.play();
	}

	public void stop () {
		playButton.setText("Play!");
		mBoundService.stop();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		final EditText server = (EditText) findViewById(R.id.EditTextServer);
		final EditText port = (EditText) findViewById(R.id.EditTextPort);
		autoStartCheckBox = (CheckBox) findViewById(R.id.auto_start);

		final SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = sharedPref.edit();

		server.setText(sharedPref.getString("server", ""));
		port.setText(sharedPref.getString("port", ""));
		autoStartCheckBox.setChecked(sharedPref.getBoolean("auto_start", false));

		// here is onButtonClick handler
		playButton = (Button) findViewById(R.id.ButtonPlay);
		playButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (false == mBoundService.playState) {
						play();
					} else {
						stop();
					}
				}
			});

		findViewById(R.id.ButtonExit).setOnClickListener(
														 new View.OnClickListener() {
															 public void onClick(View v) {
																 stop();
																 moveTaskToBack(true);
															 }
														 });

		doBindService();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

}
