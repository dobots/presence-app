package nl.dobots.presence;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import nl.dobots.presence.rest.RestApi;

/**
 * Created by christian Haas-Frangi on 15/06/15.
 */
public class presenceApp extends Application implements BootstrapNotifier {

    //stored parameters. We can't store an ArrayList<Beacon> so we go through this complicated stuff
    public static float detectionDistance = 1; // if the user is closer to the beacon than this distance, the popActivity shows. in meters.
    public static ArrayList<Identifier> beaconUUIDArray = new ArrayList<Identifier>();
    public static ArrayList<Identifier> beaconMajorArray = new ArrayList<Identifier>();
    public static ArrayList<Identifier> beaconMinorArray = new ArrayList<Identifier>();
    public static ArrayList<String> beaconNameArray = new ArrayList<String>();
    public static ArrayList<String> beaconAddressArray= new ArrayList<String>();
    public static String username;
    public static String password;
    public static final String SETTING_FILE="presenceSettingFile";

    //Default values for first loading ever
    final public static float detectionDistanceDefault = 1;
    final public static String beaconUUIDDefault= "";
    final public static String beaconMajorDefault="";
    final public static String beaconMinorDefault="";
    final public static String usernameDefault="";
    final public static String passwordDefault="";
    final public static String beaconNameDefault=null;
    final public static String beaconAddressDefault=null;


    //rest of the parameters
    private static final String TAG = presenceApp.class.getCanonicalName();
    public static RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconManager beaconManager;
    static public Region noFilterRegion= new Region("noFilter", null, null, null);
    private boolean isScreenOn;
    private String currentLocation;


    public static ArrayList<Beacon> doBeaconArray= new ArrayList<Beacon>(); // list of doBeacon we are scanning for
    public static ArrayList<Beacon> doBeaconUnfilteredArray = new ArrayList<Beacon>();
    public static String closestDoBeacon;
    public static String closestDoBeaconAddress;
    public static float currentDistance=-1;
    public static boolean isLoggedIn;
    public static String server = "http://dev.ask-cs.com"; //backend


    // Make sure to keep a reference to the rest API object to make sure it's not garbage collected
    public static final RestApi ra = RestApi.getInstance();

    @Override
    public void onCreate() {
        super.onCreate();
        readPersistentStorage(); // retrieve values from previous run
        Log.d(TAG, "App started up");
        beaconManager = BeaconManager.getInstanceForApplication(this);
        backgroundPowerSaver = new BackgroundPowerSaver(this); //enough to save up to 60% of battery consumption

        // wake up the app when any beacon is seen (you can specify specific id filers in the parameters below.Also wakes up the app when connected
        regionBootstrap= new RegionBootstrap(this,noFilterRegion);
        //Start the sticky service beaconService, which scans permanently for beacons, even when the app is closed.
        final Intent beaconServiceIntent = new Intent (this, beaconService.class);
        this.startService(beaconServiceIntent);
    }

    @Override
    public void didEnterRegion(Region arg0) { //called when the DoBeacon is seen. Used by the RegionBootstrap to start the app
        Log.d(TAG, "Got a didEnterRegion call");
        login();
        startTracking();
    }

