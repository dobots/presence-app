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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;

import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.Config;
import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.PresenceUpdateListener;
import nl.dobots.presence.srv.BleScanService;
import nl.dobots.presence.R;
import nl.dobots.presence.ScanDeviceListener;
import nl.dobots.presence.Settings;
import nl.dobots.presence.ask.AskWrapper;

public class MainActivity extends ActionBarActivity implements ScanDeviceListener, PresenceUpdateListener {

	private static final String TAG = MainActivity.class.getCanonicalName();

	private PresenceDetectionApp _app;
	private AskWrapper _ask;
	private Settings _settings;
	private BleScanService _service;

	private TextView _txtFreqScanning;
	private TextView _txtCurrentDistance;
	private boolean _lastHighFrequency;
	private Button _btnToggleScan;

	private TextView _txtCurrentPresence;
	private LinearLayout _layExpirationTime;
	private TextView _txtRemainingExpirationTime;

	private LinearLayout _layManualControl;
	private TextView _lblOverride;
	private ImageView _btnExpandManualPresence;

	private Handler _uiHandler = new Handler();
	private Handler _networkHandler;

	private boolean _serviceBound;

	private boolean _scanningStateChanged = true;
	private boolean _presenceChanged = true;

	private boolean _currentPresence;
	private String _currentLocation;
	private String _currentAdditionalInfo;

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
		_networkHandler = new Handler(handlerThread.getLooper());

		_app = PresenceDetectionApp.getInstance();

		initUI();

		// get current state from app
		_currentPresence = _app.getCurrentPresence();
		_currentLocation = _app.getCurrentLocation();
		_currentAdditionalInfo = _app.getCurrentAdditionalInfo();

		if (_app.getExpirationDate() != null) {
			startExpirationUpdate();
		}

		// connect to service
		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);

		// start UI updater
		_uiHandler.postDelayed(_uiUpdateRunnable, Config.UI_UPDATE_INTERVAL);




//		if (getIntent().getAction() == "android.intent.action.MAIN") {
//
//			initUI();
//
//			Intent intent = new Intent(this, BleScanService.class);
//			bindService(intent, _connection, Context.BIND_AUTO_CREATE);
//
//		} else {
////
////			Intent startServiceIntent = new Intent(this, BleScanService.class);
////			this.startService(startServiceIntent);
//
//			finish();
//		}

		_app.registerPresenceUpdateListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (_serviceBound) {
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
		if (_serviceBound) {
			_service.unregisterScanDeviceListener(MainActivity.this);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (_serviceBound) {
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
			_serviceBound = true;

			if (_service.isScanning()) {
				_btnToggleScan.setText("Stop");
			} else {
				_btnToggleScan.setText("Start");
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
			_serviceBound = false;
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

		_layExpirationTime = (LinearLayout) findViewById(R.id.layExpirationTime);
		_layExpirationTime.setVisibility(View.INVISIBLE);
		_txtRemainingExpirationTime = (TextView) findViewById(R.id.txtRemainingExpirationTime);

		_layManualControl = (LinearLayout) findViewById(R.id.layManualControl);
		_lblOverride = (TextView) findViewById(R.id.lblOverride);
		_btnExpandManualPresence = (ImageView) findViewById(R.id.btnExpandManualPresence);

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
		if (_serviceBound) {
			if (_service.isScanning()) {
				_service.stopIntervalScan();
			} else {
				_service.startIntervalScan();
			}
			_scanningStateChanged = true;
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

		final ArrayAdapter<String> adp = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, Config.EXPIRATION_TIMES_DISPLAY);
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

				final long expirationTime = Config.EXPIRATION_TIMES[sp.getSelectedItemPosition()] * 60 * 1000; // to ms

				_networkHandler.post(new Runnable() {
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
		_btnToggleScan.setVisibility(View.INVISIBLE);
		_lblOverride.setVisibility(View.GONE);
		_layExpirationTime.setVisibility(View.VISIBLE);

		_uiHandler.postDelayed(_expirationUpdater, 500);
	}

	private void stopExpirationUpdate() {
		_uiHandler.removeCallbacks(_expirationUpdater);

		_btnToggleScan.setVisibility(View.VISIBLE);
		_lblOverride.setVisibility(View.VISIBLE);
		_layExpirationTime.setVisibility(View.INVISIBLE);
	}


//	BleDeviceMap _deviceMap = new BleDeviceMap();

	@Override
	public void onDeviceScanned(BleDevice device) {
//		device = _deviceMap.updateDevice(device);
//		device.refresh();

		if (_serviceBound) {
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

		_currentPresence = present;
		_currentLocation = location;
		_currentAdditionalInfo = additionalInfo;
		_presenceChanged = true;

//		runOnUiThread(new Runnable() {
//			@Override
//			public void run() {
////				_txtCurrentPresence.setText(present ? "Present" : "Not Present");
//				if (present) {
//					_txtCurrentPresence.setText(String.format("at %s (%s m)", location, additionalInfo));
//					_txtCurrentPresence.setTextColor(0xFF339933);
////					_txtCurrentLocation.setVisibility(View.VISIBLE);
////					_txtCurrentLocation.setText(String.format("%s (%.2f)", location, distance));
//				} else {
//					_txtCurrentPresence.setText("Not present");
//					_txtCurrentPresence.setTextColor(Color.RED);
////					_txtCurrentLocation.setVisibility(View.INVISIBLE);
//				}
//			}
//		});
	}

	private boolean expandedManualView = false;
	public void expandManualPresence(View view) {
		if (!expandedManualView) {
			_layManualControl.setVisibility(View.VISIBLE);
			_btnExpandManualPresence.setImageResource(android.R.drawable.arrow_up_float);
		} else {
			_layManualControl.setVisibility(View.GONE);
			_btnExpandManualPresence.setImageResource(android.R.drawable.arrow_down_float);
		}
		expandedManualView = !expandedManualView;
	}

	private Runnable _expirationUpdater = new Runnable() {
		@Override
		public void run() {
			Date expirationDate = PresenceDetectionApp.getInstance().getExpirationDate();
			Date now = new Date();
			if (expirationDate == null || now.after(expirationDate)) {
				stopExpirationUpdate();
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

	private Runnable _uiUpdateRunnable = new Runnable() {
		@Override
		public void run() {
			if (_serviceBound && _scanningStateChanged) {
				if (_service.isScanning()) {
					_btnToggleScan.setText("Stop");
				} else {
					_btnToggleScan.setText("Start");
				}
			}

			if (_presenceChanged) {
				if (_currentPresence) {
					_txtCurrentPresence.setText(String.format("at %s (%s m)", _currentLocation, _currentAdditionalInfo));
					_txtCurrentPresence.setTextColor(0xFF339933);
				} else {
					_txtCurrentPresence.setText("Not present");
					_txtCurrentPresence.setTextColor(Color.RED);
				}
			}

			_uiHandler.postDelayed(_uiUpdateRunnable, Config.UI_UPDATE_INTERVAL);
		}
	};
}