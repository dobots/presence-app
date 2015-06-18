package nl.dobots.presence;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import java.util.Collection;

/**
 * Created by christian Haas-Frangi on 15/06/15.
 */
public class presenceApp extends Application implements BootstrapNotifier {

    public static float detectionDistance = 1; // if the user is closer to the beacon than this distance, the popActivity shows. in meters.
    public static String beaconUUID= "12345678-1234-1234-1234-123456789abc";
    public static String beaconMajor="42";
    public static String beaconMinor="42";
    public static String username;
    public static String password;
    public static final String SETTING_FILE="presenceSettingFile";

    //Default values for first loading
    final private static float detectionDistanceDefault = 1;
    final private static String beaconUUIDDefault= "12345678-1234-1234-1234-123456789abc";
    final private static String beaconMajorDefault="42";
    final private static String beaconMinorDefault="42";
    final private static String usernameDefault="";
    final private static String passwordDefault="";

    private static final String TAG = ".presenceApp";
    public static RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconManager beaconManager;
    public Region region;

    private boolean isScreenOn;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "App started up");
        beaconManager= BeaconManager.getInstanceForApplication(this);

        //load settings
        SharedPreferences settings = getSharedPreferences(SETTING_FILE, 0);
        detectionDistance=settings.getFloat("detectionDistanceKey",detectionDistanceDefault);
        beaconUUID= settings.getString("beaconUUIDKey", beaconUUIDDefault);
        beaconMajor= settings.getString("beaconMajorKey", beaconMajorDefault);
        beaconMinor= settings.getString("beaconMinorKey", beaconMinorDefault);
        username= settings.getString("usernameKey", usernameDefault);
        password= settings.getString("passwordKey", passwordDefault);

        // wake up the app when any beacon is seen (you can specify specific id filers in the parameters below.Also wakes up the app when connected
        region = new Region("backgroundRegion", Identifier.parse(beaconUUID), Identifier.parse(beaconMajor), Identifier.parse(beaconMinor));
        backgroundPowerSaver = new BackgroundPowerSaver(this); //enough to save up to 60% of battery consumption
        regionBootstrap = new RegionBootstrap(this, region);

        //Start the sticky service beaconService, which scans permanently for beacons, even when the app is closed.
        final Intent beaconServiceIntent = new Intent (this, beaconService.class);
        this.startService(beaconServiceIntent);
    }

    @Override
    public void didEnterRegion(Region arg0) { //called when the DoBeacon is seen. Used by the RegionBootstrap to start the app
        Log.d(TAG, "Got a didEnterRegion call");
        final Intent intent = new Intent(this, popupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    Beacon firstBeacon = beacons.iterator().next();
                    Log.i(TAG, "The first beacon" + firstBeacon.toString() + " is about " + beacons.iterator().next().getDistance() + " meters away.");
                    if (beacons.iterator().next().getDistance() <= detectionDistance && !startingActivity.isSettingsActive) {
                        Log.i(TAG, "I am in range !");
                        triggerNotification("Hey! Are you going in or out?");
                        wakeScreen();
                        startActivity(intent);
                        try {
                            beaconManager.stopRangingBeaconsInRegion(region);
                        } catch (RemoteException e) {
                        }
                    } else Log.i(TAG, "I am too far !");
                }
            }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {}
    }

    private void triggerNotification(String s) {
        final Intent intent = new Intent(this, popupActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
        CharSequence message = s;
        NotificationManager notificationManager;
        notificationManager = (NotificationManager) getSystemService(this.NOTIFICATION_SERVICE);
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Presence")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent)
                .build();
        notification.flags |= Notification.DEFAULT_LIGHTS | Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(1010, notification);
    }

    private void wakeScreen() {
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        // isScreenOn() is deprecated for API >=20
        if(android.os.Build.VERSION.SDK_INT>=20)
        {
            isScreenOn=pm.isInteractive();
        }
        else isScreenOn=pm.isScreenOn();
        if(!isScreenOn)
        {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |PowerManager.ACQUIRE_CAUSES_WAKEUP |PowerManager.ON_AFTER_RELEASE,"MyLock");
            wl.acquire(10000);
            PowerManager.WakeLock wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyCpuLock");
            wl_cpu.acquire(10000);
        }
        // Vibrate for 400 milliseconds
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(400);
    }

    @Override
    public void didDetermineStateForRegion(int arg0, Region arg1) {
        // Don't care
    }

    @Override
    public void didExitRegion(Region arg0) {
        // Don't care
    }
}
