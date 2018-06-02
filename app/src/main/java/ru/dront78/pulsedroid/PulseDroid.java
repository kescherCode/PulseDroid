package ru.dront78.pulsedroid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;


public class PulseDroid extends Activity {

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
        bindService(new Intent(PulseDroid.this, LocalService.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    public void play() {
        final Button playButton = findViewById(R.id.ButtonPlay);
        final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        final EditText server = findViewById(R.id.EditTextServer);
        final EditText port = findViewById(R.id.EditTextPort);

        sharedPref.edit()
                .putString("server", server.getText().toString())
                .putString("port", port.getText().toString())
                .putBoolean("auto_start", autoStartCheckBox.isChecked())
                .apply();
        mBoundService.port = port.getText().toString();
        mBoundService.server = server.getText().toString();

        playButton.setText(R.string.btn_stop);
        mBoundService.play();
    }

    public void stop() {
        playButton.setText(R.string.btn_play);
        mBoundService.stop();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final EditText server = findViewById(R.id.EditTextServer);
        final EditText port = findViewById(R.id.EditTextPort);
        autoStartCheckBox = findViewById(R.id.auto_start);

        final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        server.setText(sharedPref.getString("server", ""));
        port.setText(sharedPref.getString("port", ""));
        autoStartCheckBox.setChecked(sharedPref.getBoolean("auto_start", false));

        // here is onButtonClick handler
        playButton = findViewById(R.id.ButtonPlay);
        playButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!mBoundService.isPlaying()) {
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
