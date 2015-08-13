package nl.dobots.presence.srv;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;

import nl.dobots.bluenet.BleDeviceFilter;
import nl.dobots.bluenet.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.extended.BleExt;
import nl.dobots.bluenet.extended.BleExtTypes;
import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.R;
import nl.dobots.presence.cfg.Config;
import nl.dobots.presence.gui.MainActivity;

public class BleScanService extends Service {

	private static final String TAG = BleScanService.class.getCanonicalName();

	private static final String EXTRA_SCAN_INTERVAL = "nl.dobots.presence.LOW_SCAN_INTERVAL";
	private static final String EXTRA_SCAN_PAUSE = "nl.dobots.presence.LOW_SCAN_PAUSE";

	private static BleScanService INSTANCE;

	private ArrayList<ScanDeviceListener> _scanDeviceListeners = new ArrayList<>();
	private ArrayList<IntervalScanListener> _intervalScanListeners = new ArrayList<>();

	private NotificationManager _notificationManager;

	public class BleScanBinder extends Binder {

		public BleScanService getService() {
			return INSTANCE;
		}
	}

	private final IBinder _binder = new BleScanBinder();
	private BleExt _ble;

	private Handler _intervalScanHandler = null;

	private boolean _scanning = false;
	private boolean _stopped = false;

	private int _scanPause = Config.LOW_SCAN_PAUSE;
	private int _scanInterval = Config.LOW_SCAN_INTERVAL;

	public BleScanService() {
		Log.d(TAG, "constructor");
	}

