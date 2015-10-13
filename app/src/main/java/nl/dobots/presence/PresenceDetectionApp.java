package nl.dobots.presence;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
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
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.presence.ask.AskWrapper;
import nl.dobots.presence.cfg.Config;
import nl.dobots.presence.cfg.Settings;
import nl.dobots.presence.gui.MainActivity;
import nl.dobots.bluenet.localization.Localization;
import nl.dobots.bluenet.localization.SimpleLocalization;
import nl.dobots.bluenet.localization.locations.Location;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.service.callbacks.EventListener;
import nl.dobots.bluenet.service.callbacks.IntervalScanListener;

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
public class PresenceDetectionApp extends Application implements IntervalScanListener, EventListener {

	private static final String TAG = PresenceDetectionApp.class.getCanonicalName();

	private static PresenceDetectionApp instance = null;

	public static PresenceDetectionApp getInstance() {
		return instance;
	}

	private Settings _settings;
	private AskWrapper _ask;

	private NotificationManager _notificationManager;

	private Boolean _currentPresence = null;
	private String _currentLocation = "";
	private String _currentAdditionalInfo;

	private boolean _retry = false;
	private boolean _updatingPresence;

	private boolean _updateWaiting;
	private boolean _updateWaitingPresence;
	private String _updateWaitingLocation;
	private String _updateWaitingAdditionalInfo;

	private BleScanService _service;
	private boolean _bound;

//	private boolean _highFrequencyDetection;

	private Localization _localization;

	private boolean _detectionPaused = false;
	private boolean _scanning;

	// the time at which the manual override will expire and the detection will change to auto
	private Date _manualExpirationDate;

	private ArrayList<PresenceUpdateListener> _listenerList = new ArrayList<>();

	private boolean _networkErrorActive;

	private Handler _networkHandler;

