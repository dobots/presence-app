package nl.dobots.presence.gui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;

import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.PresenceUpdateListener;
import nl.dobots.presence.srv.BleScanService;
import nl.dobots.presence.R;
import nl.dobots.presence.ScanDeviceListener;
import nl.dobots.presence.Settings;
import nl.dobots.presence.ask.AskWrapper;


public class MainActivity extends ActionBarActivity implements ScanDeviceListener, PresenceUpdateListener {

	private static final String TAG = MainActivity.class.getCanonicalName();

	private AskWrapper _ask;
	private Settings _settings;

	private TextView _txtFreqScanning;
	private TextView _txtCurrentDistance;
	private boolean _lastHighFrequency;
	private Button _btnToggleScan;

	private BleScanService _service;
	private boolean _bound;
	private TextView _txtCurrentPresence;
	private TextView _txtCurrentLocation;
	private LinearLayout _layExpirationTime;
	private TextView _txtRemainingExpirationTime;

	private Handler _uiHandler = new Handler();
	private Handler _handler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setTitle(R.string.title_activity_main);

		_settings = Settings.getInstance();

		_ask = AskWrapper.getInstance();

//		if (!Utils.isServiceRunning(this, "BleScanService")) {
//
//			Intent startServiceIntent = new Intent(this, BleScanService.class);
//			this.startService(startServiceIntent);
//
//			Toast.makeText(this, "Starting service...", Toast.LENGTH_LONG);
//		} else {
//
//			Toast.makeText(this, "Service already running ...", Toast.LENGTH_LONG);
//		}


		HandlerThread handlerThread = new HandlerThread("Watchdog");
		handlerThread.start();
		_handler = new Handler(handlerThread.getLooper());

		if (getIntent().getAction() == "android.intent.action.MAIN") {


			initUI();

			Intent intent = new Intent(this, BleScanService.class);
			bindService(intent, _connection, Context.BIND_AUTO_CREATE);

		} else {
//
//			Intent startServiceIntent = new Intent(this, BleScanService.class);
//			this.startService(startServiceIntent);

			finish();
		}

		PresenceDetectionApp.getInstance().registerPresenceUpdateListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (_service != null) {
			_service.registerScanDeviceListener(MainActivity.this);
		}
		PresenceDetectionApp.getInstance().resumeDetection();

		// check if login information is present, otherwise ..
		if (!_ask.isLoginCredentialsValid(_settings.getUsername(), _settings.getPassword())) {
			Toast.makeText(this, "Need to fill in login credentials before using the app", Toast.LENGTH_LONG).show();
			// go first to login page
			LoginActivity.show(this);
		} else if (_settings.getLocationsList().isEmpty()) {
			Toast.makeText(this, "Need to configure locations in order to use the app", Toast.LENGTH_LONG).show();
			// next time, if no locations are configured go to the locations page
			LocationsListActivity.show(this);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (_bound) {
			_service.unregisterScanDeviceListener(MainActivity.this);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (_bound) {
			unbindService(_connection);
		}
		PresenceDetectionApp.getInstance().unregisterPresenceUpdateListener(this);
	}

	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "connected to ble scan service ...");
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();
			_service.registerScanDeviceListener(MainActivity.this);
			_bound = true;

