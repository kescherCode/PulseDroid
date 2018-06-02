package ru.dront78.pulsedroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class PulseDroidActivity extends AppCompatActivity {

    private Button playButton = null;
    private PulsePlaybackService boundService;
    private CheckBox autoStartCheckBox = null;
    private TextView errorText;

    private boolean isBound = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            boundService = ((PulsePlaybackService.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            Toast.makeText(PulseDroidActivity.this, R.string.local_service_connected,
                    Toast.LENGTH_SHORT).show();

            boundService.playState().observe(PulseDroidActivity.this,
                    playState -> updatePlayState(playState));

            if (autoStartCheckBox.isChecked()) {
                play();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            boundService.playState().removeObservers(PulseDroidActivity.this);

            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            boundService = null;

            Toast.makeText(PulseDroidActivity.this, R.string.local_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final EditText server = findViewById(R.id.EditTextServer);
        final EditText port = findViewById(R.id.EditTextPort);
        autoStartCheckBox = findViewById(R.id.auto_start);
        playButton = findViewById(R.id.ButtonPlay);
        errorText = findViewById(R.id.errorText);

        final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        server.setText(sharedPref.getString("server", ""));
        port.setText(sharedPref.getString("port", ""));
        autoStartCheckBox.setChecked(sharedPref.getBoolean("auto_start", false));

        playButton.setOnClickListener(v -> {
            if (boundService.getPlayState().isActive()) {
                stop();
            } else {
                play();
            }
        });

        findViewById(R.id.ButtonExit).setOnClickListener(v -> {
            stop();
            finish();
        });

        doBindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

	private void doBindService() {
		// Establish a connection with the service.  We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		bindService(new Intent(PulseDroidActivity.this, PulsePlaybackService.class),
				mConnection, Context.BIND_AUTO_CREATE);
		isBound = true;
	}

	private void doUnbindService() {
		if (isBound) {
			// Detach our existing connection.
			unbindService(mConnection);
			isBound = false;
		}
	}

    private void updatePlayState(@Nullable PlayState playState) {
        if (playState == null) {
            playButton.setText(R.string.btn_waiting);
            playButton.setEnabled(false);
            return;
        }
        switch (playState) {
            case STOPPED:
                playButton.setText(R.string.btn_play);
                playButton.setEnabled(true);
                break;
            case STARTING:
                playButton.setText(R.string.btn_starting);
                playButton.setEnabled(false);
                break;
            case STARTED:
                playButton.setText(R.string.btn_stop);
                playButton.setEnabled(true);
                break;
            case STOPPING:
                playButton.setText(R.string.btn_stopping);
                playButton.setEnabled(false);
                break;
        }
        Throwable error = boundService == null ? null : boundService.getError();
        if (error != null) {
            String msg = error.getLocalizedMessage();
            String text = error.getClass().getName()
                    + (msg == null ? " (No error message)" : " " + msg);
            errorText.setText(getString(R.string.play_error, text));
        }
    }

	public void play() {
		final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
		final EditText server = findViewById(R.id.EditTextServer);
		final EditText port = findViewById(R.id.EditTextPort);

		sharedPref.edit()
				.putString("server", server.getText().toString())
				.putString("port", port.getText().toString())
				.putBoolean("auto_start", autoStartCheckBox.isChecked())
				.apply();

		if (boundService != null) {
            boundService.port = port.getText().toString();
            boundService.server = server.getText().toString();
            boundService.play();
        }
	}

	public void stop() {
        if (boundService != null) {
            boundService.stop();
        }
	}

}