	private boolean _initialized = false;
	private IStatusCallback _connectionCallback = new IStatusCallback() {
		@Override
		public void onSuccess() {
			// will be called whenever bluetooth is enabled

			Log.d(TAG, "successfully initialized BLE");
			_initialized = true;

			// if scanning enabled, resume scanning
			if (_scanning) {
				_intervalScanHandler.removeCallbacksAndMessages(null);
				_intervalScanHandler.post(_startScanRunnable);
			}

			// if BLE init succeeded clear the notification again
			_notificationManager.cancel(Config.PRESENCE_NOTIFICATION_ID);
		}

		@Override
		public void onError(int error) {
			// will (also) be called whenever bluetooth is disabled

			Log.e(TAG, "Ble Error: " + error);
			_initialized = false;

			// if bluetooth was turned off, issue a notification that present detection won't work
			// without BLE ...
			if (error == BleExtTypes.ERROR_BLUETOOTH_TURNED_OFF) {

				Intent contentIntent = new Intent(BleScanService.this, MainActivity.class);
				PendingIntent piContent = PendingIntent.getActivity(BleScanService.this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				Intent btEnableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				PendingIntent piBtEnable = PendingIntent.getActivity(BleScanService.this, 0, btEnableIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				NotificationCompat.Builder builder = new NotificationCompat.Builder(BleScanService.this)
						.setSmallIcon(R.mipmap.ic_launcher)
						.setContentTitle("Presence Detection Error")
						.setContentText("Can't detect presence without BLE!")
						.addAction(android.R.drawable.ic_menu_manage, "Enable Bluetooth", piBtEnable)
						.setContentIntent(piContent)
						.setDefaults(Notification.DEFAULT_SOUND)
						.setLights(Color.BLUE, 500, 1000);
				_notificationManager.notify(Config.PRESENCE_NOTIFICATION_ID, builder.build());
			}
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();

		INSTANCE = this;

		_notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		_ble = new BleExt();
		_ble.init(this, _connectionCallback);
		_ble.setScanFilter(BleDeviceFilter.doBeacon);

		HandlerThread handlerThread = new HandlerThread("BleScanService");
		handlerThread.start();
		_intervalScanHandler = new Handler(handlerThread.getLooper());

	}

	@Override
	public IBinder onBind(Intent intent) {
		return _binder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		// get the parameters from the intent
		if (intent != null) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				_scanInterval = bundle.getInt(EXTRA_SCAN_INTERVAL, Config.LOW_SCAN_INTERVAL);
				_scanPause = bundle.getInt(EXTRA_SCAN_PAUSE, Config.LOW_SCAN_PAUSE);
			}
		}

//		startIntervalScan();

		// The service will at this point continue running until Context.stopService() or stopSelf() is called
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (_scanning) {
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
		}

		// Remove all callbacks and messages that were posted
		_intervalScanHandler.removeCallbacksAndMessages(null);

		_stopped = true;
	}

	private Runnable _startScanRunnable = new Runnable() {
		@Override
		public void run() {
			Log.d(TAG, "Start endless scan");
			_ble.startIntervalScan(new IBleDeviceCallback() {

				@Override
				public void onSuccess(BleDevice device) {
					onDeviceScanned(device);
				}

				@Override
				public void onError(int error) {
					Log.e(TAG, "start scan error: " + error);
				}
			});
			onIntervalScanStart();
			_intervalScanHandler.postDelayed(_stopScanRunnable, _scanInterval);
		}
	};

	private void onDeviceScanned(BleDevice device) {
		Log.d(TAG, "scanned device: " + device.getName() + " " + device.getRssi());
		notifyScanDeviceListeners(device);
	}

	private Runnable _stopScanRunnable = new Runnable() {
		@Override
		public void run() {
			Log.d(TAG, "Stop endless scan");
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {
					onIntervalScanEnd();
					_intervalScanHandler.postDelayed(_startScanRunnable, _scanPause);
				}

				@Override
				public void onError(int error) {
					_intervalScanHandler.postDelayed(_startScanRunnable, _scanPause);
				}
			});
		}
	};


	public void startIntervalScan(int scanInterval, int scanPause) {
		this._scanInterval = scanInterval;
		this._scanPause = scanPause;
		startIntervalScan();
	}

	public void startIntervalScan() {
		if (!_initialized) {
			_ble.init(this, _connectionCallback);
		}
		if (!_scanning) {
			Log.i(TAG, "Starting interval scan");
			_scanning = true;
			_intervalScanHandler.post(_startScanRunnable);
		}
	}

	public void stopIntervalScan() {
		if (_scanning) {
			_intervalScanHandler.removeCallbacksAndMessages(null);
			_scanning = false;
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {
					Log.i(TAG, "Stopped interval scan");
				}

				@Override
				public void onError(int error) {
					Log.e(TAG, "Failed to stop interval scan: " + error);
				}
			});
		}
	}

	public boolean isScanning() {
		return _scanning;
	}

	private void notifyScanDeviceListeners(BleDevice device) {
		for (ScanDeviceListener listener : _scanDeviceListeners) {
			listener.onDeviceScanned(device);
		}
	}

	public void registerScanDeviceListener(ScanDeviceListener listener) {
		if (_scanDeviceListeners.indexOf(listener) == -1) {
			_scanDeviceListeners.add(listener);
		}
	}

	public void unregisterScanDeviceListener(ScanDeviceListener listener) {
		if (_scanDeviceListeners.indexOf(listener) != -1) {
			_scanDeviceListeners.remove(listener);
		}
	}

	private void onIntervalScanStart() {
		for (IntervalScanListener listener : _intervalScanListeners) {
			listener.onScanStart();
		}
	}

	private void onIntervalScanEnd() {
		for (IntervalScanListener listener : _intervalScanListeners) {
			listener.onScanEnd();
		}
	}

	public void registerIntervalScanListener(IntervalScanListener listener) {
		if (_intervalScanListeners.indexOf(listener) == -1) {
			_intervalScanListeners.add(listener);
		}
	}

	public void unregisterIntervalScanListener(IntervalScanListener listener) {
		if (_intervalScanListeners.indexOf(listener) != -1) {
			_intervalScanListeners.remove(listener);
		}
	}

	public int getScanPause() {
		return _scanPause;
	}

	public void setScanPause(int scanPause) {
		_scanPause = scanPause;
		if (isScanning()) {
			stopIntervalScan();
			startIntervalScan();
		}
	}

	public int getScanInterval() {
		return _scanInterval;
	}

	public void setScanInterval(int scanInterval) {
		_scanInterval = scanInterval;
		if (isScanning()) {
			stopIntervalScan();
			startIntervalScan();
		}
	}

	public BleDeviceMap getDeviceMap() {
		return _ble.getDeviceMap();
	}

//	public void updateScanParams() {
//		if (isScanning()) {
//			stopIntervalScan();
//			startIntervalScan();
//		}
//	}



}
