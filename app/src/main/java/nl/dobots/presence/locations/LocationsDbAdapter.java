package nl.dobots.presence.locations;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

import nl.dobots.bluenet.extended.structs.BleDevice;

/**
 * Created by dominik on 6-8-15.
 */
public class LocationsDbAdapter {

	///////////////////////////////////////////////////////////////////////////////////////////
	/// Variables
	///////////////////////////////////////////////////////////////////////////////////////////

	private static final String TAG = LocationsDbAdapter.class.getCanonicalName();

	// database version, defines form of entries. increase if data changes
	public static final int DATABASE_VERSION = 1;
	// filename of the database
	public static final String DATABASE_NAME = "locations.db";

	// key names of the database fields
	public static final String KEY_ADDRESS = "address";
	public static final String KEY_LOCATION = "location";
	public static final String KEY_NAME = "name";
	public static final String KEY_ROWID = "_id";

	// table name
	public static final String TABLE_NAME = "locations";

	// database helper to manage database creation and version management.
	private DatabaseHelper mDbHelper;

	// database object to read and write database
	private SQLiteDatabase mDb;

	// define query used to create the database
	public static final String DATABASE_CREATE =
			"create table " + TABLE_NAME + " (" +
					KEY_ROWID + " integer primary key autoincrement, " +
					KEY_LOCATION + " text not null," +
					KEY_ADDRESS + " text not null," +
					KEY_NAME + " text not null" +
					" )";

	// application context
	private final Context mContext;

	///////////////////////////////////////////////////////////////////////////////////////////
	/// Code
	///////////////////////////////////////////////////////////////////////////////////////////

	// helper class to manage database creation and version management, see SQLiteOpenHelper
	private static class DatabaseHelper extends SQLiteOpenHelper {

		// default constructor
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		// called when database should be created
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
		}

		// called if version changed and database needs to be upgraded
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
					newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS notes");
			onCreate(db);
		}

	}

	// default constructor, assigns context and initializes date formats
	public LocationsDbAdapter(Context context) {
		mContext = context;
//		context.deleteDatabase(DATABASE_NAME);
	}

	/**
	 * Open the database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 *
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public LocationsDbAdapter open() throws SQLException {
		mDbHelper = new DatabaseHelper(mContext);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	/**
	 * Close the database
	 */
	public void close() {
		mDbHelper.close();
	}

	public void clear() {
		mDb.delete(TABLE_NAME, null, null);
	}

	public boolean saveAll(LocationsList list) {
		clear();

		boolean success = true;
		for (Location location : list) {
			success &= addLocation(location);
		}
		return success;
	}

//	public void loadAll() {
	public void loadAll(LocationsList list) {

		HashMap<String, Location> hashMap = new HashMap<>();

//		LocationsList result = new ArrayList<>();
		Cursor cursor = fetchAllEntries();

		Location location = null;
//		String lastLocationStr = "";

		// as long as there are entries
		while (!cursor.isAfterLast()) {

			String locationStr = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOCATION));

//			if (!locationStr.matches(lastLocationStr)) {
//				location = new Location(locationStr);
//				list.add(location);
//			}
			if (hashMap.containsKey(locationStr)) {
				location = hashMap.get(locationStr);
			} else {
				location = new Location(locationStr);
				hashMap.put(locationStr, location);
			}

			String address = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ADDRESS));
			String name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME));

			// dummy value -1 for rssi, because we don't need the rssi for the locations
			location.addBeacon(new BleDevice(address, name, -1));

			cursor.moveToNext();
		}

		list.addAll(hashMap.values());
//		return result;
	}

	public boolean addLocation(Location location) {
		ContentValues values = new ContentValues();

		for (BleDevice device : location.getBeaconsList()) {
			values.put(KEY_LOCATION, location.getName());
			values.put(KEY_ADDRESS, device.getAddress());
			values.put(KEY_NAME, device.getName());

			if (replaceEntry(values) == -1) {
				return false;
			}
		}

		return true;
	}

	public long createEntry(ContentValues values) {
		return mDb.insert(TABLE_NAME, null, values);
	}

	public long replaceEntry(ContentValues values) {
		return mDb.replace(TABLE_NAME, null, values);
	}

	/**
	 * Update existing entry. Return true if entry was updated
	 * successfully
	 *
	 * @param id the row id of the entry to be updated
	 * @return true if updated successfully, false otherwise
	 */
	public boolean updateEntry(long id, String location, String address, String name) {
		ContentValues values = new ContentValues();

		values.put(KEY_LOCATION, location);
		values.put(KEY_ADDRESS, address);
		values.put(KEY_NAME, name);

		int num = mDb.update(TABLE_NAME, values, "_id " + "=" + id, null);
		return num == 1;
	}

	/**
	 * Delete the entry with the given rowId
	 *
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteEntry(long rowId) {
		return mDb.delete(TABLE_NAME, KEY_ROWID + "=" + rowId, null) > 0;
	}

	/**
	 * Fetch all entries in the database
	 *
	 * @return cursor to access the entries
	 */
	public Cursor fetchAllEntries() {
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_ROWID, KEY_LOCATION, KEY_ADDRESS, KEY_NAME},
				null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Fetch entry defined by row id
	 *
	 * @param rowId the id of the entry which should be returned
	 * @return cursor to access the entry
	 */
	public Cursor fetchEntry(long rowId) {
		Cursor mCursor = mDb.query(TABLE_NAME, new String[] {KEY_ROWID, KEY_LOCATION, KEY_ADDRESS, KEY_NAME},
				KEY_ROWID + "=" + rowId, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

}

