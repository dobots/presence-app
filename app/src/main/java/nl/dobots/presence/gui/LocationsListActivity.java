package nl.dobots.presence.gui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import br.com.thinkti.android.filechooser.FileChooser;
import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.R;
import nl.dobots.presence.cfg.Settings;
import nl.dobots.presence.locations.Location;
import nl.dobots.presence.locations.LocationsList;
import nl.dobots.presence.utils.Utils;

/**
 * Created by dominik on 6-8-15.
 */
public class LocationsListActivity extends ActionBarActivity {

	private static final String TAG = LocationsListActivity.class.getCanonicalName();

	// onActivityResult request code for file selection
	private static final int FILE_CHOOSER = 6384;

	private ListView _lvLocationsList;
	private LocationsAdapter _locationsAdapter;
	private LocationsList _locationsList;

	private Settings _settings;

	private Handler _handler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_config_locations);

		_settings = Settings.getInstance();
		_locationsList = _settings.getLocationsList();

		_locationsAdapter = new LocationsAdapter(_locationsList);

		initUI();

		PresenceDetectionApp.getInstance().pauseDetection();
	}

	private void initUI() {

		_lvLocationsList = (ListView) findViewById(R.id.lvLocationList);
		_lvLocationsList.setAdapter(_locationsAdapter);
//		_lvLocationsList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
//			@Override
//			public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
//
//				String locationName = _locationsList.get(position).getName();
//
//				final AlertDialog.Builder builder = new AlertDialog.Builder(LocationsListActivity.this);
//				builder.setTitle("Remove Location");
//				builder.setMessage("Do you want to remove the location " + locationName + "?");
//				builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//					public void onClick(DialogInterface dialog, int id) {
//						_locationsList.remove(position);
//						_locationsAdapter.notifyDataSetChanged();
//					}
//				});
//				builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
//					public void onClick(DialogInterface dialog, int id) {
//						/* nothing to do */
//					}
//				});
//				builder.show();
//
//				return true;
//			}
//		});
		registerForContextMenu(_lvLocationsList);
		_lvLocationsList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

	}

	private void removeLocation(int position) {
		_locationsList.remove(position);
		_locationsAdapter.notifyDataSetChanged();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.lvLocationList) {
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
			menu.setHeaderTitle(String.format("Location: %s", _locationsList.get(info.position).getName()));
			menu.add(0, 0, 0, "Edit");
			menu.add(0, 1, 1, "Remove");
		} else {
			super.onCreateContextMenu(menu, v, menuInfo);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

		switch (item.getItemId()) {
			case 0: {
				EditLocationActivity.show(this, _locationsList.get(info.position).getName());
				break;
			}
			case 1: {
				removeLocation(info.position);
				break;
			}
			default:
				return super.onContextItemSelected(item);
		}
		return true;
	}

	public void addLocation(View view) {
		AddNewLocationActivity.show(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		_locationsAdapter.notifyDataSetChanged();
		Utils.setListViewHeightBasedOnChildren(_lvLocationsList);
	}

	public static void show(Context context) {
		Intent showIntent = new Intent(context, LocationsListActivity.class);
		context.startActivity(showIntent);
	}

	public void saveLocations(View view) {
		_settings.writePersistentLocations(this);
		finish();
	}


	// Regular inner class which act as the Adapter
	public class LocationsAdapter extends BaseAdapter {
		LocationsList _arrayList;

		public LocationsAdapter(LocationsList array) {
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

		private class ViewHolder {

			protected TextView txtLocationName;
			protected ListView lvLocationBeacons;

		}

		// Get a View that displays the data at the specified position in the data set.
		// You can either create a View manually or inflate it from an XML layout file.
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			if(convertView == null){
				// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
				LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
				convertView = layoutInflater.inflate(R.layout.location_item, null);
				final ViewHolder viewHolder = new ViewHolder();

				viewHolder.txtLocationName = (TextView) convertView.findViewById(R.id.txtLocationName);
				viewHolder.lvLocationBeacons = (ListView) convertView.findViewById(R.id.lvLocationBeacons);

				convertView.setTag(viewHolder);
			}

			final ViewHolder viewHolder = (ViewHolder) convertView.getTag();

			if (!_arrayList.isEmpty()) {
				Location location  = _arrayList.get(position);
				viewHolder.txtLocationName.setText(location.getName());
				viewHolder.lvLocationBeacons.setAdapter(location.getLocationBeaconsAdapter());
				viewHolder.lvLocationBeacons.setOnItemLongClickListener(location.getOnBeaconLongClickListener());
				// doesn't work correctly if it is not given to the handler
				_handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						Utils.setListViewHeightBasedOnChildren(viewHolder.lvLocationBeacons);
					}
				}, 0);
			}

			return convertView;
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_locations, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
			case R.id.locations_action_export: {
				String directoryPath = Environment.getExternalStorageDirectory() + "/whosin/";
				String fileName = directoryPath + "whosin_locationsDB.csv";
				if (_settings.getDbAdapter(getApplicationContext()).exportDB(fileName)) {
					Toast.makeText(getApplicationContext(), "DB successfully exported to " + fileName + "!", Toast.LENGTH_LONG).show();
				}
				return true;
			}
			case R.id.locations_action_import: {
				showFileChooser();
				return true;
			}
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Show file chooser
	 */
	private void showFileChooser() {
		Intent intent = new Intent(this, FileChooser.class);
		ArrayList<String> extensions = new ArrayList<String>();
		extensions.add(".csv");
		intent.putStringArrayListExtra("filterFileExtension", extensions);
		startActivityForResult(intent, FILE_CHOOSER);
	}

	/**
	 * Called once a file was chosen
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ((requestCode == FILE_CHOOSER) && (resultCode == -1)) {
			// Get the file path from the URI
			String fileSelected = data.getStringExtra("fileSelected");
			Toast.makeText(this, "File Selected: " + fileSelected, Toast.LENGTH_LONG).show();

			if (_settings.getDbAdapter(getApplicationContext()).importDB(fileSelected)) {
				_settings.readPersistentLocations(getApplicationContext());
				_locationsAdapter.notifyDataSetChanged();
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}



//    @Override
//    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
//        Log.i(TAG, "clicked on item" + arg2);
//        if(!PresenceApp.INSTANCE.beaconAddressArray.contains(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress())) {
//            PresenceApp.INSTANCE.beaconAddressArray.add(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress());
//            arg1.setBackgroundColor(0x660000FF);
//        }
//        else{
//            doBeaconSelectedArray.remove(doBeaconUnfilteredArray.get(arg2));
//            PresenceApp.INSTANCE.beaconAddressArray.remove(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress());
//            arg1.setBackgroundColor(0x00000000);
//        }
//    }

}
