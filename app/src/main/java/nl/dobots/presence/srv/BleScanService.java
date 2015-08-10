package nl.dobots.presence.srv;

import android.app.Service;
import android.content.Intent;
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
import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.ScanDeviceListener;
import nl.dobots.presence.gui.MainActivity;

public class BleScanService extends Service {

	private static final String TAG = BleScanService.class.getCanonicalName();

	private static final String EXTRA_SCAN_INTERVAL = "nl.dobots.presence.SCAN_INTERVAL";
	private static final String EXTRA_SCAN_PAUSE = "nl.dobots.presence.SCAN_PAUSE";

	private static final int SCAN_INTERVAL = 2000;
//	private static final int SCAN_PAUSE = 2000;
	private static final int SCAN_PAUSE = 0;

	private static BleScanService INSTANCE;

	private ArrayList<ScanDeviceListener> _listenerList = new ArrayList<>();

	public class BleScanBinder extends Binder {

		public BleScanService getService() {
			return INSTANCE;
		}
	}

	private final IBinder _binder = new BleScanBinder();
	private BleExt _ble;

	private Handler _handler = null;

	private boolean _scanning = false;
	private boolean _stopped = false;

	private int _scanPause = SCAN_PAUSE;
	private int _scanInterval = SCAN_INTERVAL;

	public BleScanService() {
		Log.d(TAG, "constructor");
	}

	private boolean _initialized = false;
	private IStatusCallback _connectionCallback = new IStatusCallback() {
		@Override
		public void onSuccess() {
			Log.d(TAG, "successfully initialized BLE");
			_initialized = true;
		}

		@Override
		public void onError(int error) {
			Log.e(TAG, "Ble Error: " + error);
			_initialized = false;
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();

		INSTANCE = this;
		_ble = new BleExt();
		_ble.init(this, _connectionCallback);
		_ble.setScanFilter(BleDeviceFilter.doBeacon);

		HandlerThread handlerThread = new HandlerThread("BleScanService");
		handlerThread.start();
		_handler = new Handler(handlerThread.getLooper());
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
				_scanInterval = bundle.getInt(EXTRA_SCAN_INTERVAL, SCAN_INTERVAL);
				_scanPause = bundle.getInt(EXTRA_SCAN_PAUSE, SCAN_PAUSE);
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
		_handler.removeCallbacksAndMessages(null);

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
			_handler.postDelayed(_stopScanRunnable, SCAN_INTERVAL);
		}
	};

	private void onDeviceScanned(BleDevice device) {
		Log.d(TAG, "scanned device: " + device.getName() + " " + device.getRssi());
		for (ScanDeviceListener listener : _listenerList) {
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
					_handler.postDelayed(_startScanRunnable, SCAN_PAUSE);
				}

				@Override
				public void onError(int error) {
					_handler.postDelayed(_startScanRunnable, SCAN_PAUSE);
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
			_handler.post(_startScanRunnable);
		}
	}

	public void stopIntervalScan() {
		if (_scanning) {
			_handler.removeCallbacksAndMessages(null);
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

	public void registerScanDeviceListener(ScanDeviceListener listener) {
		if (_listenerList.indexOf(listener) == -1) {
			_listenerList.add(listener);
		}
	}

	public void unregisterScanDeviceListener(ScanDeviceListener listener) {
		if (_listenerList.indexOf(listener) != -1) {
			_listenerList.remove(listener);
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
