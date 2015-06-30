package nl.dobots.presence;


import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;

/**
 * Created by christian Haas-Frangi on 18/06/15.
 */
public class scanActivity extends Activity implements OnItemClickListener {

    private ListView doBeaconListView;
    private TextView doBeaconNameView;
    private TextView doBeaconUUIDView;
    private TextView doBeaconMajorView;
    public  ProgressDialog progress;
    public CustomAdapter customAdapter;

    protected static final String TAG = scanActivity.class.getCanonicalName();
    final Handler handler = new Handler();
    static public boolean isScanActivityActive;
    private boolean isLoadingOn;

    public ArrayList<Beacon> doBeaconUnfilteredArray = new ArrayList<Beacon>();
    public ArrayList<Beacon> doBeaconSelectedArray= new ArrayList<Beacon>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_listview);
        isScanActivityActive=true;
        doBeaconUnfilteredArray= PresenceApp.doBeaconArray;

        BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);
        try {
            beaconManager.setBackgroundScanPeriod(PresenceApp.HIGH_SCAN_PERIOD);
            beaconManager.setForegroundScanPeriod(PresenceApp.HIGH_SCAN_PERIOD);
            beaconManager.updateScanPeriods();
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        
        //show a progress message
        progress = new ProgressDialog(this);
        progress.setTitle("Scanning");
        progress.setMessage("Wait while scanning...");
        progress.show();
        isLoadingOn=true;

        // To show the doBeacons in a list
        doBeaconListView = (ListView) findViewById(R.id.doBeaconListView);
        // Initialize our Adapter and plug it to the ListView
        final CustomAdapter customAdapter = new CustomAdapter(doBeaconUnfilteredArray);
        doBeaconListView.setAdapter(customAdapter);
        // Activate the Click even of the List items
        doBeaconListView.setOnItemClickListener(this);
        //initiate buttons
        initButtons();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!doBeaconUnfilteredArray.isEmpty()) {
                    if (isLoadingOn) {
                        progress.dismiss();
                        isLoadingOn = false;
                    }
                    customAdapter.updateResults(doBeaconUnfilteredArray);
                }
                if (isScanActivityActive)
                    handler.postDelayed(this, 500);
            }
        }, 500);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isScanActivityActive=false;
        writePersistentSettings();
        PresenceApp.doBeaconArray=doBeaconSelectedArray;

        BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);
        try {
            beaconManager.setBackgroundScanPeriod(PresenceApp.LOW_SCAN_PERIOD);
            beaconManager.setForegroundScanPeriod(PresenceApp.LOW_SCAN_PERIOD);
            beaconManager.updateScanPeriods();
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isScanActivityActive=true;
    }
    @Override
    public void onPause() {
      super.onPause();
        isScanActivityActive=false;
    }

    // Regular inner class which act as the Adapter
    public class CustomAdapter extends BaseAdapter{
        private ArrayList<Beacon> innerClassdoBeaconUnfilteredArray;

        public CustomAdapter(ArrayList<Beacon> paradoBeaconUnfilteredArray) {
            Log.i(TAG, "*** 1 CustomAdapter constructor");
            innerClassdoBeaconUnfilteredArray = paradoBeaconUnfilteredArray;
        }

        // How many items are in the data set represented by this Adapter.
        @Override
        public int getCount() {
            return innerClassdoBeaconUnfilteredArray.size();
        }

        // Get the data item associated with the specified position in the data set.
        @Override
        public Object getItem(int position) {
            Log.i(TAG, "*** ? getItem method");
            Log.i(TAG, String.valueOf(innerClassdoBeaconUnfilteredArray.get(position)));
            return innerClassdoBeaconUnfilteredArray.get(position);
        }

        @Override
        public long getItemId(int position) {
            return PresenceApp.beaconAddressArray.indexOf(innerClassdoBeaconUnfilteredArray.get(position));
        }

        public void updateResults(ArrayList<Beacon> results) {
            for(int i=0;i<results.size();i++)
                if(!innerClassdoBeaconUnfilteredArray.contains(results.get(i)))
                    innerClassdoBeaconUnfilteredArray.add(results.get(i));
            //Triggers the list update
            notifyDataSetChanged();
        }

        // Get a View that displays the data at the specified position in the data set.
        // You can either create a View manually or inflate it from an XML layout file.
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            Log.i(TAG,"*** 4 getView method");
            Log.i(TAG, String.valueOf(position));

            if(convertView == null){
                // LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
                LayoutInflater layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                convertView = layoutInflater.inflate(R.layout.list_row, null);
            }
            doBeaconNameView = (TextView) convertView.findViewById(R.id.doBeaconName);
            doBeaconUUIDView = (TextView) convertView.findViewById(R.id.doBeaconUUID);
            doBeaconMajorView = (TextView) convertView.findViewById(R.id.doBeaconMajor);
            if (!innerClassdoBeaconUnfilteredArray.isEmpty()) {
                doBeaconNameView.setText(innerClassdoBeaconUnfilteredArray.get(position).getBluetoothName());
                doBeaconUUIDView.setText("UUID: " + String.valueOf(innerClassdoBeaconUnfilteredArray.get(position).getId1())
                        + "\nAddress: " + innerClassdoBeaconUnfilteredArray.get(position).getBluetoothAddress());
                doBeaconMajorView.setText("Major: " + String.valueOf(innerClassdoBeaconUnfilteredArray.get(position).getId2())
                        + "           Minor: " + String.valueOf(innerClassdoBeaconUnfilteredArray.get(position).getId3())
                        + "\nDistance: " + String.valueOf(innerClassdoBeaconUnfilteredArray.get(position).getDistance()) + " m");
                if(PresenceApp.beaconAddressArray.contains(innerClassdoBeaconUnfilteredArray.get(position).getBluetoothAddress())) {
                    convertView.setBackgroundColor(0x660000FF);
                    if(!doBeaconSelectedArray.contains(doBeaconUnfilteredArray.get(position))) doBeaconSelectedArray.add(doBeaconUnfilteredArray.get(position));
                    Log.i(TAG,"<--added beacon "+ doBeaconUnfilteredArray.get(position).getBluetoothName());
                }
                else {
                    convertView.setBackgroundColor(0x00000000);
                    if(doBeaconSelectedArray.contains(doBeaconUnfilteredArray.get(position)))doBeaconSelectedArray.remove(doBeaconUnfilteredArray.get(position));
                    Log.i(TAG, "-->remove beacon " + doBeaconUnfilteredArray.get(position).getBluetoothName());
                }
            }

            return convertView;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        Log.i(TAG,"clicked on item" + arg2);
        if(!PresenceApp.beaconAddressArray.contains(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress())) {
            PresenceApp.beaconAddressArray.add(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress());
            arg1.setBackgroundColor(0x660000FF);
        }
        else{
            doBeaconSelectedArray.remove(doBeaconUnfilteredArray.get(arg2));
            PresenceApp.beaconAddressArray.remove(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress());
            arg1.setBackgroundColor(0x00000000);
        }
    }

    public void initButtons(){
        final Button doneButton = (Button) findViewById(R.id.doneButton);
        doneButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onDoneClick();
            }
        });
    }

    public void onDoneClick(){
        //close this activity
        finish();
    }

    private void writePersistentSettings() {
        //store the settings in the Shared Preference file
        SharedPreferences settings = getSharedPreferences(PresenceApp.SETTING_FILE, 0);
        PresenceApp.password=settings.getString("passwordKey", PresenceApp.passwordDefault);
        PresenceApp.username=settings.getString("usernameKey", PresenceApp.usernameDefault);
        PresenceApp.server=settings.getString("serverKey", PresenceApp.serverDefault);
        PresenceApp.detectionDistance=settings.getFloat("detectionDistanceKey", PresenceApp.detectionDistanceDefault);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.putFloat("detectionDistanceKey", PresenceApp.detectionDistance);
        editor.putString("usernameKey", PresenceApp.username);
        editor.putString("passwordKey", PresenceApp.password);
        editor.putString("serverKey", PresenceApp.server);
        editor.putInt("doBeaconListSize", doBeaconSelectedArray.size());
        Log.i(TAG,"saved the "+ doBeaconSelectedArray.size()+" beacons selected");
        for (int i=0;i<doBeaconSelectedArray.size();i++)
        {
            editor.putString("beaconAdressKey"+ String.valueOf(i),doBeaconSelectedArray.get(i).getBluetoothAddress());
        }
        // Commit the edits!
        editor.commit();
    }
}
