package nl.dobots.presence;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
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
    protected static final String TAG = startingActivity.class.getCanonicalName();
    public static boolean isSettingsActive;
    final Handler handler = new Handler();
    private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starting);
        isSettingsActive = true;
        //make sure the user's phone is BLE compatible and has BLE enabled. Offers to turn it on otherwise.
        verifyBluetooth();

        initUI();

        Log.i(TAG, "Application just launched");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isSettingsActive=false;
        writePersistentSettings();
        final Intent restartAppIntent = new Intent (this, PresenceApp.class);
        this.startService(restartAppIntent);

    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyBluetooth();
        initUI();
        isSettingsActive=true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isSettingsActive=false;
        writePersistentSettings();
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

    private void writePersistentSettings() {
        //store the settings in the Shared Preference file
        SharedPreferences settings = getSharedPreferences(PresenceApp.SETTING_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove("detectionDistanceKey");
        editor.putFloat("detectionDistanceKey", PresenceApp.detectionDistance);
        editor.commit();
    }

    public void updateCurrentDistance(){
        final TextView currentDistanceText = (TextView) findViewById(R.id.currentDistanceHint);
        if (!PresenceApp.beaconAddressArray.isEmpty() && PresenceApp.beaconAddressArray.contains(PresenceApp.closestDoBeacon.getBluetoothAddress()))
            currentDistanceText.setText("closest DoBeacon " + PresenceApp.closestDoBeacon.getBluetoothName() + ": " + String.valueOf(PresenceApp.closestDoBeacon.getDistance()) + "m");
        else
            if(PresenceApp.beaconAddressArray.isEmpty())
                currentDistanceText.setText("Please select your Dobeacons !");
            else
                currentDistanceText.setText("loading...");
    }

    private void initUI(){
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
        final Button clearSettingsButton= (Button) findViewById(R.id.clearSettingsButton);
        clearSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClearSettingsClick();
            }
        });
        //start seekBar and distance related texts
        final TextView detectionDistanceText = (TextView) findViewById(R.id.detectionDistance);
        detectionDistanceText.setText(String.valueOf(PresenceApp.detectionDistance) + "m");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateCurrentDistance();
                if(isSettingsActive)handler.postDelayed(this, 500);
            }
        }, 1000);
        SeekBar distanceBar= (SeekBar) findViewById(R.id.distanceBar);
        distanceBar.setProgress((int) PresenceApp.detectionDistance*5);
        distanceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                PresenceApp.detectionDistance= (float) ( progress / 5.0);
                detectionDistanceText.setText(String.valueOf(PresenceApp.detectionDistance) + "m");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void onStopAppClick() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("closing Presence");
        builder.setMessage("This will stop all background services and monitoring, until you restart the application.");
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
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
        PresenceApp.regionBootstrap.disable();
        final Intent beaconServiceIntent = new Intent(this, beaconService.class);
        this.stopService(beaconServiceIntent);
        finish();
        System.exit(0);
    }

    private void clearSettings(){
        SharedPreferences settings = getSharedPreferences(PresenceApp.SETTING_FILE, 0);
        settings.edit().clear().commit();
        PresenceApp.buildClosestBeacon();
        PresenceApp.doBeaconArray.clear();
        PresenceApp.beaconAddressArray.clear();
        PresenceApp.password= PresenceApp.passwordDefault;
        PresenceApp.username= PresenceApp.usernameDefault;
        PresenceApp.detectionDistance= PresenceApp.detectionDistanceDefault;
        initUI();
    }

    private void onScanClick(){
        final Intent intent = new Intent(this, scanActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void onClearSettingsClick(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("removing settings");
        builder.setMessage("Your stored settings will be removed. You will have to reconfigure it !");
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                clearSettings();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        builder.show();
    }

    private void onLoginClick() {
        final Intent loginIntent = new Intent(this,loginActivity.class);
        loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(loginIntent);
    }
}
