package nl.dobots.presence;

/**
 * Created by dominik on 6-8-15.
 */
public class Config {

	public static final String DATABASE_NAME = "WhosInLocationsDB";
	public static final int DATABASE_VERSION = 1;

	public static final int UI_UPDATE_INTERVAL = 250;

	public static final String[] EXPIRATION_TIMES_DISPLAY = new String[] {"5 min", "15 min", "1 h", "2 h", "4 h", "8 h", "24 h"};
	public static final long[] EXPIRATION_TIMES = new long[] {5, 15, 60, 120, 240, 480, 1440};

	public static final int PRESENCE_NOTIFICATION_ID = 1010;

//	// NOTE: PRESENCE_UPDATE_TIMEOUT has to be smaller than PRESENCE_TIMEOUT
//	public static final long PRESENCE_UPDATE_TIMEOUT = 10 * 1000; // 30 seconds (in ms)
//	//	public static final long PRESENCE_TIMEOUT = 30 * (60 * 1000); // 30 minutes (in ms)
//	public static final long PRESENCE_TIMEOUT = 1* (30 * 1000); // 30 minutes (in ms)

	public static final long PRESENCE_UPDATE_TIMEOUT = 2 * (60 * 1000); // 30 seconds (in ms)
	//	public static final long PRESENCE_TIMEOUT = 30 * (60 * 1000); // 30 minutes (in ms)
	public static final long PRESENCE_TIMEOUT = 3 * (60 * 1000); // 3 minutes (in ms)

	// scan for 1 second every 30 seconds
	public static final int LOW_SCAN_INTERVAL = 1000; // 1 second scanning
	public static final int LOW_SCAN_PAUSE = 29000; // 29 seconds pause

//	public static final int LOW_SCAN_PAUSE = 2500; // 2.5 seconds
//	public static final int LOW_SCAN_PERIOD = 500; // 0.5 seconds
//	public static final int LOW_SCAN_EXPIRATION = 3500; // 3.5 seconds

//	public static final int HIGH_SCAN_PAUSE = 500; // 0.5 seconds
//	public static final int HIGH_SCAN_PERIOD = 500; // 0.5 seconds
//	public static final int HIGH_SCAN_EXPIRATION = 2000; // 2 seconds

	//	private static final long WATCHDOG_INTERVAL = 5 * (60 * 1000); // 5 minutes (in ms)
	static final long WATCHDOG_INTERVAL = 1 * (30 * 1000); // 5 minutes (in ms)

}
