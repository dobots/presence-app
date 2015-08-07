package nl.dobots.presence;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.ask.AskWrapper;
import nl.dobots.presence.gui.MainActivity;
import nl.dobots.presence.locations.Location;
import nl.dobots.presence.locations.LocationsList;
import nl.dobots.presence.srv.BleScanService;
import retrofit.RetrofitError;

/**
 * Created by dominik on 5-8-15.
 */
public class PresenceDetectionApp extends Application implements ScanDeviceListener {

	private static final String TAG = PresenceDetectionApp.class.getCanonicalName();

	public static final int PRESENCE_NOTIFICATION_ID = 1010;

	// NOTE: PRESENCE_UPDATE_TIMEOUT has to be smaller than PRESENCE_TIMEOUT
	public static final long PRESENCE_UPDATE_TIMEOUT = 10 * 1000; // 30 seconds (in ms)
//	public static final long PRESENCE_TIMEOUT = 30 * (60 * 1000); // 30 minutes (in ms)
	public static final long PRESENCE_TIMEOUT = 1* (30 * 1000); // 30 minutes (in ms)

//	private static final long WATCHDOG_INTERVAL = 5 * (60 * 1000); // 5 minutes (in ms)
	private static final long WATCHDOG_INTERVAL = 1 * (30 * 1000); // 5 minutes (in ms)

	public static final int LOW_SCAN_PAUSE = 2500; // 2.5 seconds
	public static final int LOW_SCAN_PERIOD = 500; // 0.5 seconds
	public static final int LOW_SCAN_EXPIRATION = 3500; // 3.5 seconds

	public static final int HIGH_SCAN_PAUSE = 500; // 0.5 seconds
	public static final int HIGH_SCAN_PERIOD = 500; // 0.5 seconds
	public static final int HIGH_SCAN_EXPIRATION = 2000; // 2 seconds

	private static PresenceDetectionApp instance = null;

	private Settings _settings;
	private AskWrapper _ask;

	private NotificationManager notificationManager;
	private NotificationCompat.Builder _builder;

	private boolean _currentPresence = false;
	private String _currentLocation = "";
	private long _lastPresenceUpdateTime = 0;
	private long _lastDetectionTime = 0;
	private boolean _retry = false;
	private boolean _updatingPresence;

	private boolean _updateWaiting;
	private boolean _updateWaitingPresence;
	private String _updateWaitingLocation;
	private String _updateWaitingAdditionalInfo;

	private BleScanService _service;
	private boolean _bound;

	private boolean _highFrequencyDetection;

	private LocationsList _locationsList;

	private boolean _detectionPaused = false;

	private Handler _watchdogHandler;
	private Runnable _watchdogRunner = new Runnable() {
		@Override
		public void run() {
			if (System.currentTimeMillis() - _lastDetectionTime > PRESENCE_TIMEOUT) {
				updatePresence(false, "", "");
			}
			_watchdogHandler.postDelayed(_watchdogRunner, WATCHDOG_INTERVAL);
		}
	};

