package nl.dobots.presence;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.service.RangedBeacon;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import nl.dobots.presence.rest.RestApi;
import retrofit.RetrofitError;

/**
 * Created by christian Haas-Frangi on 15/06/15.
 */
public class PresenceApp extends Application implements BootstrapNotifier {

    private static final String TAG = PresenceApp.class.getCanonicalName();

    public static final int PRESENCE_NOTIFICATION_ID = 1010;

    public static final String EXTRAS_BEACON_NAME = "nl.dobots.presence.BEACON_NAME";
    public static final String PRESENCE_UPDATE_PRESENT = "nl.dobots.presence.PRESENT";
    public static final String PRESENCE_UPDATE_ABSENT = "nl.dobots.presence.ABSENT";
    private static final String PRESENCE_UPDATE_DISMISS = "nl.dobots.presence.DISMISS";

    public final static String SETTING_FILE="presenceSettingFile";

    public static final long TIMEOUT = 30000; // timeout in ms before the next presence update notification will trigger

    public static final int LOW_SCAN_PAUSE = 2500;
    public static final int LOW_SCAN_PERIOD = 500;
    public static final int LOW_SCAN_EXPIRATION = 3500;

    public static final int HIGH_SCAN_PAUSE = 500;
    public static final int HIGH_SCAN_PERIOD = 500;
    public static final int HIGH_SCAN_EXPIRATION = 2000;

    public static final float HIGH_FREQUENCY_DISTANCE = 6;
    public static final float LOW_FREQUENCY_DISTANCE = 10;

    public float highFrequencyDistance = HIGH_FREQUENCY_DISTANCE;
    public float lowFrequencyDistance = LOW_FREQUENCY_DISTANCE;

    // Variables

    //stored parameters. We can't store an ArrayList<Beacon> so we go through this complicated stuff
    public float detectionDistance = 1; // if the user is closer to the beacon than this distance, the popActivity shows. in meters.
    public ArrayList<String> beaconAddressArray= new ArrayList<String>();
    public String username;
    public String password;
    public String server; //backend

    //Default values for first loading ever
    final public float detectionDistanceDefault = 1;

    final public String usernameDefault="";
    final public String passwordDefault="";
    final public String serverDefault="http://dev.ask-cs.com";
    final public String beaconAddressDefault=null;

    public RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconManager beaconManager;
    static public Region noFilterRegion= new Region("noFilter", null, null, null);
    private boolean isScreenOn;

    public ArrayList<Beacon> doBeaconArray= new ArrayList<Beacon>(); // list of doBeacon we are scanning for
    public Beacon firstBeacon;

    public Beacon closestDoBeacon;
    public String initClosestDoBeaconAddress="FI:RS:TA:DD:RE:SS";
    public String initClosestDoBeaconName="just came in";

    //    public static float currentDistance=-1;
    private boolean isLoggedIn;

    private String currentLocation;
    private boolean currentPresence = false;

    private boolean updatingPresence = false;

    // Make sure to keep a reference to the rest API object to make sure it's not garbage collected
    public final RestApi ra = RestApi.getInstance();

    private Handler mNetworkHandler;

    public static PresenceApp INSTANCE;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder _builder;

    public boolean mHighFrequencyDetection = false;
    private boolean retry;

