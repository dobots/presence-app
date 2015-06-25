package nl.dobots.presence;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
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
import org.altbeacon.beacon.Region;

/**
 * Created by christian Haas-Frangi on 18/06/15.
 */
public class scanActivity extends Activity implements OnItemClickListener {
    private ListView doBeaconListView;
    private TextView doBeaconNameView;
    private TextView doBeaconUUIDView;
    private TextView doBeaconMajorView;
    protected static final String TAG = scanActivity.class.getCanonicalName();
    private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);
    final Handler handler = new Handler();
    static public boolean isScanActivityActive;
    private boolean isLoadingOn;
    public  ProgressDialog progress;
    public CustomAdapter customAdapter;
    public ArrayList<Beacon> doBeaconUnfilteredArray = new ArrayList<Beacon>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_listview);
        isScanActivityActive=true;
        doBeaconUnfilteredArray=presenceApp.doBeaconArray;
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
        final Intent restartAppIntent = new Intent (this, presenceApp.class);
        this.startService(restartAppIntent);
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
            Log.i(TAG, "*** 2 getCount method");
            Log.i(TAG, String.valueOf(innerClassdoBeaconUnfilteredArray.size()));
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
            return position;
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
                +"\nAddress: "+ innerClassdoBeaconUnfilteredArray.get(position).getBluetoothAddress());
                doBeaconMajorView.setText("Major: " + String.valueOf(innerClassdoBeaconUnfilteredArray.get(position).getId2())
                        + "           Minor: " + String.valueOf(innerClassdoBeaconUnfilteredArray.get(position).getId3())
                        + "\nDistance: " + String.valueOf(innerClassdoBeaconUnfilteredArray.get(position).getDistance()) + " m");
                if(presenceApp.beaconAddressArray.contains(innerClassdoBeaconUnfilteredArray.get(position).getBluetoothAddress()))
                    convertView.setBackgroundColor(0x660000FF);
            }

            return convertView;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        if(!presenceApp.beaconUUIDArray.contains(doBeaconUnfilteredArray.get(arg2).getId1())) {
            presenceApp.beaconUUIDArray.add(doBeaconUnfilteredArray.get(arg2).getId1());
            presenceApp.beaconMajorArray.add(doBeaconUnfilteredArray.get(arg2).getId2());
            presenceApp.beaconMinorArray.add(doBeaconUnfilteredArray.get(arg2).getId3());
            presenceApp.beaconNameArray.add(doBeaconUnfilteredArray.get(arg2).getBluetoothName());
            presenceApp.beaconAddressArray.add(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress());
            presenceApp.currentDistance= (float) doBeaconUnfilteredArray.get(arg2).getDistance();
            arg1.setBackgroundColor(0x660000FF);
        }
        else{
            presenceApp.beaconUUIDArray.remove(doBeaconUnfilteredArray.get(arg2).getId1());
            presenceApp.beaconMajorArray.remove(doBeaconUnfilteredArray.get(arg2).getId2());
            presenceApp.beaconMinorArray.remove(doBeaconUnfilteredArray.get(arg2).getId3());
            presenceApp.beaconNameArray.remove(doBeaconUnfilteredArray.get(arg2).getBluetoothName());
            presenceApp.beaconAddressArray.remove(doBeaconUnfilteredArray.get(arg2).getBluetoothAddress());
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
        SharedPreferences settings = getSharedPreferences(presenceApp.SETTING_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        for (int i=0;i<settings.getInt("doBeaconListSize", 0);i++)
        {
            editor.remove("beaconUUIDKey" + String.valueOf(i));
            editor.remove("beaconMajorKey" + String.valueOf(i));
            editor.remove("beaconMinorKey" + String.valueOf(i));
            editor.remove("beaconNameKey" + String.valueOf(i));
            editor.remove("beaconAdressKey" + String.valueOf(i));
        }
        editor.remove("doBeaconListSize");
        editor.putInt("doBeaconListSize", presenceApp.beaconAddressArray.size());
        for (int i=0;i<presenceApp.beaconAddressArray.size();i++)
        {
            editor.putString("beaconUUIDKey" + String.valueOf(i), presenceApp.beaconUUIDArray.get(i).toString());
            editor.putString("beaconMajorKey" + String.valueOf(i), presenceApp.beaconMajorArray.get(i).toString());
            editor.putString("beaconMinorKey" + String.valueOf(i), presenceApp.beaconMinorArray.get(i).toString());
            editor.putString("beaconNameKey" + String.valueOf(i), presenceApp.beaconNameArray.get(i));
            editor.putString("beaconAdressKey"+ String.valueOf(i),presenceApp.beaconAddressArray.get(i));
        }
        // Commit the edits!
        editor.commit();
    }
}
