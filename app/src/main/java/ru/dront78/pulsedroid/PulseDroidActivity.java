package ru.dront78.pulsedroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;


public class PulseDroidActivity extends AppCompatActivity {

    private Button playButton = null;
    private Spinner bufferSizeSpinner;
    private BufferSizeAdapter bufferSizeAdapter;
    private CheckBox autoStartCheckBox = null;
    private TextView errorText;

    private PulsePlaybackService boundService;
    private boolean isBound = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            boundService = ((PulsePlaybackService.LocalBinder) service).getService();

            boundService.playState().observe(PulseDroidActivity.this,
                    playState -> updatePlayState(playState));

            if (autoStartCheckBox.isChecked() && boundService.isStartable()) {
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
        bufferSizeSpinner = findViewById(R.id.bufferSizeSpinner);

        final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        bufferSizeAdapter = new BufferSizeAdapter(this);
        bufferSizeSpinner.setAdapter(bufferSizeAdapter);

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int bufferMillis = preferences.getInt("buffer_ms", 2000);
        int pos = bufferSizeAdapter.getItemPosition(bufferMillis);
        bufferSizeSpinner.setSelection(pos >= 0 ? pos : BufferSizeAdapter.DEFAULT_INDEX);

        bufferSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int millis = bufferSizeAdapter.getItem(position);
                getPreferences(MODE_PRIVATE).edit()
                        .putInt("buffer_ms", millis)
                        .apply();
                if (boundService != null) {
                    boundService.setBufferMillis(millis);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

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
                playButton.setEnabled(true);
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
            String text = formatMessage(error);
            errorText.setText(getString(R.string.play_error, text));
        } else {
            errorText.setText("");
        }
    }

    public void play() {
        final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        final EditText serverText = findViewById(R.id.EditTextServer);
        final EditText portText = findViewById(R.id.EditTextPort);

        String server = serverText.getText().toString();
        int port;
        try {
            port = Integer.parseInt(portText.getText().toString());
        } catch (NumberFormatException e) {
            portText.setError(formatMessage(e));
            return;
        }
        portText.setError(null);
        sharedPref.edit()
                .putString("server", server)
                .putString("port", Integer.toString(port))
                .putBoolean("auto_start", autoStartCheckBox.isChecked())
                .apply();
        int bufferSize = bufferSizeAdapter.getItem(bufferSizeSpinner.getSelectedItemPosition());

        if (boundService != null) {
            boundService.setBufferMillis(bufferSize);
            boundService.play(server, port);
        }
    }

    public void stop() {
        if (boundService != null) {
            boundService.stop();
        }
    }

    @NonNull
    private String formatMessage(Throwable error) {
        String msg = error.getLocalizedMessage();
        return error.getClass().getName()
                + (msg == null ? " (No error message)" : ": " + msg);
    }

}
