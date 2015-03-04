package ru.dront78.pulsedroid;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CheckBox;
import android.content.SharedPreferences;
import android.content.Context;

public class PulseDroid extends Activity {
	/** Called when the activity is first created. */

	boolean playState = false;
	PulseSoundThread playThread = null;

	public void play() {
		final Button playButton = (Button) findViewById(R.id.ButtonPlay);
		final SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = sharedPref.edit();
		final EditText server = (EditText) findViewById(R.id.EditTextServer);
		final EditText port = (EditText) findViewById(R.id.EditTextPort);
		final CheckBox checkBox = (CheckBox) findViewById(R.id.auto_start);

		playState = true;
		playButton.setText("Stop");
		if (null != playThread) {
			playThread.Terminate();
			playThread = null;
		}
		editor.putString("server", server.getText().toString());
		editor.putString("port", port.getText().toString());
		editor.putBoolean("auto_start", checkBox.isChecked());
		editor.commit();
		playThread = new PulseSoundThread(server.getText()
										  .toString(), port.getText().toString());
		new Thread(playThread).start();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		final EditText server = (EditText) findViewById(R.id.EditTextServer);
		final EditText port = (EditText) findViewById(R.id.EditTextPort);
		final CheckBox checkBox = (CheckBox) findViewById(R.id.auto_start);

		final SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = sharedPref.edit();

		server.setText(sharedPref.getString("server", ""));
		port.setText(sharedPref.getString("port", ""));
		checkBox.setChecked(sharedPref.getBoolean("auto_start", true));

		// here is onButtonClick handler
		final Button playButton = (Button) findViewById(R.id.ButtonPlay);
		playButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (false == playState) {
						play();
					} else {
						playState = false;
						playButton.setText("Play!");
						if (null != playThread) {
							playThread.Terminate();
							playThread = null;
						}
					}
				}
			});

		findViewById(R.id.ButtonExit).setOnClickListener(
														 new View.OnClickListener() {
															 public void onClick(View v) {
																 playState = false;
																 playButton.setText("Play!");
																 if (null != playThread) {
																	 playThread.Terminate();
																	 playThread = null;
																 }
																 moveTaskToBack(true);
															 }
														 });
	}
}
