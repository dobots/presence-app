package nl.dobots.presence;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.altbeacon.beacon.BeaconManager;

/**
 * Created by christian on 15/06/15.
 */
public class startingActivity extends Activity {
    protected static final String TAG = "startingActivity";
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

        //start the distance detection seekbar
        initUI();

        Log.i(TAG, "Application just launched");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isSettingsActive=false;
        writePersistentSettings();

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
        SharedPreferences settings = getSharedPreferences(presenceApp.SETTING_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.putFloat("detectionDistanceKey", presenceApp.detectionDistance);
        editor.putString("beaconUUIDKey", presenceApp.beaconUUID);
        if(presenceApp.beaconName!= null) editor.putString("beaconNameKey",presenceApp.beaconName);
        editor.putString("beaconMajorKey",presenceApp.beaconMajor);
        editor.putString("beaconMinorKey",presenceApp.beaconMinor);
        editor.putString("usernameKey",presenceApp.username);
        editor.putString("passwordKey",presenceApp.password);
        // Commit the edits!
        editor.commit();
    }

    public void updateCurrentDistance(){
        final TextView currentDistanceText = (TextView) findViewById(R.id.currentDistance);
        if (presenceApp.beaconName!=null)
            currentDistanceText.setText("current distance from " + presenceApp.beaconName + ": " + String.valueOf(presenceApp.currentDistance) + "m");
        else
            currentDistanceText.setText("please select your DoBeacon.");
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
        final Button readyButton=(Button) findViewById(R.id.readyButton);
        readyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onReadyClick();
            }
        });
        //start seekBar and distance related texts
        final TextView detectionDistanceText = (TextView) findViewById(R.id.detectionDistance);
        detectionDistanceText.setText(String.valueOf(presenceApp.detectionDistance) + "m");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateCurrentDistance();
                if(isSettingsActive)handler.postDelayed(this, 3000);
            }
        }, 1000);
        SeekBar distanceBar= (SeekBar) findViewById(R.id.distanceBar);
        distanceBar.setProgress((int)presenceApp.detectionDistance*10);
        distanceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                presenceApp.detectionDistance= (float) ( progress / 10.0);
                detectionDistanceText.setText(String.valueOf(presenceApp.detectionDistance) + "m");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        //login stuff
        if(presenceApp.username!=null && presenceApp.password!=null ) {
            EditText username = (EditText) findViewById(R.id.username);
            EditText password = (EditText) findViewById(R.id.password);
            username.setText(presenceApp.username);
            password.setText(presenceApp.password);
        }
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
        presenceApp.regionBootstrap.disable();
        final Intent beaconServiceIntent = new Intent(this, beaconService.class);
        this.stopService(beaconServiceIntent);
        finish();
        System.exit(0);
    }

    private void clearSettings(){
        SharedPreferences settings = getSharedPreferences(presenceApp.SETTING_FILE, 0);
        settings.edit().clear().commit();
        presenceApp.beaconName=presenceApp.beaconNameDefault;
        presenceApp.beaconUUID=presenceApp.beaconUUIDDefault;
        presenceApp.beaconMajor=presenceApp.beaconMajorDefault;
        presenceApp.beaconMinor=presenceApp.beaconMinorDefault;
        presenceApp.username=presenceApp.usernameDefault;
        presenceApp.password=presenceApp.passwordDefault;
        presenceApp.detectionDistance=presenceApp.detectionDistanceDefault;
        presenceApp.currentDistance=0;
        killApp();
    }

    private void onScanClick(){
        if(presenceApp.beaconName==null) {
            final Intent intent = new Intent(this, myScanActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        else {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("doBeacon already set");
            builder.setMessage("You have already selected a DoBeacon.delete all settings? (you will have a to restart the app)");
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
    }

    private void onClearSettingsClick(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("removing settings");
        builder.setMessage("Your stored settings will be removed. You will have to restart the app and reconfigure it !");
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

    private void onReadyClick(){
        if(presenceApp.beaconName==null)
            Toast.makeText(getApplicationContext(), "You need to select your DoBeacon!", Toast.LENGTH_LONG).show();
        else {
            finish();
            try {
                beaconManager.startRangingBeaconsInRegion(presenceApp.region);
            } catch (RemoteException e) {
            }
        }
    }

    private void onLoginClick() {
        EditText username=(EditText) findViewById(R.id.username);
        EditText password=(EditText) findViewById(R.id.password);
        if(username.getText().toString().length()!=0) {
            presenceApp.username = username.getText().toString();
            presenceApp.password = password.getText().toString();
            Log.i(TAG, "set username to " + presenceApp.username);
            Toast.makeText(getApplicationContext(), "Welcome " + presenceApp.username + " !", Toast.LENGTH_LONG).show();
        }
        else Toast.makeText(getApplicationContext(), "Please enter your credentials.", Toast.LENGTH_LONG).show();
    }
}
