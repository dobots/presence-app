package nl.dobots.presence.cfg;

import android.content.Context;
import android.content.SharedPreferences;

import nl.dobots.bluenet.localization.locations.Location;
import nl.dobots.bluenet.localization.locations.LocationsDbAdapter;
import nl.dobots.bluenet.localization.locations.LocationsList;

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
 * Created on 4-8-15
 *
 * @author Dominik Egger
 */
public class Settings {

	private static final String SETTING_FILE = "general_settings";

	private static final String DETECTION_DISTANCE_KEY = "detectionDistanceKey";
	private static final String LOW_FREQUENCY_DISTANCE_KEY = "lowFrequencyDistanceKey";
	private static final String HIGH_FREQUENCY_DISTANCE_KEY = "highFrequencyDistanceKey";
	private static final String NOTIFICATIONS_ENABLED_KEY = "notificationsEnabledKey";
	private static final String USERNAME_KEY = "usernameKey";
	private static final String PASSWORD_KEY = "passwordKey";
	private static final String SERVER_KEY = "serverKey";

	private static Settings INSTANCE = null;

	private float _detectionDistance;
	private float _highFrequencyDistance;
	private float _lowFrequencyDistance;
	private String _username;
	private String _password;
	private String _server;

	private LocationsList _locationsList = new LocationsList();
	private LocationsDbAdapter _dbAdapter = null;
	private boolean _notificationsEnabled;

	private Settings() {
	}

	public static Settings getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new Settings();
		}
		return INSTANCE;
	}

	public void onDestroy() {
		if (_dbAdapter != null) {
			_dbAdapter.close();
		}
	}

	public float getHighFrequencyDistance() {
		return _highFrequencyDistance;
	}

	public float getLowFrequencyDistance() {
		return _lowFrequencyDistance;
	}

	public float getDetectionDistance() {
		return _detectionDistance;
	}

	public void setDetectionDistance(float detectionDistance) {
		_detectionDistance = detectionDistance;
	}

	public void setHighFrequencyDistance(float highFrequencyDistance) {
		_highFrequencyDistance = highFrequencyDistance;
	}

	public void setLowFrequencyDistance(float lowFrequencyDistance) {
		_lowFrequencyDistance = lowFrequencyDistance;
	}

	public String getUsername() {
		return _username;
	}

	public void setUsername(String username) {
		_username = username;
	}

	public String getPassword() {
		return _password;
	}

	public void setPassword(String password) {
		_password = password;
	}

	public String getServer() {
		return _server;
	}

	public void setServer(String server) {
		_server = server;
	}
	public void writePersistentSettings(Context context) {
		//store the settings in the Shared Preference file
		SharedPreferences sharedPreferences = context.getSharedPreferences(SETTING_FILE, 0);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putFloat(DETECTION_DISTANCE_KEY, _detectionDistance);
		editor.putFloat(LOW_FREQUENCY_DISTANCE_KEY, _lowFrequencyDistance);
		editor.putFloat(HIGH_FREQUENCY_DISTANCE_KEY, _highFrequencyDistance);
		editor.putBoolean(NOTIFICATIONS_ENABLED_KEY, _notificationsEnabled);
		editor.commit();
	}

	public void writePersistentCredentials(Context context) {
		//store the settings in the Shared Preference file
		SharedPreferences settings = context.getSharedPreferences(SETTING_FILE, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(USERNAME_KEY, _username);
		editor.putString(PASSWORD_KEY, _password);
		editor.putString(SERVER_KEY, _server);
		// Commit the edits!
		editor.commit();
	}

	public void clearSettings(Context context){
		SharedPreferences sharedPreferences = context.getSharedPreferences(SETTING_FILE, 0);
		sharedPreferences.edit().clear().commit();
		_detectionDistance = Config.DETECTION_DISTANCE_DEFAULT;
		_highFrequencyDistance = Config.HIGH_FREQUENCY_DISTANCE_DEFAULT;
		_lowFrequencyDistance = Config.LOW_FREQUENCY_DISTANCE_DEFAULT;
		_notificationsEnabled = true;

		clearPersistentLocations(context);
		_locationsList.clear();
	}

	public void readPersistentStorage(Context context) {
		SharedPreferences sharedPreferences = context.getSharedPreferences(SETTING_FILE, 0);
		_detectionDistance = sharedPreferences.getFloat(DETECTION_DISTANCE_KEY, Config.DETECTION_DISTANCE_DEFAULT);
		_lowFrequencyDistance = sharedPreferences.getFloat(LOW_FREQUENCY_DISTANCE_KEY, Config.LOW_FREQUENCY_DISTANCE_DEFAULT);
		_highFrequencyDistance = sharedPreferences.getFloat(HIGH_FREQUENCY_DISTANCE_KEY, Config.HIGH_FREQUENCY_DISTANCE_DEFAULT);
		_username = sharedPreferences.getString(USERNAME_KEY, Config.USERNAME_DEFAULT);
		_password = sharedPreferences.getString(PASSWORD_KEY, Config.PASSWORD_DEFAULT);
		_server = sharedPreferences.getString(SERVER_KEY, Config.SERVER_DEFAULT);
		_notificationsEnabled = sharedPreferences.getBoolean(NOTIFICATIONS_ENABLED_KEY, true);
	}

	public LocationsDbAdapter getDbAdapter(Context context) {
		if (_dbAdapter == null) {
			_dbAdapter = new LocationsDbAdapter(context).open(Config.DATABASE_NAME, Config.DATABASE_VERSION);
		}
		return _dbAdapter;
	}

	public void writePersistentLocations(Context context) {
		getDbAdapter(context).saveAll(_locationsList);
	}

	public void readPersistentLocations(Context context) {
		getDbAdapter(context).loadAll(_locationsList);
	}

	public void clearPersistentLocations(Context context) {
		getDbAdapter(context).clear();
	}

	public LocationsList getLocationsList() {
		return _locationsList;
	}

	public void addNewLocation(Location location) {
		_locationsList.add(location);
	}

	public void setNotificationsEnabled(boolean notificationsEnabled) {
		_notificationsEnabled = notificationsEnabled;
	}

	public boolean isNotificationsEnabled() {
		return _notificationsEnabled;
	}
}
