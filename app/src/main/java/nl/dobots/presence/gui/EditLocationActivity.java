package nl.dobots.presence.gui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.text.method.CharacterPickerDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceList;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.R;
import nl.dobots.presence.cfg.Settings;
import nl.dobots.presence.locations.Location;
import nl.dobots.presence.srv.BleScanService;
import nl.dobots.presence.srv.ScanDeviceListener;


public class EditLocationActivity extends ActionBarActivity implements ScanDeviceListener {

	private static final String TAG = EditLocationActivity.class.getCanonicalName();

	private ScannedDeviceAdapter _adapter;
	private BleDeviceMap _deviceMap;
//	private BleDeviceList _deviceList;

	private Settings _settings;

	private BleScanService _service;
	private boolean _bound;
	private ArrayList<String> _selectedDevices;

	private boolean _scanning;

	private int _oldScanInterval;
	private int _oldScanPause;
	private boolean _oldIsScanning;

	private ListView _lvScannedBeacons;
	private EditText _edtLocationName;
	private Button _btnScanLocationBeacons;
	private Button _btnStoreLocation;
	private Location _location;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_location);


		String locationName = getIntent().getStringExtra("location");

		_settings = Settings.getInstance();

		_location = _settings.getLocationsList().getLocation(locationName);
		BleDeviceList _deviceList = _location.getBeaconsList();

		_deviceMap = new BleDeviceMap();
		for (BleDevice device : _deviceList) {
			_deviceMap.updateDevice(device);
		}

		_selectedDevices = new ArrayList<>(_deviceMap.keySet());

		_adapter = new ScannedDeviceAdapter(_deviceList);

		initUI();

		_edtLocationName.setText(locationName);

		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);

		PresenceDetectionApp.getInstance().pauseDetection();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (_bound) {
			unbindService(_connection);
		}
	}

	private void initUI() {

		_edtLocationName = (EditText) findViewById(R.id.edtAddLocationName);

		_btnScanLocationBeacons = (Button) findViewById(R.id.btnScanLocationBeacons);

		_lvScannedBeacons = (ListView) findViewById(R.id.lvScannedBeacons);
		_lvScannedBeacons.setAdapter(_adapter);
		_lvScannedBeacons.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				String address = ((BleDevice) _adapter.getItem(position)).getAddress();
				if (_selectedDevices.indexOf(address) == -1) {
					_selectedDevices.add(address);
				} else {
					_selectedDevices.remove(address);
				}
				_adapter.notifyDataSetChanged();
			}
		});

		_btnStoreLocation = (Button) findViewById(R.id.btnStoreLocation);
		_btnStoreLocation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				storeLocation();
			}
		});
		_btnStoreLocation.setText("Save");

	}

	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "connected to ble scan service ...");
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();
			_service.registerScanDeviceListener(EditLocationActivity.this);
			_bound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
			_bound = false;
		}
	};

	@Override
	public void onDeviceScanned(BleDevice device) {
		_deviceMap.updateDevice(device);
		final BleDeviceList deviceList = _deviceMap.getDistanceSortedList();
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_adapter.updateList(deviceList);
				_adapter.notifyDataSetChanged();
			}
		});
	}

	public void toggleScan(View v) {
		if (_bound) {
			if (_scanning) {
				_service.stopIntervalScan();
				_service.setScanInterval(_oldScanInterval);
				_service.setScanPause(_oldScanPause);
				if (_oldIsScanning) {
					_service.startIntervalScan();
				}
				_btnScanLocationBeacons.setText("Scan for beacons");
			} else {
				_oldIsScanning = _service.isScanning();
				_service.stopIntervalScan();
				_oldScanInterval = _service.getScanInterval();
				_oldScanPause = _service.getScanPause();
				_service.setScanInterval(2000);
				_service.setScanPause(100);
				_service.startIntervalScan();
				_btnScanLocationBeacons.setText("Stop");
			}
			_scanning = !_scanning;
		}
	}

	public void storeLocation() {
		String name;
		if (!(name = _edtLocationName.getText().toString()).isEmpty()) {
			_location.setName(name);

			BleDeviceList beaconsList = _location.getBeaconsList();
			for (String address : _selectedDevices) {
				if (!beaconsList.containsDevice(address)) {
					beaconsList.add(_deviceMap.getDevice(address));
				}
			}
//			Settings.getInstance().addNewLocation(location);
			finish();
		} else {
			Toast.makeText(this, "Location name cannot be empty! Please fill in a name.", Toast.LENGTH_LONG).show();
			_edtLocationName.requestFocus();
		}
	}

	public static void show(Context context, String name) {
		Intent showIntent = new Intent(context, EditLocationActivity.class);
		showIntent.putExtra("location", name);
		context.startActivity(showIntent);
	}

	// Regular inner class which act as the Adapter
	public class ScannedDeviceAdapter extends BaseAdapter {
		BleDeviceList _arrayList;

		public ScannedDeviceAdapter(BleDeviceList array) {
			_arrayList = array;
		}

		// How many items are in the data set represented by this Adapter.
		@Override
		public int getCount() {
			return _arrayList.size();
		}

		// Get the data item associated with the specified position in the data set.
		@Override
		public Object getItem(int position) {
			return _arrayList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		public void updateList(BleDeviceList list) {
			_arrayList = list;
		}

		private class ViewHolder {

			protected TextView txtDeviceName;
			protected TextView txtDeviceAddress;
			protected TextView txtDeviceRssi;
			protected TextView txtDeviceDistance;

			protected boolean selected;

		}

		// Get a View that displays the data at the specified position in the data set.
		// You can either create a View manually or inflate it from an XML layout file.
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			if(convertView == null){
				// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
				LayoutInflater layoutInflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = layoutInflater.inflate(R.layout.device_item, null);
				final ViewHolder viewHolder = new ViewHolder();

				viewHolder.txtDeviceName = (TextView) convertView.findViewById(R.id.txtDeviceName);
				viewHolder.txtDeviceAddress = (TextView) convertView.findViewById(R.id.txtDeviceAddress);
				viewHolder.txtDeviceRssi = (TextView) convertView.findViewById(R.id.txtDeviceRssi);
				viewHolder.txtDeviceDistance = (TextView) convertView.findViewById(R.id.txtDeviceDistance);

				convertView.setTag(viewHolder);
			}

			ViewHolder viewHolder = (ViewHolder) convertView.getTag();

			if (!_arrayList.isEmpty()) {
				BleDevice beacon = _arrayList.get(position);
				viewHolder.txtDeviceName.setText(beacon.getName());
				viewHolder.txtDeviceAddress.setText(beacon.getAddress());
				viewHolder.txtDeviceRssi.setText(String.valueOf(beacon.getAverageRssi()));
				viewHolder.txtDeviceDistance.setText(String.format("%.2f m", beacon.getDistance()));

				if (_selectedDevices.indexOf(beacon.getAddress()) != -1) {
					convertView.setBackgroundColor(0x660000FF);
				} else {
					convertView.setBackgroundColor(0x00000000);
				}
			}

			return convertView;
		}

	}


}