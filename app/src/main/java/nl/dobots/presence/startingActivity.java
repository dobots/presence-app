package nl.dobots.presence;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import org.altbeacon.beacon.BeaconManager;

/**
 * Created by christian on 15/06/15.
 */
public class startingActivity extends Activity {
    protected static final String TAG = "startingActivity";
    public static boolean isSettingsActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starting);
        isSettingsActive = true;

        //make sure the user's phone is BLE compatible and has BLE enabled. Offers to turn it on otherwise.
        verifyBluetooth();

        //start buttons
        final Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onStopAppClick();
            }
        });
        final Button scanButton = (Button) findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onScanClick();
            }
        });
        final Button loginButton = (Button) findViewById(R.id.loginButton);
        loginButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onLoginClick();
            }
        });

        //start the distance detection seekbar
        final TextView distanceText = (TextView) findViewById(R.id.distance);
        distanceText.setText( String.valueOf(presenceApp.detectionDistance));
        SeekBar distanceBar= (SeekBar) findViewById(R.id.distanceBar);
        distanceBar.setProgress((int)presenceApp.detectionDistance*20);
        distanceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                presenceApp.detectionDistance= (float) ( progress / 20.0);
                distanceText.setText( String.valueOf(presenceApp.detectionDistance));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });


        Log.i(TAG, "Application just launched");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isSettingsActive=false;

        //store the settings in the Shared Preference file
        SharedPreferences settings = getSharedPreferences(presenceApp.SETTING_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.putFloat("detectionDistanceKey", presenceApp.detectionDistance);
        editor.putString("beaconUUIDKey", presenceApp.beaconUUID);
        editor.putString("beaconMajorKey",presenceApp.beaconMajor);
        editor.putString("beaconMinorKey",presenceApp.beaconMinor);
        editor.putString("usernameKey",presenceApp.username);
        editor.putString("passwordKey",presenceApp.password);
        // Commit the edits!
        editor.commit();
    }

    private void verifyBluetooth() {

        try {
            if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
                startActivityForResult( new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 0);
            }
        }
        catch (RuntimeException e) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not available");
            builder.setMessage("Sorry, this device does not support Bluetooth LE.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                    finish();
                    System.exit(0);
                }

            });
            builder.show();
        }

    }

    private void onStopAppClick() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("closing Presence");
        builder.setMessage("This will stop all background services and monitoring, until you restart the application.");
        builder.setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                killApp();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    private void killApp(){
        presenceApp.regionBootstrap.disable();
        final Intent beaconServiceIntent = new Intent(this, beaconService.class);
        this.stopService(beaconServiceIntent);
        finish();
        System.exit(0);
    }

    private void onScanClick(){
        final Intent intent = new Intent(this, beaconScanActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void onLoginClick() {

    }
}