    @Override
    public void onCreate() {
        super.onCreate();

        INSTANCE = this;

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

        notificationManager = (NotificationManager) getSystemService(this.NOTIFICATION_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(PRESENCE_UPDATE_PRESENT);
        filter.addAction(PRESENCE_UPDATE_ABSENT);
        filter.addAction(PRESENCE_UPDATE_DISMISS);
        registerReceiver(receiver, filter);

        HandlerThread networkHandlerThread = new HandlerThread("NetworkHandlerThread");
        networkHandlerThread.start();
        mNetworkHandler = new Handler(networkHandlerThread.getLooper());

        RangedBeacon.setSampleExpirationMilliseconds(LOW_SCAN_EXPIRATION);
    }

    public void buildClosestBeacon(){
        closestDoBeacon = new Beacon.Builder()
                .setBluetoothName(initClosestDoBeaconName
                )
                .setBluetoothAddress(initClosestDoBeaconAddress)
                .build();
    }
    @Override
    public void didEnterRegion(Region arg0) { //called when the DoBeacon is seen. Used by the RegionBootstrap to start the app
        Log.d(TAG, "Got a didEnterRegion call");
//        login();
        startTracking();
    }

    public void setHighFrequencyDetection(boolean enable) {
        if (mHighFrequencyDetection == enable) return;

        try {
            if (enable) {
                Log.i(TAG, "set scan frequency to high");
                beaconManager.setBackgroundBetweenScanPeriod(PresenceApp.HIGH_SCAN_PAUSE);
                beaconManager.setForegroundBetweenScanPeriod(PresenceApp.HIGH_SCAN_PAUSE);
                beaconManager.setBackgroundScanPeriod(HIGH_SCAN_PERIOD);
                beaconManager.setForegroundScanPeriod(HIGH_SCAN_PERIOD);
                beaconManager.updateScanPeriods();
                RangedBeacon.setSampleExpirationMilliseconds(HIGH_SCAN_EXPIRATION);
            } else {
                Log.i(TAG, "set scan frequency to low");
                beaconManager.setBackgroundBetweenScanPeriod(PresenceApp.LOW_SCAN_PAUSE);
                beaconManager.setForegroundBetweenScanPeriod(PresenceApp.LOW_SCAN_PAUSE);
                beaconManager.setBackgroundScanPeriod(LOW_SCAN_PERIOD);
                beaconManager.setForegroundScanPeriod(LOW_SCAN_PERIOD);
                beaconManager.updateScanPeriods();
                RangedBeacon.setSampleExpirationMilliseconds(LOW_SCAN_EXPIRATION);
            }
            mHighFrequencyDetection = enable;
        } catch (RemoteException e) { }
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
                        if (!closestDoBeacon.getBluetoothAddress().equals(initClosestDoBeaconAddress) && closestDoBeacon.getDistance() <= detectionDistance && !loginActivity.isLoginActivityActive) {
                            onDetection();
                        } else Log.i(TAG, "I am too far ! or Settings is Active");
                    }
                } else {
//                    setHighFrequencyDetection(false);
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
            boolean closestDoBeaconExists = false;
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
                        .build();
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
//            logout();
            buildClosestBeacon();
        }

        mNetworkHandler.post(new Runnable() {
            @Override
            public void run() {
                if (closestDoBeacon.getDistance() < highFrequencyDistance) {
                    setHighFrequencyDetection(true);
                } else if (closestDoBeacon.getDistance() > lowFrequencyDistance) {
                    setHighFrequencyDetection(false);
                }
            }
        });
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

    long lastDetectionTime = 0;
    long lastPresenceUpdateTime = 0;

    public void onDetection(){

//        if (System.currentTimeMillis() - lastDetectionTime > TIMEOUT) {
        if (!updatingPresence && System.currentTimeMillis() - lastPresenceUpdateTime > TIMEOUT) {
            Log.i(TAG, "I am in range of " + closestDoBeacon.getBluetoothName() + " !");
            triggerNotification(closestDoBeacon.getBluetoothName());
//            lastDetectionTime = System.currentTimeMillis();
            updatingPresence = true;
            currentLocation = closestDoBeacon.getBluetoothName();
        }
    }

    public void updatePresence(final boolean present) {

        // can't execute network operations in the main thread, so we have to delegate
        // the call to the network handler
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mNetworkHandler.post(new Runnable() {
                @Override
                public void run() {
                   updatePresence(present);
                }
            });
            return;
        }

