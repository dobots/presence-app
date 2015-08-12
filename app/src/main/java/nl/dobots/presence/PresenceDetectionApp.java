package nl.dobots.presence;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;

import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.ask.AskWrapper;
import nl.dobots.presence.cfg.Config;
import nl.dobots.presence.cfg.Settings;
import nl.dobots.presence.gui.MainActivity;
import nl.dobots.presence.localization.Localization;
import nl.dobots.presence.localization.SimpleLocalization;
import nl.dobots.presence.locations.Location;
import nl.dobots.presence.locations.LocationsList;
import nl.dobots.presence.srv.BleScanService;
import nl.dobots.presence.srv.IntervalScanListener;
import retrofit.RetrofitError;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 5-8-15
 *
 * @author Dominik Egger
 */
public class PresenceDetectionApp extends Application implements IntervalScanListener {

	private static final String TAG = PresenceDetectionApp.class.getCanonicalName();

	private static PresenceDetectionApp instance = null;

	private Settings _settings;
	private AskWrapper _ask;

	private NotificationManager notificationManager;
	private NotificationCompat.Builder _builder;

	private Boolean _currentPresence = null;
	private String _currentLocation = "";
	private String _currentAdditionalInfo;

	private long _lastPresenceUpdateTime = 0;
//	private long _lastDetectionTime = 0;
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
	private Localization _localization;

	private boolean _detectionPaused = false;

//	private Handler _networkHandler;

	private Date _manualExpirationDate;
	private boolean _scanningBeforeManual = false;
	private boolean _scanning;

	private Handler _networkHandler;

	private Handler _watchdogHandler;
	private Runnable _watchdogRunner = new Runnable() {
		@Override
		public void run() {
			if (System.currentTimeMillis() - _localization.getLastDetectionTime() > Config.PRESENCE_TIMEOUT) {
				Log.i(TAG, String.format("Watchdog timeout. No beacon seen within %d seconds. Changing state to not present.", Config.PRESENCE_TIMEOUT));
				updatePresence(false, "", "");
			} else {
				Log.i(TAG, "watchdog ok.");
			}
			_watchdogHandler.postDelayed(_watchdogRunner, Config.WATCHDOG_INTERVAL);
		}
	};

	private BroadcastReceiver _receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				if (!intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
					if (_updateWaiting) {
						_updateWaiting = false;
						_watchdogHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								updatePresence(_updateWaitingPresence, _updateWaitingLocation, _updateWaitingAdditionalInfo);
							}
						}, 500);
					}
				}
			}
		}
	};

//	private BroadcastReceiver _receiver = new BroadcastReceiver() {
//		@Override
//		public void onReceive(Context context, Intent intent) {
//			if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
//				if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED) {
//					if (_updateWaiting) {
//						_updateWaiting = false;
//						_watchdogHandler.postDelayed(new Runnable() {
//							@Override
//							public void run() {
//								updatePresence(_updateWaitingPresence, _updateWaitingLocation, _updateWaitingAdditionalInfo);
//							}
//						}, 500);
//					}
//				}
//			}
//		}
//	};

	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "connected to ble scan service ...");
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();
//			_service.registerScanDeviceListener(PresenceDetectionApp.this);
			_service.registerIntervalScanListener(PresenceDetectionApp.this);

			// check if login information is present, otherwise ..
			if (_ask.isLoginCredentialsValid(_settings.getUsername(), _settings.getPassword()) &&
				!_settings.getLocationsList().isEmpty()) {
				// if login credentials are ok and locations are added, start scanning directly
				_service.startIntervalScan();
			} else {
				pauseDetection();
			}
			_scanning = _service.isScanning();

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

		_localization = SimpleLocalization.getInstance();

		_ask = AskWrapper.getInstance();

		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		HandlerThread watchdogThread = new HandlerThread("Watchdog");
		watchdogThread.start();
		_watchdogHandler = new Handler(watchdogThread.getLooper());
		_watchdogHandler.postDelayed(_watchdogRunner, Config.WATCHDOG_INTERVAL);

		HandlerThread networkThread = new HandlerThread("NetworkHandler");
		networkThread.start();
		_networkHandler = new Handler(networkThread.getLooper());

		IntentFilter filter = new IntentFilter();
