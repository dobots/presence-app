package nl.dobots.presence;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by christian Haas-Frangi on 15/06/15.
 */
public class presenceApp extends Application implements BootstrapNotifier {

    //stored parameters
    public static float detectionDistance = 1; // if the user is closer to the beacon than this distance, the popActivity shows. in meters.
    public static float currentDistance;
    public static ArrayList<Identifier> beaconUUIDArray = new ArrayList<Identifier>();
    public static ArrayList<Identifier> beaconMajorArray = new ArrayList<Identifier>();
    public static ArrayList<Identifier> beaconMinorArray = new ArrayList<Identifier>();
    public static ArrayList<String> beaconNameArray = new ArrayList<String>() ;
    public static String username;
    public static String password;
    public static final String SETTING_FILE="presenceSettingFile";

    //Default values for first loading ever
    final public static float detectionDistanceDefault = 1;
    final public static String beaconUUIDDefault= "";
    final public static String beaconMajorDefault="";
    final public static String beaconMinorDefault="";
    final public static String usernameDefault=null;
    final public static String passwordDefault=null;
    final public static String beaconNameDefault=null;

    //rest of the parameters
    private static final String TAG = ".presenceApp";
    public static RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconManager beaconManager;
    static public ArrayList<Region> regionArray = new ArrayList<Region>();
    static public Region noFilterRegion= new Region("noFilter", null, null, null);
    private boolean isScreenOn;
    public static ArrayList<Beacon> doBeaconArray= new ArrayList<Beacon>();
    public static String closestDoBeacon;

    @Override
    public void onCreate() {
        super.onCreate();
        readPersistentStorage(); // retrieve values from previous run
        Log.d(TAG, "App started up");
        beaconManager = BeaconManager.getInstanceForApplication(this);
        backgroundPowerSaver = new BackgroundPowerSaver(this); //enough to save up to 60% of battery consumption
        // wake up the app when any beacon is seen (you can specify specific id filers in the parameters below.Also wakes up the app when connected
        if (!beaconUUIDArray.isEmpty())
            for (int i = 0; i < beaconUUIDArray.size(); i++)
                regionArray.add(new Region("backgroundRegion"+String.valueOf(i), beaconUUIDArray.get(i), beaconMajorArray.get(i), beaconMinorArray.get(i)));
        else
            regionArray.add(noFilterRegion);

        regionBootstrap= new RegionBootstrap(this,regionArray);
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
                        currentDistance = (float) firstBeacon.getDistance();
                        closestDoBeacon = firstBeacon.getBluetoothName();
                        Log.i(TAG, "The first beacon " + firstBeacon.getBluetoothName() + " is about " + currentDistance + " meters away.");
                        Log.i(TAG, "beacons.size= " + String.valueOf(beacons.size()));
                        if (myScanActivity.isScanActivityActive){
                            for (int i=0;i<beacons.size();i++) {
                                if (!doBeaconArray.contains(firstBeacon))
                                    doBeaconArray.add(firstBeacon);
                                firstBeacon=beacons.iterator().next();
                            }
                        }
                        else {
                            if (currentDistance <= detectionDistance && !startingActivity.isSettingsActive) {
                                Log.i(TAG, "I am in range !");
                                triggerNotification("Hey! Are you going in or out?");
                                wakeScreen();
                                startActivity(intent);
                                try {
                                    for (int i=0; i<regionArray.size();i++)
                                        beaconManager.stopRangingBeaconsInRegion(regionArray.get(i));
                                } catch (RemoteException e) {
                                }
                            } else Log.i(TAG, "I am too far !");
                        }
                    }
                }
            });
        try {
            for (int i=0; i<regionArray.size();i++)
                beaconManager.startRangingBeaconsInRegion(regionArray.get(i));
        } catch (RemoteException e) {
        }
    }

    private void triggerNotification(String s) {
        final Intent intent = new Intent(this, popupActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
        CharSequence message = s;
        NotificationManager notificationManager;
        notificationManager = (NotificationManager) getSystemService(this.NOTIFICATION_SERVICE);
        //Play a notification sound
        Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Presence")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setLights(0xFF00FF00,500,1000)
                .build();
        notification.flags |= Notification.DEFAULT_LIGHTS | Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(1010, notification);
    }

    private void wakeScreen() {
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        // isScreenOn() is deprecated for API >=20
        if(android.os.Build.VERSION.SDK_INT>=20) {
            isScreenOn=pm.isInteractive();
        }
        else isScreenOn=pm.isScreenOn();
        if(!isScreenOn) {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK |PowerManager.ACQUIRE_CAUSES_WAKEUP |PowerManager.ON_AFTER_RELEASE,"MyLock");
            wl.acquire(10000);
            PowerManager.WakeLock wl_cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyCpuLock");
            wl_cpu.acquire(10000);
        }
        // Vibrate for 400 milliseconds
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(400);
    }

    private void readPersistentStorage() {
        SharedPreferences settings = getSharedPreferences(SETTING_FILE, 0);
        detectionDistance=settings.getFloat("detectionDistanceKey", detectionDistanceDefault);
        int doBeaconListSize =settings.getInt("doBeaconListSize",0);
        Log.i(TAG,"i= "+ String.valueOf(doBeaconListSize));
        if(doBeaconListSize>0) {
            for (int i = 0; i < settings.getInt("doBeaconListSize", 1); i++) {
                beaconUUIDArray.add(Identifier.parse(settings.getString("beaconUUIDKey" + String.valueOf(i), beaconUUIDDefault)));
                beaconMajorArray.add(Identifier.parse(settings.getString("beaconMajorKey" + String.valueOf(i), beaconMajorDefault)));
                beaconMinorArray.add(Identifier.parse(settings.getString("beaconMinorKey" + String.valueOf(i), beaconMinorDefault)));
                beaconNameArray.add(settings.getString("beaconNameKey" + String.valueOf(i), beaconNameDefault));
                Log.i(TAG,"added beacon " + beaconNameArray.get(i));
            }
        }
        username= settings.getString("usernameKey", usernameDefault);
        password= settings.getString("passwordKey", passwordDefault);
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
