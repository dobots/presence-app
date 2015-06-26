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
    public static ArrayList<String> beaconAddressArray= new ArrayList<String>();
    public static String username;
    public static String password;
    public static String server; //backend
    public static final String SETTING_FILE="presenceSettingFile";

    //Default values for first loading ever
    final public static float detectionDistanceDefault = 1;
    final public static String usernameDefault="";
    final public static String passwordDefault="";
    final public static String serverDefault="http://dev.ask-cs.com";
    final public static String beaconAddressDefault=null;

    private static final String TAG = presenceApp.class.getCanonicalName();
    public static RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconManager beaconManager;
    static public Region noFilterRegion= new Region("noFilter", null, null, null);
    private boolean isScreenOn;

    public static ArrayList<Beacon> doBeaconArray= new ArrayList<Beacon>(); // list of doBeacon we are scanning for
    public Beacon firstBeacon;

    public static Beacon closestDoBeacon;
    public static String initClosestDoBeaconAddress="FI:RS:TA:DD:RE:SS";
    public static String initClosestDoBeaconName="just came in";
    public boolean closestDoBeaconExists;

    public static float currentDistance=-1;
    public static boolean isLoggedIn;
    public String currentLocation;

    // Make sure to keep a reference to the rest API object to make sure it's not garbage collected
    public static final RestApi ra = RestApi.getInstance();

    @Override
    public void onCreate() {
        super.onCreate();
        readPersistentStorage(); // retrieve values from previous run
        buildClosestBeacon();
        Log.d(TAG, "App started up");
        beaconManager = BeaconManager.getInstanceForApplication(this);
        backgroundPowerSaver = new BackgroundPowerSaver(this); //enough to save up to 60% of battery consumption

        // wake up the app when any beacon is seen (you can specify specific id filers in the parameters below.Also wakes up the app when connected
        regionBootstrap= new RegionBootstrap(this,noFilterRegion);
        //Start the sticky service beaconService, which scans permanently for beacons, even when the app is closed.
        final Intent beaconServiceIntent = new Intent (this, beaconService.class);
        this.startService(beaconServiceIntent);
    }

    public static void buildClosestBeacon(){
        closestDoBeacon = new Beacon.Builder()
                .setBluetoothName(initClosestDoBeaconName
                )
                .setBluetoothAddress(initClosestDoBeaconAddress)
                .build();
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
                        if (!closestDoBeacon.getBluetoothAddress().equals(initClosestDoBeaconAddress) && currentDistance <= detectionDistance && !startingActivity.isSettingsActive && !loginActivity.isLoginActivityActive) {
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
            if(closestDoBeacon.getBluetoothAddress().equals(initClosestDoBeaconAddress)){
                closestDoBeacon= doBeaconArray.get(0);
            }
            //update distance of the last closest doBeacon
            closestDoBeaconExists=false;
            for (int i=0; i<doBeaconArray.size();i++){
                if(doBeaconArray.get(i).equals(closestDoBeacon)) {
                    closestDoBeacon = doBeaconArray.get(i); // no it's not useless, it updates the Rssi !
                    closestDoBeaconExists=true;
                }
            }
            //if the closest beacon disappeared, get the new closest one
            if(!closestDoBeaconExists) {
                Beacon closestDoBeacon= new Beacon.Builder()
                        .setBluetoothName("dummy")
                        .setBluetoothAddress("RA:ND:OM:AD:DR:ES")
                        .setRssi(-2000)
                        .build();;
                for (int i = 0; i < doBeaconArray.size(); i++)
                    if (beaconAddressArray.contains(doBeaconArray.get(i).getBluetoothAddress()) && doBeaconArray.get(i).getDistance() < closestDoBeacon.getDistance())
                        closestDoBeacon = doBeaconArray.get(i);
            }
            //otherwise check if any doBeacon is closer
            else {
                for (int i = 0; i < doBeaconArray.size(); i++) {
                    Log.i(TAG, "I am at " + String.valueOf(doBeaconArray.get(i).getDistance()) + "m "
                            + "which corresponds to " + String.valueOf(doBeaconArray.get(i).getRssi()) + "dBm from "
                            + doBeaconArray.get(i).getBluetoothName() + " " + doBeaconArray.get(i).getBluetoothAddress());
                    if (doBeaconArray.get(i).getDistance() < closestDoBeacon.getDistance()) {
                        closestDoBeacon = doBeaconArray.get(i);
                        Log.i(TAG, "updating distance from" + closestDoBeacon.getBluetoothName() + " at " + closestDoBeacon.getDistance() + "m.");
                    }
                }
                Log.i(TAG, "The first beacon " + closestDoBeacon.getBluetoothName() + " is about " + closestDoBeacon.getDistance() + " meters away.");
            }
        }
        else { // if none of the selected doBeacons are in range, it means we left the area
            logout();
            buildClosestBeacon();
        }
    }

    public void generateDoBeaconArrayFiltered(Collection<Beacon> beacons){
        Iterator iterator = beacons.iterator();
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
            if (!doBeaconArray.contains(firstBeacon))
                doBeaconArray.add(firstBeacon);
        }
    }

    public void onDetection(){
        Log.i(TAG, "I am in range of " + closestDoBeacon.getBluetoothName()+" !");
        updatePosition();
        triggerNotification(currentLocation + " noticed you");
    }

    private void updatePosition(){
        try {
            //we only send the new position if it differs from the old one, to avoid surcharging the server
            if (isLoggedIn && !closestDoBeacon.getBluetoothName().equals(currentLocation)){
                ra.getStandByApi().setLocationPresenceManually(true, closestDoBeacon.getBluetoothName());
                currentLocation=closestDoBeacon.getBluetoothName();
            }
            else
                login();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerNotification(String s) {
        final Intent intent = new Intent(this, startingActivity.class);
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

    //not used anymore
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
        username= settings.getString("usernameKey", usernameDefault);
        password= settings.getString("passwordKey", passwordDefault);
        server= settings.getString("serverKey",serverDefault);
        int doBeaconListSize =settings.getInt("doBeaconListSize",0);
        Log.i(TAG, "i= " + String.valueOf(doBeaconListSize));
        if(doBeaconListSize>0) {
            for (int i = 0; i < settings.getInt("doBeaconListSize", 1); i++) {
                beaconAddressArray.add(settings.getString("beaconAdressKey" + String.valueOf(i), beaconAddressDefault));
                Log.i(TAG,"added beacon with adress " + beaconAddressArray.get(i));
            }
        }
    }

    private void login(){
        if(!username.equals(usernameDefault) && !password.equals(passwordDefault) && !isLoggedIn) {
            try {
                ra.login(presenceApp.username, presenceApp.password, server, closestDoBeacon.getBluetoothName());
                if(ra.getStandByApi().setLocationPresenceManually(true, closestDoBeacon.getBluetoothName()).get(0))
                    isLoggedIn=true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void logout(){
        if(isLoggedIn){
            ra.logout();
            isLoggedIn=false;
        }
    }

    @Override
    public void didDetermineStateForRegion(int arg0, Region arg1) {
        // Don't care
    }

    @Override
    public void didExitRegion(Region arg0) {
        //we can do something here like logout or set absent but I don't know the function for
        logout();
    }
}