        try {
            //we only send the new position if it differs from the old one, to avoid surcharging the server
            if (!isLoggedIn()) {
                login();

                if (!isLoggedIn()) {
                    Log.e(TAG, "failed to log in");
                    _builder.setContentText("Can't login, please check your internet!")
                            .setStyle(new NotificationCompat.BigTextStyle().bigText("Can't login, please check your internet!"));
                    notificationManager.notify(PRESENCE_NOTIFICATION_ID, _builder.build());
                    Toast.makeText(this, "Can't login, please check your internet!", Toast.LENGTH_LONG);
                    return;
                }
            }
            if (isLoggedIn() && (currentPresence != present)) {
                ra.getStandByApi().setLocationPresenceManually(present, currentLocation);
                currentPresence = present;
            }
            updatingPresence = false;
            lastPresenceUpdateTime = System.currentTimeMillis();
            notificationManager.cancel(PRESENCE_NOTIFICATION_ID);
            retry = false;
        } catch (RetrofitError e) {
            e.printStackTrace();
            isLoggedIn = false;
            if (!retry) {
                updatePresence(present);
                retry = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerNotification(String beaconName) {
        final Intent intent = new Intent(this, popupActivity.class);
        intent.putExtra(EXTRAS_BEACON_NAME, beaconName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);

        final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        CharSequence shortMessage = beaconName + " noticed you";
        CharSequence longMessage = String.format("You walked past %s.\nAre you going in or out?", beaconName);

        Intent presentIntent = new Intent(PRESENCE_UPDATE_PRESENT);
        presentIntent.putExtra(EXTRAS_BEACON_NAME, beaconName);
//        presentIntent.putExtra("nl.dobots.presence.NOTIFICATION_ANSWER", true);
        PendingIntent piPresent = PendingIntent.getBroadcast(this, 0, presentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent absentIntent = new Intent(PRESENCE_UPDATE_ABSENT);
        absentIntent.putExtra(EXTRAS_BEACON_NAME, beaconName);
//        absentIntent.putExtra("nl.dobots.presence.NOTIFICATION_ANSWER", false);
        PendingIntent piAbsent = PendingIntent.getBroadcast(this, 0, absentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent dismissIntent = new Intent(PRESENCE_UPDATE_DISMISS);
        PendingIntent piDismiss = PendingIntent.getBroadcast(this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        _builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Presence detected")
                .setContentText(shortMessage)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(longMessage))
                .addAction(android.R.drawable.ic_input_delete, "Going out", piAbsent)
                .addAction(android.R.drawable.ic_input_add, "Coming in", piPresent)
                .setDeleteIntent(piDismiss)
                .setContentIntent(contentIntent)
                .setLights(Color.BLUE, 500, 1000);

        notificationManager.notify(PRESENCE_NOTIFICATION_ID, _builder.build());
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, final Intent intent) {

//            mNetworkHandler.post(new Runnable() {
//                @Override
//                public void run() {
            switch(intent.getAction()) {
            case PRESENCE_UPDATE_PRESENT:
                updatePresence(true);
                break;
            case PRESENCE_UPDATE_ABSENT:
                updatePresence(false);
                break;
            case PRESENCE_UPDATE_DISMISS:
                updatingPresence = false;
                break;
            }
//                    if (intent.getAction() == PRESENCE_UPDATE_PRESENT) {
//                        updatePresence(true);
//                    } else {
//                        updatePresence(false);
//                    }
//                }
//            });

//            boolean present = intent.getBooleanExtra("nl.dobots.presence.NOTIFICATION_ANSWER", false);
//            if (present) {
//                Log.d(TAG, "present");
//            } else {
//                Log.d(TAG, "absent");
//            }
        }

    };

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
        lowFrequencyDistance = settings.getFloat("lowFrequencyDistanceKey", LOW_FREQUENCY_DISTANCE);
        highFrequencyDistance = settings.getFloat("highFrequencyDistanceKey", HIGH_FREQUENCY_DISTANCE);
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

    public boolean isLoginCredentialsValid() {
        return !username.equals(usernameDefault) && !password.equals(passwordDefault);
    }

    public boolean login(){
        if (isLoggedIn()) return true;

        if(isLoginCredentialsValid()) {
            try {
                ra.login(username, password, server, closestDoBeacon.getBluetoothName());
                Map<String, Object> presence = ra.getStandByApi().getPresence(false);
                if (presence != null) {
                    setIsLoggedIn(true);
                    currentPresence = (Boolean)presence.get("present");
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void logout(){
        if(isLoggedIn()){
            ra.logout();
            setIsLoggedIn(false);
        }
    }

    @Override
    public void didDetermineStateForRegion(int arg0, Region arg1) {
        // Don't care
    }

    @Override
    public void didExitRegion(Region arg0) {
        //we can do something here like logout or set absent but I don't know the function for
//        logout();
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void setIsLoggedIn(boolean isLoggedIn) {
        this.isLoggedIn = isLoggedIn;
    }

}
