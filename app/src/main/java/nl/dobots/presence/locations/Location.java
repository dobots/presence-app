package nl.dobots.presence.locations;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import nl.dobots.bluenet.BleUtils;
import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceList;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.presence.R;
import nl.dobots.presence.utils.Utils;

/**
 * Created by dominik on 6-8-15.
 */
public class Location {

	private String _name;

	private BleDeviceMap _beaconsMap;
	private BleDeviceList _beaconsList;

	private ListAdapter _locationBeaconsAdapter;

	public Location(String name) {
		_name = name;

		_beaconsMap = new BleDeviceMap();
		_beaconsList = new BleDeviceList();

		_locationBeaconsAdapter = new LocationBeaconsAdapter(_beaconsList);
	}

	private AdapterView.OnItemLongClickListener _onBeaconLongClickListener = new AdapterView.OnItemLongClickListener() {
		@Override
		public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, long id) {

			String beaconName = _beaconsList.get(position).getName();

			final AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
			builder.setTitle("Remove Beacon");
			builder.setMessage("Do you want to remove the beacon " + beaconName + "?");
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					_beaconsList.remove(position);
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
	};

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		_name = name;
	}

	public void addBeacon(BleDevice device) {
		_beaconsMap.updateDevice(device);
		_beaconsList.add(device);
	}

	public void removeBeacon(BleDevice device) {
		_beaconsMap.remove(device.getAddress());
		_beaconsList.remove(device);
	}

	public boolean containsBeacon(String deviceAddress) {
		return _beaconsMap.containsKey(deviceAddress);
	}

	public ListAdapter getLocationBeaconsAdapter() {
		return _locationBeaconsAdapter;
	}

	public void setLocationBeaconsAdapter(ListAdapter locationBeaconsAdapter) {
		_locationBeaconsAdapter = locationBeaconsAdapter;
	}

	public AdapterView.OnItemLongClickListener getOnBeaconLongClickListener() {
		return _onBeaconLongClickListener;
	}

	public BleDeviceList getBeaconsList() {
		return _beaconsList;
	}

	// Regular inner class which act as the Adapter
	public class LocationBeaconsAdapter extends BaseAdapter {
		ArrayList<BleDevice> _arrayList;

		public LocationBeaconsAdapter(ArrayList<BleDevice> array) {
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

}