//        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(_receiver, filter);

		Intent startServiceIntent = new Intent(this, BleScanService.class);
		this.startService(startServiceIntent);

		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);

//		if (!_ask.isLoggedIn()) {
//			if (_ask.login(_settings.getUsername(), _settings.getPassword(), _settings.getServer())) {
//				_ask.getCurrentPresence(_currentPresence, _currentLocation);
//				if (_currentPresence != null) {
//					notifyPresenceUpdate(_currentPresence, "", "");
//				}
//			}
//		}

//		BleDevice.setExpirationTime(1500);
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
		if (!_detectionPaused) {
			_detectionPaused = true;
			if (_bound) {
				_scanning = _service.isScanning();
				_service.stopIntervalScan();
			}
			Log.i(TAG, "stop watchdog");
			_watchdogHandler.removeCallbacks(_watchdogRunner);
		}
	}

	public void resumeDetection() {
		if (_detectionPaused) {
			_detectionPaused = false;
			if (_bound && _scanning) {
				_service.stopIntervalScan();
			}
			Log.i(TAG, "resume watchdog");
			_watchdogHandler.postDelayed(_watchdogRunner, Config.WATCHDOG_INTERVAL);
		}
	}

	@Override
	public void onScanStart() {
		// don't care
	}

	@Override
	public void onScanEnd() {
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
				System.currentTimeMillis() - _lastPresenceUpdateTime > Config.PRESENCE_UPDATE_TIMEOUT)
		{
			SimpleLocalization.LocalizationResult localizationResult = SimpleLocalization.getInstance().findLocation(devices);

			if (localizationResult != null) {
				Location location = localizationResult.location;
				BleDevice device = localizationResult.triggerDevice;
				updatePresence(true, location.getName(),
						String.format("%s at %.2f", device.getName(), device.getDistance()));
			}
		}
	}