	private BroadcastReceiver _receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
				if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED) {
					if (_updateWaiting) {
						_updateWaiting = false;
						updatePresence(_updateWaitingPresence, _updateWaitingLocation, _updateWaitingAdditionalInfo);
					}
				}
			}
		}
	};

	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "connected to ble scan service ...");
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();
			_service.registerScanDeviceListener(PresenceDetectionApp.this);
			_bound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
			_bound = false;
		}
	};
	private ArrayList<PresenceUpdateListener> _listenerList = new ArrayList<>();

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;

		_settings = Settings.getInstance();
		_settings.readPersistentStorage(getApplicationContext());

		_settings.readPersistentLocations(getApplicationContext());
		_locationsList = _settings.getLocationsList();

		_ask = AskWrapper.getInstance();

		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		HandlerThread handlerThread = new HandlerThread("Watchdog");
		handlerThread.start();
		_watchdogHandler = new Handler(handlerThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(_receiver, filter);

		Intent startServiceIntent = new Intent(this, BleScanService.class);
		this.startService(startServiceIntent);

		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		if (_bound) {
			unbindService(_connection);
		}
		_settings.onDestroy();
	}

	public static PresenceDetectionApp getInstance() {
		return instance;
	}

	public void pauseDetection() {
		_detectionPaused = true;
		_watchdogHandler.removeCallbacksAndMessages(null);
	}

	public void resumeDetection() {
		_detectionPaused = false;
		_watchdogHandler.postDelayed(_watchdogRunner, WATCHDOG_INTERVAL);
	}

	@Override
	public void onDeviceScanned(BleDevice dev) {
		if (_detectionPaused) return;

		// refresh device, triggers calculation of average rssi and distance
//		device.refresh();

		BleDeviceMap deviceMap = _service.getDeviceMap();
		deviceMap.refresh();
		final ArrayList<BleDevice> devices = deviceMap.getDistanceSortedList();

		if (!devices.isEmpty() &&
			// as long as DETECTION_TIMEOUT is bigger than PRESENCE_UPDATE_TIMEOUT
			// otherwise we have to move it inside the find location
			!_updatingPresence &&
			System.currentTimeMillis() - _lastPresenceUpdateTime > PRESENCE_UPDATE_TIMEOUT)
		{
			Location location;
			// loop over all devices in the list and find ...
			for (BleDevice device : devices) {
				// ... the closest device registered with a location
				if ((location = _locationsList.findLocation(device.getAddress())) != null) {
//					if (!_updatingPresence && System.currentTimeMillis() - _lastPresenceUpdateTime > PRESENCE_UPDATE_TIMEOUT) {
						double distance = device.getDistance();
						if (distance != -1 && distance < _settings.getDetectionDistance()) {
							Log.i(TAG, "I am in range of: " + device.getName() + "at " + device.getDistance() + " m");
							updatePresence(true, location.getName(), String.format("%s at %.2f", device.getName(), distance));
						}
//					}
					_lastDetectionTime = System.currentTimeMillis();
					return;
				}
			}
		}

	}

	private BleDevice getClosestRegisteredDevice(ArrayList<BleDevice> devices) {
		for (BleDevice device : devices) {
			if (_locationsList.findLocation(device.getAddress()) != null) {
				return device;
			}
		}
		return null;
	}

	private void updatePresence(boolean present, String location, String additionalInfo) {

		//we only send the new presence if it differs from the old one, to avoid surcharging the server
		if ((_currentPresence != present) || !_currentLocation.matches(location)) {
			_updatingPresence = true;

			// can't execute network operations in the main thread, so we have to delegate
			// the call to the network handler
//		if (Looper.myLooper() == Looper.getMainLooper()) {
//			mNetworkHandler.post(new Runnable() {
//				@Override
//				public void run() {
//					updatePresence(present);
//				}
//			});
//			return;
//		}

			try {
				// check if we are logged in, and do so otherwise
				if (!_ask.isLoggedIn()) {
					_ask.login(_settings.getUsername(), _settings.getPassword(), _settings.getServer());

					// if login failed, give notification
					if (!_ask.isLoggedIn()) {
						Log.e(TAG, "failed to log in");

						_builder = new NotificationCompat.Builder(this)
								.setSmallIcon(R.mipmap.ic_launcher)
								.setContentTitle("Presence detected")
								.setContentText("Can't login, please check your internet!")
								.setDefaults(Notification.DEFAULT_SOUND)
								.setLights(Color.BLUE, 500, 1000);
						notificationManager.notify(PRESENCE_NOTIFICATION_ID, _builder.build());
						Toast.makeText(this, "Can't login, please check your internet!", Toast.LENGTH_LONG).show();

						_updateWaiting = true;
						_updateWaitingPresence = present;
						_updateWaitingLocation = location;

						return;
					}
				}
				// make sure we are now logged in
				if (_ask.isLoggedIn()) {
					Log.i(TAG, "Update presence to: " + present + " at " + location);
					_ask.updatePresence(present, location);
					_currentLocation = location;
					_currentPresence = present;

					notifyPresenceUpdate(present, location, additionalInfo);
				}
				_updatingPresence = false;
				_lastPresenceUpdateTime = System.currentTimeMillis();
				notificationManager.cancel(PRESENCE_NOTIFICATION_ID);
				_retry = false;
			} catch (RetrofitError e) {
				e.printStackTrace();
				_ask.setLoggedIn(false);
				if (!_retry) {
					updatePresence(present, location, additionalInfo);
					_retry = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void notifyPresenceUpdate(boolean present, String location, String additionalInfo) {
		for (PresenceUpdateListener listener : _listenerList) {
			listener.onPresenceUpdate(present, location, additionalInfo);
		}
	}

	public void registerPresenceUpdateListener(PresenceUpdateListener listener) {
		if (_listenerList.indexOf(listener) == -1) {
			_listenerList.add(listener);
		}
	}

	public void unregisterPresenceUpdateListener(PresenceUpdateListener listener) {
		if (_listenerList.indexOf(listener) != -1) {
			_listenerList.remove(listener);
		}
	}

//	public void setHighFrequencyDetection(boolean enable) {
//		if (_highFrequencyDetection == enable) return;
//
//		if (enable) {
//			Log.i(TAG, "set scan frequency to high");
//			_service.setScanInterval(HIGH_SCAN_PERIOD);
//			_service.setScanPause(HIGH_SCAN_PAUSE);
//			_service.updateScanParams();
//			BleDevice.setExpirationTime(HIGH_SCAN_EXPIRATION);
//		} else {
//			Log.i(TAG, "set scan frequency to low");
//			_service.setScanInterval(LOW_SCAN_PERIOD);
//			_service.setScanPause(LOW_SCAN_PAUSE);
//			_service.updateScanParams();
//			BleDevice.setExpirationTime(LOW_SCAN_EXPIRATION);
//		}
//		_highFrequencyDetection = enable;
//	}

}
