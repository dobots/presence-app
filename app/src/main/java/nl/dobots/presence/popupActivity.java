package nl.dobots.presence;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;


import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;

/**
 * Created by christian Haas-Frangi on 15/06/15.
 */
public class popupActivity extends Activity implements BeaconConsumer {
    protected static final String TAG = "popupActivity";
    private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_popup);

        //remove the notification if not clicked
        NotificationManager notificationManager;
        notificationManager = (NotificationManager) getSystemService(this.NOTIFICATION_SERVICE);
        notificationManager.cancel(1010);

        //initialize buttons
        final Button settingsButton = (Button) findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSettingsClick();
            }
        });
        final Button inButton = (Button) findViewById(R.id.inButton);
        inButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onComingInClick();
            }
        });
        final Button outButton = (Button) findViewById(R.id.outButton);
        outButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onLeavingClick();
            }
        });

        beaconManager.bind(this);
    }
    @Override
    protected void onDestroy() {
        beaconManager.unbind(this);
        super.onDestroy();
        try {
            for (int i=0; i<presenceApp.regionArray.size();i++)
                beaconManager.startRangingBeaconsInRegion(presenceApp.regionArray.get(i));
        } catch (RemoteException e) {
        }
    }
    @Override
    public void onBeaconServiceConnect() {
        //we don't care
    }

    public void onComingInClick(){


    }

    public void onLeavingClick(){

    }

    public void onSettingsClick(){
        final Intent intent = new Intent(this, startingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

}