//	@Override
//	public void onDeviceScanned(BleDevice dev) {
//		if (_detectionPaused) return;
//
//		// refresh device, triggers calculation of average rssi and distance
////		device.refresh();
//
//		BleDeviceMap deviceMap = _service.getDeviceMap();
//		deviceMap.refresh();
//		final ArrayList<BleDevice> devices = deviceMap.getDistanceSortedList();
//
//		if (!devices.isEmpty() &&
//			// as long as DETECTION_TIMEOUT is bigger than PRESENCE_UPDATE_TIMEOUT
//			// otherwise we have to move it inside the find location
//			!_updatingPresence &&
//			System.currentTimeMillis() - _lastPresenceUpdateTime > Config.PRESENCE_UPDATE_TIMEOUT)
//		{
//			SimpleLocalization.LocalizationResult localizationResult = SimpleLocalization.getInstance().findLocation(devices);
//
//			if (localizationResult != null) {
//				Location location = localizationResult.location;
//				BleDevice device = localizationResult.triggerDevice;
//				updatePresence(true, location.getName(),
//						String.format("%s at %.2f", device.getName(), device.getDistance()));
//			}
//		}
//	}


	public Date getManualExpirationDate() {
		return _manualExpirationDate;
	}

	private Runnable _onManualPresenceExpired = new Runnable() {
		@Override
		public void run() {
			setAutoPresence();
		}
	};

	public void setManualPresence(boolean present, long expirationTime) {
		_manualExpirationDate = new Date(new Date().getTime() + expirationTime);
		pauseDetection();
		if (_bound) {
			_scanningBeforeManual = _service.isScanning();
			_service.stopIntervalScan();
		}
		_watchdogHandler.postDelayed(_onManualPresenceExpired, expirationTime);
		updatePresence(present, "Manual", "");
	}

	public void setAutoPresence() {
		_manualExpirationDate = null;
		_watchdogHandler.removeCallbacks(_onManualPresenceExpired);
		resumeDetection();
		if (_bound) {
			if (_scanningBeforeManual) {
				_service.startIntervalScan();
			}
		}
	}

	private void onNetworkError(String error, boolean present, String location, String additionalInfo) {

		Intent contentIntent = new Intent(this, MainActivity.class);
		contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent piContent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Intent wifiSettingsIntent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
//        presentIntent.putExtra("nl.dobots.presence.NOTIFICATION_ANSWER", true);
		PendingIntent piWifiSettings = PendingIntent.getActivity(this, 0, wifiSettingsIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		_builder = new NotificationCompat.Builder(this)
				.setSmallIcon(R.mipmap.ic_launcher)
				.setContentTitle("Network Error")
				.setContentText(error)
				.addAction(android.R.drawable.ic_menu_manage, "Wifi Settings", piWifiSettings)
				.setContentIntent(piContent)
				.setDefaults(Notification.DEFAULT_SOUND)
				.setLights(Color.BLUE, 500, 1000);
		notificationManager.notify(Config.PRESENCE_NOTIFICATION_ID, _builder.build());
		Toast.makeText(this, error, Toast.LENGTH_LONG).show();

		_ask.setLoggedIn(false);

		_updateWaiting = true;
		_updateWaitingPresence = present;
		_updateWaitingLocation = location;
		_updateWaitingAdditionalInfo = additionalInfo;

		// just to make sure we don't get stuck
		_updatingPresence = false;
	}

	private void updatePresence(final boolean present, final String location, final String additionalInfo) {

		//we only send the new presence if it differs from the old one, to avoid surcharging the server
		if ((_currentPresence == null) || (_currentPresence != present) || !_currentLocation.matches(location)) {

			// can't execute network operations in the main thread, so we have to delegate
			// the call to the network handler
			if (Looper.myLooper() == Looper.getMainLooper()) {
				_networkHandler.post(new Runnable() {
					@Override
					public void run() {
						updatePresence(present, location, additionalInfo);
					}
				});
				return;
			}

			_updatingPresence = true;

//			// can't execute network operations in the main thread, so we have to delegate
//			// the call to the network handler
//			if (Looper.myLooper() == Looper.getMainLooper()) {
//				_networkHandler.post(new Runnable() {
//					@Override
//					public void run() {
//						updatePresence(present, location, additionalInfo);
//					}
//				});
//				return;
//			}

			try {
				// check if we are logged in, and do so otherwise
				if (!_ask.isLoggedIn()) {
					_ask.login(_settings.getUsername(), _settings.getPassword(), _settings.getServer(),
							new AskWrapper.StatusCallback() {
								@Override
								public void onSuccess() {
									updatePresence(present, location, additionalInfo);
								}

								@Override
								public void onError() {
									Log.e(TAG, "failed to log in");

									onNetworkError("Can't login, please check your internet!",
											present, location, additionalInfo);
								}
							}
					);
					return;

//					// if login failed, give notification
//					if (!_ask.isLoggedIn()) {
//						Log.e(TAG, "failed to log in");
//
//						onNetworkError("Can't login, please check your internet!",
//								present, location, additionalInfo);
//						return;
//					}
				}
				// make sure we are now logged in
				Log.i(TAG, "Update presence to: " + present + " at " + location);

				_ask.updatePresence(present, location,
						new AskWrapper.StatusCallback() {
							@Override
							public void onSuccess() {
								_currentLocation = location;
								_currentPresence = present;
								_currentAdditionalInfo = additionalInfo;

								notifyPresenceUpdate(present, location, additionalInfo);
								notificationManager.cancel(Config.PRESENCE_NOTIFICATION_ID);
							}

							@Override
							public void onError() {
								Log.e(TAG, "failed to update presence");
								onNetworkError("Failed to update presence, please check your internet!",
										present, location, additionalInfo);
							}
						}
				);

				_updatingPresence = false;
				_lastPresenceUpdateTime = System.currentTimeMillis();
				_retry = false;
			} catch (RetrofitError e) {
				e.printStackTrace();
				_ask.setLoggedIn(false);
				if (!_retry) {
					updatePresence(present, location, additionalInfo);
					_retry = true;
				} else {
					_updatingPresence = false;
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

	public Boolean getCurrentPresence() {
//		if (_currentPresence == null) return false;
		return _currentPresence;
	}

	public String getCurrentLocation() {
		return _currentLocation;
	}

	public String getCurrentAdditionalInfo() {
		return _currentAdditionalInfo;
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
