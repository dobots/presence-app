package nl.dobots.presence.locations;

import java.util.ArrayList;

/**
 * Created by dominik on 7-8-15.
 */
public class LocationsList extends ArrayList<Location> {

	public Location findLocation(String deviceAddress) {

		for (Location location : this) {
			if (location.containsBeacon(deviceAddress)) {
				return location;
			}
		}

		return null;
	}

	public Location getLocation(String name) {
		for (Location location : this) {
			if (location.getName().equals(name)) {
				return location;
			}
		}

		return null;
	}

}
