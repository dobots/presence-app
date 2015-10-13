package nl.dobots.presence.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.R;
import nl.dobots.presence.cfg.Settings;
import nl.dobots.presence.ask.AskWrapper;


public class SettingsActivity extends ActionBarActivity {

	private Settings _settings;
	private AskWrapper _ask;

	private TextView _txtLogInStatus;
	private Button _btnLogIn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		_settings = Settings.getInstance();
		_ask = AskWrapper.getInstance();

		initUI();

		// pause detection while we are changing settings
		PresenceDetectionApp.getInstance().pauseDetection();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (_ask.isLoggedIn()) {
			_txtLogInStatus.setText("Logged In");
			_btnLogIn.setText("Change User");
		} else {
			_txtLogInStatus.setText("Not Logged In");
			_btnLogIn.setText("Log In");
		}

	}

	private void initUI() {

		_btnLogIn = (Button) findViewById(R.id.btnLogin);

		_txtLogInStatus = (TextView) findViewById(R.id.txtLogInStatus);

		final TextView detectionDistanceText = (TextView) findViewById(R.id.txtDetectionDistance);
		detectionDistanceText.setText(String.valueOf(_settings.getDetectionDistance()) + "m");

		final TextView highFrequencyDistanceText = (TextView) findViewById(R.id.txtHighFrequencyDistance);
		highFrequencyDistanceText.setText(String.valueOf(_settings.getHighFrequencyDistance()) + "m");

		final TextView lowFrequencyDistanceText = (TextView) findViewById(R.id.txtLowFrequencyDistance);
		lowFrequencyDistanceText.setText(String.valueOf(_settings.getLowFrequencyDistance()) + "m");

		SeekBar distanceBar= (SeekBar) findViewById(R.id.sbDetectionDistance);
		distanceBar.setProgress((int) _settings.getDetectionDistance() * 5);
		distanceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			int progress = 0;

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				_settings.setDetectionDistance((float) (progress / 5.0));
				detectionDistanceText.setText(String.valueOf(_settings.getDetectionDistance()) + "m");
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});

		SeekBar highFrequencyDistance = (SeekBar) findViewById(R.id.sbHighFrequencyDistance);
		highFrequencyDistance.setProgress((int) _settings.getHighFrequencyDistance() * 5);
		highFrequencyDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				_settings.setHighFrequencyDistance((float) (progress / 5.0));
				highFrequencyDistanceText.setText(String.valueOf(_settings.getHighFrequencyDistance()) + "m");
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});

		SeekBar lowFrequencyDistance = (SeekBar) findViewById(R.id.sbLowFrequencyDistance);
		lowFrequencyDistance.setProgress((int) _settings.getLowFrequencyDistance() * 5);
		lowFrequencyDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				_settings.setLowFrequencyDistance((float) (progress / 5.0));
				lowFrequencyDistanceText.setText(String.valueOf(_settings.getLowFrequencyDistance()) + "m");
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});

		CheckBox cbNotifications = (CheckBox) findViewById(R.id.cbNotifications);
		cbNotifications.setChecked(_settings.isNotificationsEnabled());
		cbNotifications.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				_settings.setNotificationsEnabled(isChecked);
			}
		});
	}

	public void clearSettings(View view) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Remove Settings");
		builder.setMessage("Your stored settings and locations will be removed! This cannot be undone!");
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				_settings.clearSettings(getApplicationContext());
				initUI();
			}
		});
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				/* nothing to do */
			}
		});
		builder.show();
	}

	public void logIn(View view) {
		LoginActivity.show(this);
	}

	public static void show(Context context) {
		context.startActivity(new Intent(context, SettingsActivity.class));
	}

	public void configureLocations(View view) {
		LocationsListActivity.show(this);
	}

	public void saveSettings(View view) {
		_settings.writePersistentSettings(this);
		finish();
	}
}
