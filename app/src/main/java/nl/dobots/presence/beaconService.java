package nl.dobots.presence;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;

import nl.dobots.presence.rest.RestApi;

/**
 * Created by christian Haas-Frangi on 16/06/15.
 */
public class beaconService extends Service {
    private BeaconManager beaconManager;
    private String TAG = beaconService.class.getCanonicalName();

    //BLE Adapter to remember user not to turn BLE off
    BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // It means the user has changed his bluetooth state.
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    triggerNotification("Presence can not work without BLE ! Please turn the bluetooth back on when you want to check in or out.");
                    return;
                }
            }
        }
    };
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "starting beacon service");
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        beaconManager = BeaconManager.getInstanceForApplication(this);
        try {
            beaconManager.setBackgroundBetweenScanPeriod(0);
            beaconManager.setForegroundBetweenScanPeriod(0);
            beaconManager.setBackgroundScanPeriod(3000);
            beaconManager.setForegroundScanPeriod(3000);
            beaconManager.updateScanPeriods();
        }
        catch (RemoteException e) { }

        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void triggerNotification(String s) {
        final Intent intent = new Intent(this, startingActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
        CharSequence message = s;
        NotificationManager notificationManager;
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Presence")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setLights(Color.BLUE, 500, 1000)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(1011, notification);
    }

}
