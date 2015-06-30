package nl.dobots.presence;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;

import java.nio.charset.Charset;

/**
 * Created by christian Haas-Frangi on 15/06/15.
 */
public class popupActivity extends Activity implements BeaconConsumer {
    protected static final String TAG = popupActivity.class.getCanonicalName();
//    private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_popup);

        //remove the notification if not clicked
//        NotificationManager notificationManager;
//        notificationManager = (NotificationManager) getSystemService(this.NOTIFICATION_SERVICE);
//        notificationManager.cancel(1010);

        Intent intent = getIntent();
        CharSequence beaconName = intent.getCharSequenceExtra("nl.dobots.presence.BEACON_NAME");

        TextView popupDescription = (TextView) findViewById(R.id.popupDescription);
        popupDescription.setText(String.format("You walked past %s.\nAre you going in or out?", beaconName));

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

//        beaconManager.bind(this);
    }
    @Override
    protected void onDestroy() {
//        beaconManager.unbind(this);
        super.onDestroy();
//        final Intent restartAppIntent = new Intent (this, PresenceApp.class);
//        this.startService(restartAppIntent);
    }
    @Override
    public void onBeaconServiceConnect() {
        //we don't care
    }

    public void onComingInClick(){
        PresenceApp.INSTANCE.updatePresence(true);
        finish();
    }

    public void onLeavingClick(){
        PresenceApp.INSTANCE.updatePresence(false);
        finish();
    }

    public void onSettingsClick(){
        final Intent intent = new Intent(this, startingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

}
