package nl.dobots.presence.cfg;

import android.content.Context;
import android.content.SharedPreferences;

import nl.dobots.presence.locations.Location;
import nl.dobots.presence.locations.LocationsDbAdapter;
import nl.dobots.presence.locations.LocationsList;

/**
 * Created by dominik on 4-8-15.
 */
public class Settings {

	private static final String SETTING_FILE = "general_settings";

	private static Settings INSTANCE = null;

	private float _detectionDistance;
	private float _highFrequencyDistance;
	private float _lowFrequencyDistance;
	private String _username;
	private String _password;
	private String _server;

	private LocationsList _locationsList = new LocationsList();
	private LocationsDbAdapter _dbAdapter = null;

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
		editor.putFloat("detectionDistanceKey", _detectionDistance);
		editor.putFloat("lowFrequencyDistanceKey", _lowFrequencyDistance);
		editor.putFloat("highFrequencyDistanceKey", _highFrequencyDistance);
		editor.commit();
	}

	public void writePersistentCredentials(Context context) {
		//store the settings in the Shared Preference file
		SharedPreferences settings = context.getSharedPreferences(SETTING_FILE, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("usernameKey", _username);
		editor.putString("passwordKey", _password);
		editor.putString("serverKey", _server);
		// Commit the edits!
		editor.commit();
	}

	public void clearSettings(Context context){
		SharedPreferences sharedPreferences = context.getSharedPreferences(SETTING_FILE, 0);
		sharedPreferences.edit().clear().commit();
		_detectionDistance = Config.DETECTION_DISTANCE_DEFAULT;
		_highFrequencyDistance = Config.HIGH_FREQUENCY_DISTANCE_DEFAULT;
		_lowFrequencyDistance = Config.LOW_FREQUENCY_DISTANCE_DEFAULT;

		clearPersistentLocations(context);
		_locationsList.clear();
	}

	public void readPersistentStorage(Context context) {
		SharedPreferences sharedPreferences = context.getSharedPreferences(SETTING_FILE, 0);
		_detectionDistance = sharedPreferences.getFloat("detectionDistanceKey", Config.DETECTION_DISTANCE_DEFAULT);
		_lowFrequencyDistance = sharedPreferences.getFloat("lowFrequencyDistanceKey", Config.LOW_FREQUENCY_DISTANCE_DEFAULT);
		_highFrequencyDistance = sharedPreferences.getFloat("highFrequencyDistanceKey", Config.HIGH_FREQUENCY_DISTANCE_DEFAULT);
		_username = sharedPreferences.getString("usernameKey", Config.USERNAME_DEFAULT);
		_password = sharedPreferences.getString("passwordKey", Config.PASSWORD_DEFAULT);
		_server = sharedPreferences.getString("serverKey", Config.SERVER_DEFAULT);
	}

	public LocationsDbAdapter getDbAdapter(Context context) {
		if (_dbAdapter == null) {
			_dbAdapter = new LocationsDbAdapter(context).open();
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
}
