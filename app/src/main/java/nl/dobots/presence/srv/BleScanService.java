package nl.dobots.presence.srv;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;

import nl.dobots.bluenet.BleDeviceFilter;
import nl.dobots.bluenet.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.extended.BleExt;
import nl.dobots.bluenet.extended.BleExtTypes;
import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;

import nl.dobots.presence.srv.EventListener.Event;

public class BleScanService extends Service {

	private static final String TAG = BleScanService.class.getCanonicalName();

	public static final String EXTRA_SCAN_INTERVAL = "nl.dobots.bluenet.SCAN_INTERVAL";
	public static final String EXTRA_SCAN_PAUSE = "nl.dobots.bluenet.SCAN_PAUSE";
	public static final String EXTRA_AUTO_START = "nl.dobots.bluenet.AUTO_START";

	public static final String BLE_SERVICE_CFG = "ble_service";
	public static final String SCANNING_STATE = "scanningState";

	private static final int DEFAULT_SCAN_INTERVAL = 500;
	private static final int DEFAULT_SCAN_PAUSE = 500;

	private static BleScanService INSTANCE;

	public class BleScanBinder extends Binder {
		public BleScanService getService() {
			return INSTANCE;
		}
	}

	private final IBinder _binder = new BleScanBinder();

	private ArrayList<ScanDeviceListener> _scanDeviceListeners = new ArrayList<>();
	private ArrayList<IntervalScanListener> _intervalScanListeners = new ArrayList<>();
	private ArrayList<EventListener> _eventListeners = new ArrayList<>();

	private BleExt _ble;

	private Handler _intervalScanHandler = null;

	private int _scanPause = DEFAULT_SCAN_PAUSE;
	private int _scanInterval = DEFAULT_SCAN_INTERVAL;
	private boolean _scanning = false;
	private boolean _initialized = false;

	@Override
	public void onCreate() {
		super.onCreate();

		INSTANCE = this;

		_ble = new BleExt();
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

		boolean autoStart = false;

		// get the parameters from the intent
		if (intent != null) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				_scanInterval = bundle.getInt(EXTRA_SCAN_INTERVAL, DEFAULT_SCAN_INTERVAL);
				_scanPause = bundle.getInt(EXTRA_SCAN_PAUSE, DEFAULT_SCAN_PAUSE);
				autoStart = bundle.getBoolean(EXTRA_AUTO_START, false);
			}
		}

		// if intent had the auto start set, or if last scanning state was true, start interval scan
		if (autoStart || getScanningState()) {
			startIntervalScan();
		}

		// sticky makes the service restart if gets killed (by the user or by android due to
		// low memory.
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (_scanning) {
			_ble.stopScan(null); // don' t care if it worked or not, so don' t need a callback
		}

		// Remove all callbacks and messages that were posted
		_intervalScanHandler.removeCallbacksAndMessages(null);
	}

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

			onEvent(Event.BLUETOOTH_INITIALIZED);
		}

		@Override
		public void onError(int error) {
			// will (also) be called whenever bluetooth is disabled

			switch (error) {
				case BleExtTypes.ERROR_BLUETOOTH_TURNED_OFF: {
					Log.e(TAG, "Bluetooth turned off!!");

					// if bluetooth was turned off and scanning is enabled, issue a notification that present
					// detection won't work without BLE ...
					if (_scanning) {
						onEvent(Event.BLUETOOTH_TURNED_OFF);
					}
					break;
				}
				default:
					Log.e(TAG, "Ble Error: " + error);
			}
			_initialized = false;
		}
	};

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

		for (ScanDeviceListener listener : _scanDeviceListeners) {
			listener.onDeviceScanned(device);
		}
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
			setScanningState(true);
			_intervalScanHandler.post(_startScanRunnable);
		}
	}

	public void stopIntervalScan() {
		if (_scanning) {
			_intervalScanHandler.removeCallbacksAndMessages(null);
			_scanning = false;
			setScanningState(false);
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

	public void registerScanDeviceListener(ScanDeviceListener listener) {
		if (!_scanDeviceListeners.contains(listener)) {
			_scanDeviceListeners.add(listener);
		}
	}

	public void unregisterScanDeviceListener(ScanDeviceListener listener) {
		if (_scanDeviceListeners.contains(listener)) {
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
		if (!_intervalScanListeners.contains(listener)) {
			_intervalScanListeners.add(listener);
		}
	}

	public void unregisterIntervalScanListener(IntervalScanListener listener) {
		if (_intervalScanListeners.contains(listener)) {
			_intervalScanListeners.remove(listener);
		}
	}

	public void registerEventListener(EventListener listener) {
		if (!_eventListeners.contains(listener)) {
			_eventListeners.add(listener);
		}
	}

	public void unregisterEventListener(EventListener listener) {
		if (_eventListeners.contains(listener)) {
			_eventListeners.remove(listener);
		}
	}

	private void onEvent(Event event) {
		for (EventListener listener : _eventListeners) {
			listener.onEvent(event);
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

	private boolean getScanningState() {
		SharedPreferences sharedPreferences = getSharedPreferences(BLE_SERVICE_CFG, 0);
		return sharedPreferences.getBoolean(SCANNING_STATE, true);
	}

	private void setScanningState(boolean scanning) {
		SharedPreferences sharedPreferences = getSharedPreferences(BLE_SERVICE_CFG, 0);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(SCANNING_STATE, scanning);
		editor.commit();
	}

//	public void updateScanParams() {
//		if (isScanning()) {
//			stopIntervalScan();
//			startIntervalScan();
//		}
//	}

}