			if (_service.isScanning()) {
				_btnToggleScan.setText("Stop");
			} else {
				_btnToggleScan.setText("Start");
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
			_bound = false;
		}
	};

	private void updateCurrentDistance(final String name, final double distance) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
			_txtCurrentDistance.setText("closest beacon: " + name + " at " + String.valueOf(distance) + "m");
			}
		});
	}

	private void updateScanningFrequency(boolean highFrequency) {
		if (_lastHighFrequency == highFrequency) return;

		if (highFrequency) {
			_txtFreqScanning.setText("High Frequency Scanning");
		} else {
			_txtFreqScanning.setText("Low Frequency Scanning");
		}

		_lastHighFrequency = highFrequency;
	}

	private void initUI() {

        _txtFreqScanning = (TextView) findViewById(R.id.txtFreqScanning);
		_txtCurrentDistance = (TextView) findViewById(R.id.txtCurrentDistance);
		_btnToggleScan = (Button) findViewById(R.id.btnToggleScan);

		_txtCurrentPresence = (TextView) findViewById(R.id.txtPresence);
		_txtCurrentPresence.setText("Unknown");

		_txtCurrentLocation = (TextView) findViewById(R.id.txtLocation);
		_txtCurrentLocation.setVisibility(View.GONE);

		_layExpirationTime = (LinearLayout) findViewById(R.id.layExpirationTime);
		_layExpirationTime.setVisibility(View.INVISIBLE);
		_txtRemainingExpirationTime = (TextView) findViewById(R.id.txtRemainingExpirationTime);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
		case R.id.action_settings: {
			SettingsActivity.show(this);
			return true;
		}
		case R.id.action_login: {
			LoginActivity.show(this);
		}
		}

		return super.onOptionsItemSelected(item);
	}

	public void toggleScan(View view) {
		if (_bound) {
			if (_service.isScanning()) {
				_service.stopIntervalScan();
				_btnToggleScan.setText("Start");
			} else {
				_service.startIntervalScan();
				_btnToggleScan.setText("Stop");
			}
		}
	}

	public void autoPresence(View view) {
		stopExpirationUpdate();
		PresenceDetectionApp.getInstance().setAutoPresence();
	}

	public void manualPresent(View view) {
		manualPresence(true);
	}

	public void manualNotPresent(View view) {
		manualPresence(false);
	}

	private void manualPresence(final boolean present) {

		String[] expirationTimesDisplay = { "1 min", "15 min", "1 h", "2 h", "4 h", "8 h"};
		final long[] expirationTimes = { 1, 15, 60, 120, 240, 480}; // in minutes

		final ArrayAdapter<String> adp = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, expirationTimesDisplay);
		final Spinner sp = new Spinner(this);
		sp.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		sp.setAdapter(adp);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Set manual presence");
		builder.setMessage("You can override your presence manually. Choose an expiration time and confirm. " +
				"Once the selected time expires, automatic presence control will continue.");
		builder.setView(sp);
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {

				final long expirationTime = expirationTimes[sp.getSelectedItemPosition()] * 60 * 1000; // in ms

				_handler.post(new Runnable() {
					@Override
					public void run() {
						PresenceDetectionApp.getInstance().setManualPresence(present, expirationTime);
					}
				});

				startExpirationUpdate();
			}
		});
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				/* nothing to do */
			}
		});
		builder.show();
	}

	private void startExpirationUpdate() {
		_layExpirationTime.setVisibility(View.VISIBLE);

		Runnable expirationUpdater = new Runnable() {
			@Override
			public void run() {
				Date expirationDate = PresenceDetectionApp.getInstance().getExpirationDate();
				Date now = new Date();
				if (now.after(expirationDate)) {
					_layExpirationTime.setVisibility(View.INVISIBLE);
					return;
				} else {
					long diff = expirationDate.getTime() - now.getTime();
					int hours = (int) (diff / (60 * 60 * 1000));
					int mins = (int) (diff / (60 * 1000)) % 60;
					int secs = (int) (diff / 1000) % 60;
					if (hours > 0) {
						_txtRemainingExpirationTime.setText(String.format("%1d h %2d m %2d s", hours, mins, secs));
					} else if (mins > 0) {
						_txtRemainingExpirationTime.setText(String.format("    %2d m %2d s", mins, secs));
					} else {
						_txtRemainingExpirationTime.setText(String.format("         %2d s", secs));
					}
				}
				_uiHandler.postDelayed(this, 500);
			}
		};

		_uiHandler.postDelayed(expirationUpdater, 500);
	}

	private void stopExpirationUpdate() {
		_uiHandler.removeCallbacksAndMessages(null);
		_layExpirationTime.setVisibility(View.INVISIBLE);
	}


//	BleDeviceMap _deviceMap = new BleDeviceMap();

	String text = "";
	@Override
	public void onDeviceScanned(BleDevice device) {
//		device = _deviceMap.updateDevice(device);
//		device.refresh();

		if (_bound) {
			BleDeviceMap deviceMap = _service.getDeviceMap();
			deviceMap.refresh();
			final ArrayList<BleDevice> list = deviceMap.getDistanceSortedList();
//		BleDevice dev = list.get(0);
//		updateCurrentDistance(dev.getName(), dev.getDistance());

			final TextView _txtCurrentDistance2 = (TextView) findViewById(R.id.txtCurrentDistance2);
			final TextView _txtCurrentDistance3 = (TextView) findViewById(R.id.txtCurrentDistance3);
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					BleDevice dev = list.get(0);
					_txtCurrentDistance.setText("1: " + dev.getName() + " at " + String.format("%.2f m", dev.getDistance()));
					if (list.size() > 1) {
						dev = list.get(1);
						_txtCurrentDistance2.setText("2: " + dev.getName() + " at " + String.format("%.2f m", dev.getDistance()));
						if (list.size() > 2) {
							dev = list.get(2);
							_txtCurrentDistance3.setText("3: " + dev.getName() + " at " + String.format("%.2f m", dev.getDistance()));
						}
					}
				}
			});
		}
	}

	@Override
	public void onPresenceUpdate(final boolean present, final String location, final String additionalInfo) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
//				_txtCurrentPresence.setText(present ? "Present" : "Not Present");
				if (present) {
					_txtCurrentPresence.setText(String.format("at %s (%s m)", location, additionalInfo));
					_txtCurrentPresence.setTextColor(0xFF339933);
//					_txtCurrentLocation.setVisibility(View.VISIBLE);
//					_txtCurrentLocation.setText(String.format("%s (%.2f)", location, distance));
				} else {
					_txtCurrentPresence.setText("Not present");
					_txtCurrentPresence.setTextColor(Color.RED);
//					_txtCurrentLocation.setVisibility(View.INVISIBLE);
				}
			}
		});
	}

}
