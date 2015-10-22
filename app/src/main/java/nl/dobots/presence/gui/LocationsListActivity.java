package nl.dobots.presence.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

import br.com.thinkti.android.filechooser.FileChooser;
import nl.dobots.bluenet.utils.logger.BleLogger;
import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.R;
import nl.dobots.presence.cfg.Settings;
import nl.dobots.bluenet.localization.locations.LocationsList;
import nl.dobots.presence.utils.Utils;

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
 * Created on 6-8-15
 *
 * @author Dominik Egger
 */
public class LocationsListActivity extends ActionBarActivity {

	private static final String TAG = LocationsListActivity.class.getCanonicalName();

	// onActivityResult request code for file selection
	private static final int FILE_CHOOSER = 6384;

	private ListView _lvLocationsList;
	private LocationsAdapter _locationsAdapter;
	private LocationsList _locationsList;

	private Settings _settings;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_config_locations);

		_settings = Settings.getInstance();
		_locationsList = _settings.getLocationsList();

		_locationsAdapter = new LocationsAdapter(this, _locationsList);

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
		PresenceDetectionApp.getInstance().logLine(BleLogger.BleLogEvent.appForeGround);
	}

	@Override
	protected void onPause() {
		super.onPause();
		PresenceDetectionApp.getInstance().logLine(BleLogger.BleLogEvent.appBackGround);
	}

	public static void show(Context context) {
		Intent showIntent = new Intent(context, LocationsListActivity.class);
		context.startActivity(showIntent);
	}

	public void saveLocations(View view) {
		_settings.writePersistentLocations(this);
		finish();
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
			case R.id.locations_action_clearall: {

				AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
				helpBuilder.setTitle("Exit");
				helpBuilder.setMessage("Are you sure you want clear all locations?");
				helpBuilder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								clearAll();
							}
						}
				);

				helpBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Do nothing
					}
				});

				AlertDialog helpDialog = helpBuilder.create();
				helpDialog.show();
			}
		}

		return super.onOptionsItemSelected(item);
	}

	private void clearAll() {
		_locationsList.clear();
		_locationsAdapter.notifyDataSetChanged();
		_settings.getDbAdapter(getApplicationContext()).clear();
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