    public void startTracking(){
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    Log.i(TAG, "beacons.size= " + String.valueOf(beacons.size()));
                    if (scanActivity.isScanActivityActive) { //generate the array of all doBeacons in range, to show in ScanActivity and let user select the one to track
                        generateDoBeaconArrayUnfiltered(beacons);
                    } else {
                        if (!beaconAddressArray.isEmpty()) {
                            generateDoBeaconArrayFiltered(beacons);
                            updateRange();
                        }
                        if (currentDistance !=-1 && currentDistance <= detectionDistance && !startingActivity.isSettingsActive && !loginActivity.isLoginActivityActive) {
                            onDetection();
                        } else Log.i(TAG, "I am too far ! or Settings is Active");
                    }
                }
            }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(noFilterRegion);
        } catch (RemoteException e) {
        }
    }

    public void updateRange(){
        if (!doBeaconArray.isEmpty()) {
            //initialize first range
            if(currentDistance==-1){
                currentDistance= (float) doBeaconArray.get(0).getDistance();
                closestDoBeacon= doBeaconArray.get(0).getBluetoothName();
                closestDoBeaconAddress=doBeaconArray.get(0).getBluetoothAddress();
            }
            //update distance last closest doBeacon
            for (int i=0; i<doBeaconArray.size();i++){
                if(doBeaconArray.get(i).getBluetoothAddress().equals(closestDoBeaconAddress))
                    currentDistance= (float)doBeaconArray.get(i).getDistance();
                    closestDoBeacon=doBeaconArray.get(i).getBluetoothName();
            }
            //check if any doBeacon is closer
            for (int i=0; i< doBeaconArray.size();i++)
                if (doBeaconArray.get(i).getDistance() < currentDistance) {
                    closestDoBeacon = doBeaconArray.get(i).getBluetoothName();
                    closestDoBeaconAddress = doBeaconArray.get(i).getBluetoothAddress();
                    currentDistance = (float) doBeaconArray.get(i).getDistance();
                }
            Log.i(TAG, "The first beacon " + closestDoBeacon + " is about " + currentDistance + " meters away.");
        }
    }

    public void generateDoBeaconArrayFiltered(Collection<Beacon> beacons){
        Iterator iterator = beacons.iterator();
        Beacon firstBeacon;
        doBeaconArray.clear();
        for (int i=0; i<beacons.size();i++) {
            firstBeacon = (Beacon) iterator.next();
            if (beaconAddressArray.contains(firstBeacon.getBluetoothAddress())){
                doBeaconArray.add(firstBeacon);
            }
        }
    }

    public void generateDoBeaconArrayUnfiltered(Collection<Beacon> beacons){
        Iterator iterator = beacons.iterator();
        Beacon firstBeacon;
        for (int i = 0; i < beacons.size(); i++) {
            firstBeacon = (Beacon) iterator.next();
            if (!doBeaconUnfilteredArray.contains(firstBeacon))
                doBeaconUnfilteredArray.add(firstBeacon);
        }
    }

    public void onDetection(){
        final Intent intent = new Intent(this, popupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.i(TAG, "I am in range !");
        triggerNotification("Hey! Are you going in or out?");
        //wakeScreen();
        try {
            if (isLoggedIn && !closestDoBeacon.equals(currentLocation)){
                ra.getStandByApi().setLocationPresenceManually(true, closestDoBeacon);
                currentLocation=closestDoBeacon
        }
            else
                login();
        }catch (Exception e) {
            e.printStackTrace();
        }
//        startActivity(intent);
//        try {
//            beaconManager.stopRangingBeaconsInRegion(noFilterRegion);
//        } catch (RemoteException e) {
//        }
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
                .setDefaults(Notification.DEFAULT_SOUND)
                .setLights(Color.BLUE, 500, 1000)
                .setAutoCancel(true)
                .build();
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
                beaconAddressArray.add(settings.getString("beaconAdressKey" + String.valueOf(i), beaconAddressDefault));
                Log.i(TAG,"added beacon " + beaconNameArray.get(i) + "with adress " + beaconAddressArray.get(i));
            }
        }
        username= settings.getString("usernameKey", usernameDefault);
        password= settings.getString("passwordKey", passwordDefault);
    }

    private void login(){
        if(!username.equals(usernameDefault) && !password.equals(passwordDefault) && !isLoggedIn) {
            try {
                ra.login(presenceApp.username, presenceApp.password, server, presenceApp.closestDoBeacon);
                if(ra.getStandByApi().setLocationPresenceManually(true, presenceApp.closestDoBeacon).get(0))
                    isLoggedIn=true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
