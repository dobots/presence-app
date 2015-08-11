package nl.dobots.presence.localization;

import java.util.ArrayList;

import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.presence.locations.Location;

/**
 * Created by dominik on 11-8-15.
 */
public interface Localization {

	class LocalizationResult {
		public Location location;
		public BleDevice triggerDevice;
	}

	/**
	 * go through the list of devices and determine the current location
	 * if no location was found, return null
	 * @param devices list of scanned devices (ordered by distance?)
	 * @return return the found location and the device which triggered
	 * 			the location
	 */
	LocalizationResult findLocation(ArrayList<BleDevice> devices);

	/**
	 * return the last time a device was seen which is registered in one of
	 * the locations
	 * @return time in ms
	 */
	long getLastDetectionTime();

}
