package nl.dobots.presence.gui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.presence.R;

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
 */
public class LocationBeaconsAdapter extends BaseAdapter {
	BleDeviceList _arrayList;

	public LocationBeaconsAdapter(BleDeviceList array) {
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

		protected TextView txtBeaconName;
		protected TextView txtBeaconAddress;

	}

	// Get a View that displays the data at the specified position in the data set.
	// You can either create a View manually or inflate it from an XML layout file.
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if(convertView == null){
			// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
			LayoutInflater layoutInflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = layoutInflater.inflate(R.layout.location_beacon_item, null);
			final ViewHolder viewHolder = new ViewHolder();

			viewHolder.txtBeaconName = (TextView) convertView.findViewById(R.id.txtLocationBeaconName);
			viewHolder.txtBeaconAddress = (TextView) convertView.findViewById(R.id.txtLocationBeaconAddress);

			convertView.setTag(viewHolder);
		}

		ViewHolder viewHolder = (ViewHolder) convertView.getTag();

		if (!_arrayList.isEmpty()) {
			BleDevice beacon = _arrayList.get(position);
			viewHolder.txtBeaconName.setText(beacon.getName());
			viewHolder.txtBeaconAddress.setText("[" + beacon.getAddress() + "]");
		}

		return convertView;
	}

}