	private Handler _watchdogHandler;
	private Runnable _watchdogRunner = new Runnable() {
		@Override
		public void run() {
			if (System.currentTimeMillis() - _localization.getLastDetectionTime() > Config.PRESENCE_TIMEOUT) {
				Log.i(TAG, String.format("Watchdog timeout. No beacon seen within %d seconds. Changing state to not present.", Config.PRESENCE_TIMEOUT));

				_networkHandler.post(new Runnable() {
					@Override
					public void run() {
						updatePresence(false, "", "");
					}
				});

				// todo: should we go into low frequency scanning, e.g. 2 sec per 5 min or so until at least one beacon is seen again?
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
					// connection reestablished, clear network error
					_networkErrorActive = false;

					Log.d(TAG, "Intent: " + intent.toString());
					Log.d(TAG, "Extras: " + intent.getExtras().toString());

					if (_updateWaiting) {
						Log.i(TAG, "update waiting ...");
						// if update is waiting, trigger presence update again ...
						_networkHandler.post(new Runnable() {
							@Override
							public void run() {
								updatePresence(_updateWaitingPresence, _updateWaitingLocation, _updateWaitingAdditionalInfo);
							}
						});
					} else {
						// ... otherwise, request current presence in case it changed, or if we never
						// new it in the first place
						if (_currentPresence == null) {
							Log.i(TAG, "no update, and presence unknown ...");
							_networkHandler.post(new Runnable() {
								@Override
								public void run() {
									requestCurrentPresence();
								}
							});
						}
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
			_service.registerIntervalScanListener(PresenceDetectionApp.this);
			_service.registerEventListener(PresenceDetectionApp.this);

			_service.setScanInterval(Config.LOW_SCAN_INTERVAL);
			_service.setScanPause(Config.LOW_SCAN_PAUSE);

			// check if login information is present, otherwise ..
			if (_ask.isLoginCredentialsValid(_settings.getUsername(), _settings.getPassword()) &&
				!_settings.getLocationsList().isEmpty()) {
				// if login credentials are ok and locations are configured, start detection directly ..
//				_service.startIntervalScan();
			} else {
				// .. otherwise, pause detection until both requirements are met
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

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;

		// load settings from persistent storage
		_settings = Settings.getInstance();
		_settings.readPersistentStorage(getApplicationContext());

		_settings.readPersistentLocations(getApplicationContext());

		// get localization algo
		_localization = new SimpleLocalization(_settings.getLocationsList(), _settings.getDetectionDistance());

		// get ask wrapper (wraps login and presence functions)
		_ask = AskWrapper.getInstance();

		_notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		// watchdog handler checks for non-presence and handles manual override
		HandlerThread watchdogThread = new HandlerThread("Watchdog");
		watchdogThread.start();
		_watchdogHandler = new Handler(watchdogThread.getLooper());
		_watchdogHandler.postDelayed(_watchdogRunner, Config.WATCHDOG_INTERVAL);

		// network handler used for network operations (login, updatePresence, etc.)
		HandlerThread networkThread = new HandlerThread("NetworkHandler");
		networkThread.start();
		_networkHandler = new Handler(networkThread.getLooper());

		// filter for connectivity broadcasts
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(_receiver, filter);

//		Intent startServiceIntent = new Intent(this, BleScanService.class);
//		this.startService(startServiceIntent);

		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);

		// set expiration time for RSSI measurements. keeps measurements of 5 scan intervals
		// before throwing them out. i.e. averages over all measurements received in the last 5
		// scan intervals
		BleDevice.setExpirationTime(5 * (Config.LOW_SCAN_PAUSE + Config.LOW_SCAN_INTERVAL));

	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		if (_bound) {
			unbindService(_connection);
		}
		_settings.onDestroy();
	}

	private void requestCurrentPresence() {

		AskWrapper.PresenceCallback callback = new AskWrapper.PresenceCallback() {
			@Override
			public void onSuccess(boolean present, String location) {
				// let current values unassigned so that it will be populated the first time
				// that a device / location is found
//				_currentPresence = present;
//				_currentLocation = location;
				// only inform listener (e.g. to update the UI)
				Log.i(TAG, "Remote presence: " + present + " at " + location);
				notifyPresenceUpdate(present, location, "");
			}

			@Override
			public void onError(String errorMessage) {
				Log.e(TAG, String.format("could not get presence. Error: %s", errorMessage));
			}
		};

		if (!_ask.isLoggedIn()) {
			_ask.login(_settings.getUsername(), _settings.getPassword(), _settings.getServer(), callback);
		} else {
			_ask.getCurrentPresence(callback);
		}
	}

	public void pauseDetection() {
		if (!_detectionPaused) {
			_detectionPaused = true;
			if (_bound) {
				// if connected to service, store current scanning state
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
				// if connected to service and last state was scanning, resume scan
				_service.startIntervalScan();
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

		final ArrayList<BleDevice> devices = _service.getDeviceMap().getDistanceSortedList();

		Log.d(TAG, "search locations");
		if (!_updatingPresence && !devices.isEmpty()) {
			SimpleLocalization.LocalizationResult localizationResult = _localization.findLocation(devices);

			if (localizationResult != null) {
				final Location location = localizationResult.location;
				final BleDevice device = localizationResult.triggerDevice;
				Log.d(TAG, "post presence update");
				_networkHandler.post(new Runnable() {
					@Override
					public void run() {
						updatePresence(true, location.getName(),
								String.format("%s at %.2f", device.getName(), device.getDistance()));
					}
				});
			} else {
				Log.d(TAG, "no location found");
			}
		}
	}

	public Date getManualExpirationDate() {
		return _manualExpirationDate;
	}

	private Runnable _onManualPresenceExpired = new Runnable() {
		@Override
		public void run() {
			setAutoPresence();
		}
	};

	public void setManualPresence(final boolean present, long expirationTime) {
		_manualExpirationDate = new Date(new Date().getTime() + expirationTime);
		pauseDetection();
		_watchdogHandler.postDelayed(_onManualPresenceExpired, expirationTime);
		_networkHandler.post(new Runnable() {
			@Override
			public void run() {
				updatePresence(present, "Manual", "");
			}
		});
	}

	public void setAutoPresence() {
		_manualExpirationDate = null;
		_watchdogHandler.removeCallbacks(_onManualPresenceExpired);
		// force update
		_updatingPresence = false;
		resumeDetection();
	}

	private void onNetworkError(String error, boolean present, String location, String additionalInfo) {

		// only trigger notification once as long as the network error is active.
		if (!_networkErrorActive) {
			_networkErrorActive = true;

			if (_settings.isNotificationsEnabled()) {
				Intent contentIntent = new Intent(this, MainActivity.class);
				contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				PendingIntent piContent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				Intent wifiSettingsIntent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
				PendingIntent piWifiSettings = PendingIntent.getActivity(this, 0, wifiSettingsIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
						.setSmallIcon(R.mipmap.ic_launcher)
						.setContentTitle("Network Error")
						.setContentText(error)
						.setStyle(new NotificationCompat.BigTextStyle().bigText(error))
						.addAction(android.R.drawable.ic_menu_manage, "Wifi Settings", piWifiSettings)
						.setContentIntent(piContent)
						.setDefaults(Notification.DEFAULT_SOUND)
						.setLights(Color.BLUE, 500, 1000);
				_notificationManager.notify(Config.PRESENCE_NOTIFICATION_ID, builder.build());
				Toast.makeText(this, error, Toast.LENGTH_LONG).show();
			}
		}

		// set logged in as false (because of network error)
		_ask.setLoggedIn(false);

		// store the presence update, will be triggered once network connection is reestablished
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

			// check if we are logged in, and do so otherwise
			if (!_ask.isLoggedIn()) {
				_ask.login(_settings.getUsername(), _settings.getPassword(), _settings.getServer(),
					new AskWrapper.PresenceCallback() {
						@Override
						public void onSuccess(boolean remotePresent, String remoteLocation) {
							// login was successful, call update presence again
							updatePresence(present, location, additionalInfo);

							// just to be sure, it should already be set to false when
							// connection is reestablished, but just in case we missed that
							// broadcast, if login succeeded, we can set it to false for sure
							_networkErrorActive = false;
						}

						@Override
						public void onError(String errorMessage) {
							Log.e(TAG, "failed to log in");

							onNetworkError(String.format("Can't login, please check your internet!\n\n" +
											"Error: %s", errorMessage),
									present, location, additionalInfo);
						}
					}
				);
				return;
			}

			Log.i(TAG, "Update presence to: " + present + " at " + location);

			_ask.updatePresence(present, location, new AskWrapper.StatusCallback() {
					@Override
					public void onSuccess() {
						// set current presence values
						_currentLocation = location;
						_currentPresence = present;
						_currentAdditionalInfo = additionalInfo;

						// notify anybody listening for presence updates
						notifyPresenceUpdate(present, location, additionalInfo);

						// cancel any outstanding notification (network error or BT error)
						_notificationManager.cancel(Config.PRESENCE_NOTIFICATION_ID);

						// clear flags
						_updatingPresence = false;
						_updateWaiting = false;
						_retry = false;

						// just to be sure. it should already be set to false when
						// connection is reestablished, but just in case we missed that
						// broadcast, if updating the presence succeeded, we can set it
						// to false for sure
						_networkErrorActive = false;

					}

					@Override
					public void onError(String errorMessage) {
						Log.e(TAG, "failed to update presence");

						// most likely the presence update fails because the session expired
						// so we try again, but this time, we make sure that we log in again first
						// by setting logged in state to false
						_ask.setLoggedIn(false);

						if (!_retry) {

							// set retry flag ..
							_retry = true;

							// .. and try calling update presence again
							updatePresence(present, location, additionalInfo);

						} else {
							// if the second time it fails again after logging in, we abort

							// clear flags ..
							_updatingPresence = false;
							_retry = false;

							// .. inform user and store presence update ..
							onNetworkError(String.format("Failed to update presence, please check your internet!\n\n" +
											"Error: %s", errorMessage),
									present, location, additionalInfo);

							// .. and abort
						}
					}
				}
			);
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
		// should never be null, but just in case
		if (_currentPresence == null) return false;

		return _currentPresence;
	}

	public String getCurrentLocation() {
		return _currentLocation;
	}

	public String getCurrentAdditionalInfo() {
		return _currentAdditionalInfo;
	}

	@Override
	public void onEvent(EventListener.Event event) {
		switch (event) {
			case BLUETOOTH_INITIALIZED: {
				// if BLE init succeeded clear the notification again
				_notificationManager.cancel(Config.PRESENCE_NOTIFICATION_ID);
				break;
			}
			case BLUETOOTH_TURNED_OFF: {
				if (_settings.isNotificationsEnabled()) {
					Intent contentIntent = new Intent(this, MainActivity.class);
					PendingIntent piContent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

					Intent btEnableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					PendingIntent piBtEnable = PendingIntent.getActivity(this, 0, btEnableIntent, PendingIntent.FLAG_UPDATE_CURRENT);

					String errorMessage = "Can't detect presence without BLE!";

					NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
							.setSmallIcon(R.mipmap.ic_launcher)
							.setContentTitle("Presence Detection Error")
							.setContentText(errorMessage)
							.setStyle(new NotificationCompat.BigTextStyle().bigText(errorMessage))
							.addAction(android.R.drawable.ic_menu_manage, "Enable Bluetooth", piBtEnable)
							.setContentIntent(piContent)
							.setDefaults(Notification.DEFAULT_SOUND)
							.setLights(Color.BLUE, 500, 1000);
					_notificationManager.notify(Config.PRESENCE_NOTIFICATION_ID, builder.build());
				}
			}
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
