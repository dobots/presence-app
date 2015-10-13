package nl.dobots.presence.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.bluenet.localization.locations.Location;
import nl.dobots.bluenet.localization.locations.LocationsList;
import nl.dobots.presence.R;
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
 * Created on 16-9-15
 *
 * @author Dominik Egger
 */ // Regular inner class which act as the Adapter
public class LocationsAdapter extends BaseAdapter {

	private Handler _handler = new Handler();

	private LocationsList _arrayList;
	private Context _context;

	public LocationsAdapter(Context context, LocationsList array) {
		_context = context;
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

		if (convertView == null) {
			// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
			LayoutInflater layoutInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = layoutInflater.inflate(R.layout.location_item, null);
			final ViewHolder viewHolder = new ViewHolder();

			viewHolder.txtLocationName = (TextView) convertView.findViewById(R.id.txtLocationName);
			viewHolder.lvLocationBeacons = (ListView) convertView.findViewById(R.id.lvLocationBeacons);

			convertView.setTag(viewHolder);
		}

		final ViewHolder viewHolder = (ViewHolder) convertView.getTag();

		if (!_arrayList.isEmpty()) {
			Location location = _arrayList.get(position);
			viewHolder.txtLocationName.setText(location.getName());
			final BleDeviceList beaconsList = location.getBeaconsList();
			ListAdapter adapter = new LocationBeaconsAdapter(beaconsList);
			viewHolder.lvLocationBeacons.setAdapter(adapter);
			viewHolder.lvLocationBeacons.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, long id) {
					String beaconName = beaconsList.get(position).getName();

					final AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
					builder.setTitle("Remove Beacon");
					builder.setMessage("Do you want to remove the beacon " + beaconName + "?");
					builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							beaconsList.remove(position);
							((LocationBeaconsAdapter) parent.getAdapter()).notifyDataSetChanged();
							Utils.setListViewHeightBasedOnChildren((ListView) view.getParent());
						}
					});
					builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
						/* nothing to do */
						}
					});
					builder.show();

					return true;
				}
			});
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
