package nl.dobots.presence.cfg;

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
public class Config {

	////////////////////////
	// Presence Detection //
	////////////////////////

//	// NOTE: PRESENCE_UPDATE_TIMEOUT has to be smaller than PRESENCE_TIMEOUT
//	public static final long PRESENCE_UPDATE_TIMEOUT = 10 * 1000; // 10 seconds (in ms)
//	//	public static final long PRESENCE_TIMEOUT = 30 * (60 * 1000); // 30 minutes (in ms)
//	public static final long PRESENCE_TIMEOUT = 1* (30 * 1000); // 30 minutes (in ms)

//	public static final long PRESENCE_UPDATE_TIMEOUT = 2 * (60 * 1000); // 30 seconds (in ms)
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
	public static final long WATCHDOG_INTERVAL = 1 * (30 * 1000); // 30 seconds (in ms)

	//////////////////
	// Locations DB //
	//////////////////

	// version number
	public static final int DATABASE_VERSION = 1;
	// filename of the database
	public static final String DATABASE_NAME = "locations.db";

	//////////////////////////////
	// Graphical User Interface //
	//////////////////////////////

	public static final int UI_UPDATE_INTERVAL = 250;

	////////////////////
	// Manual Control //
	////////////////////

	public static final String[] EXPIRATION_TIMES_DISPLAY = new String[] {"5 min", "15 min", "1 h", "2 h", "4 h", "8 h", "24 h"};
	public static final long[] EXPIRATION_TIMES = new long[] {5, 15, 60, 120, 240, 480, 1440};

	//////////////////
	// Notification //
	//////////////////

	public static final int PRESENCE_NOTIFICATION_ID = 1010;

	//////////////
	// Settings //
	//////////////

	// default values
	public static final String USERNAME_DEFAULT = "";
	public static final String PASSWORD_DEFAULT = "";
	public static final String SERVER_DEFAULT = "http://dev.ask-cs.com";
	public static final float DETECTION_DISTANCE_DEFAULT = 2;
	public static final float HIGH_FREQUENCY_DISTANCE_DEFAULT = 6;
	public static final float LOW_FREQUENCY_DISTANCE_DEFAULT = 10;
	// database version, defines form of entries. increase if data changes
}
