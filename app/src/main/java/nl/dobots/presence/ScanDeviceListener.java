package nl.dobots.presence;

import nl.dobots.bluenet.extended.structs.BleDevice;

/**
 * Created by dominik on 4-8-15.
 */
public interface ScanDeviceListener {

	void onDeviceScanned(BleDevice device);

}
