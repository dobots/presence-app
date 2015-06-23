package com.askcs.standby_vanilla.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.askcs.standby_vanilla.R;
import com.askcs.standby_vanilla.adapters.ServersAdapter;
import com.askcs.standby_vanilla.callbacks.onEveServiceReady;
import com.askcs.standby_vanilla.events.LoginStateChangeEvent;
import com.askcs.standby_vanilla.rest.RestApi;
import com.askcs.standby_vanilla.service.EveService;
import com.askcs.standby_vanilla.util.AppConfig;
import com.askcs.standby_vanilla.util.Cryptography;
import com.squareup.otto.Subscribe;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class LoginActivity extends BaseActivity {

    public static String TAG = LoginActivity.class.getCanonicalName();
    private Context ctx;

    private SharedPreferences sp;

    private Button loginButton;
    private EditText fieldUsername;
    private EditText fieldPassword;
    private Spinner fieldServer;

    private ViewGroup contentContainer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        // Content container (for Croutons)
        contentContainer = (ViewGroup) findViewById(R.id.content_login);

        // Instantly show a notification here if the device is offline
        notifyIfOffline();

        // Copy context
        ctx = this;

        // This activity
        final LoginActivity self = this;

        // Shared prefs
        sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        final SharedPreferences fsp = sp;

        // Set last known username and password in the edittext field
        loginButton = (Button) findViewById(R.id.btn_login);
        fieldUsername = (EditText) findViewById(R.id.input_username);
        fieldPassword = (EditText) findViewById(R.id.input_password);
        fieldServer = (Spinner) findViewById(R.id.backend_servers);

        // AppConfig: Hide system selector if a specific back-end system is forced
        // AppConfig: Hide system selector if a specific back-end system is forced
        if(AppConfig.ACTIVITY_LOGIN_FORCE_BACKEND){
            // Visually remove serverlist
            fieldServer.setVisibility(View.GONE);

            // Add rounded corners to password field if serverlist is removed
            fieldPassword.setBackgroundDrawable( getResources().getDrawable(R.drawable.txt_white_corners_bottom_gray_borders) );
        }

        // Disable login button by default; enable it if the server list is filled
        loginButton.setEnabled( false );

        // Prefill username/password fields
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        fieldUsername.setText(prefs.getString(EveService.XMPP_USERNAME_KEY, ""));
        fieldPassword.setText(prefs.getString(EveService.XMPP_ORIGINAL_PASSWORD_KEY, "")); // Not the MD5 hash, but the original password

        loadBackendServersList();

        // Give focus to the password form field when we've preset the username
        if ( prefs.contains( EveService.XMPP_USERNAME_KEY ) ) {
            fieldPassword.requestFocus();
        }

        // Some versions/devices set hint in password field to a monospace font (different from the username field). Fix:
        fieldPassword.setTypeface(Typeface.DEFAULT);

        // On login
        final Button button = (Button) findViewById(R.id.btn_login);
        button.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {

                System.err.println("[onClick] Login");

                // Stop login attempt if the device is offline
                if ( notifyIfOffline() ) {
                    return;
                }

                String username = fieldUsername.getText().toString().toLowerCase();
                String originalpassword = fieldPassword.getText().toString();
                String password = Cryptography.md5(originalpassword);

                // If the password is 32 characters long; consider it being the md5 hash and use that one directly instead
                if(originalpassword.length() == 32){
                    password = originalpassword;
                }

                // Get the server and selected position
                HashMap<String, String> selectedServer = (HashMap<String, String>) fieldServer.getSelectedItem();

                // Stop login attempt if the server list is (still) empty
                if(selectedServer == null) {
                    return;
                }

                String server = selectedServer.get("url");
                int selectedPosition = fieldServer.getSelectedItemPosition();

                Log.w(TAG, "Login - Using REST endpoint ["+selectedPosition+"]: " + server );

                if (username.equals("") || originalpassword.equals("")) {
                    Log.w(TAG, "Failed - No username and/or password given via the login form");
                    Crouton.cancelAllCroutons();
                    Crouton.showText((LoginActivity) ctx, "Vul een gebruikersnaam en wachtwoord in", Style.ALERT, contentContainer);
                }
                else {

                    // Save the userdetails in the sharedprefs
                    SharedPreferences.Editor spe = fsp.edit();
                    spe.putString(EveService.XMPP_USERNAME_KEY, username);
                    spe.putString(EveService.XMPP_PASSWORD_KEY, password);
                    spe.putString(EveService.XMPP_ORIGINAL_PASSWORD_KEY, originalpassword);
                    spe.putString(EveService.HTTP_ENDPOINT, server);
                    spe.putInt(EveService.HTTP_ENDPOINT_SELECTED_POSITION, selectedPosition);
                    spe.commit();

                    // Lets see if the LoaderActivity can log us in with these new details
                    Intent loadingActivity = new Intent(ctx, LoaderActivity.class);
                    loadingActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(loadingActivity);

                    // NOTE: Important to finish. Otherwise this activity will be 'recycled' on logout and thus showing the password again
                    // NOTE: Alternative: Empty the passwordfield in checkStartUpStuff() when the LOGIN_STATE__LOGOUT is triggered
                    finish();

                }
            }
        });

        // Handle "shortcut" submit (from soft keyboard)
        fieldPassword.setOnEditorActionListener( new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction( TextView textView, int id, KeyEvent keyEvent ) {

                // Trigger onClick in the background when submitting the login form from the keyboard
                if ( id == R.id.btn_login || id == EditorInfo.IME_NULL ) {
                    button.performClick();
                    return true;
                }
                return false;

            }

        } );

    }
    private void loadBackendServersList(){

        // Remove the onclick listener on the servers field
        fieldServer.setOnTouchListener(null);

        // Copy context
        ctx = this;

        // This activity
        final LoginActivity self = this;

        // Load backend servers in the background
        new Thread(new Runnable() {
            @Override
            public void run() {

                // Get the list over servers from the remote server
                ArrayList<HashMap<String, Object>> servers = new ArrayList<HashMap<String, Object>>();
                try {

                    // Appconfig: Get all backend servers or a forced specific one
                    if(AppConfig.ACTIVITY_LOGIN_FORCE_BACKEND || !AppConfig.ACTIVITY_LOGIN_FORCE_BACKEND_TYPE.equals("")) {
                        servers = RestApi.getInstance().getBackendServers( AppConfig.ACTIVITY_LOGIN_FORCE_BACKEND_TYPE );
                    } else {
                        servers = RestApi.getInstance().getBackendServers();
                    }

                    System.out.println(servers.toString());
                }
                catch (IOException e) {
                    Crouton.showText(self, getResources().getString(R.string.login_failed_to_load_servers), Style.ALERT, contentContainer);

                    // Retry loading this list when someone tocuhes the server list field if the previous loading failed
                    fieldServer.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            loadBackendServersList();
                            return false;
                        }
                    });
                    e.printStackTrace();
                }

                // TODO: Disable before release
                // Add local development server if it's enabled
                /*
                if(RestConfig.LOCAL_DEV_ENDPOINT_URL != null){
                    HashMap localDevServer = new HashMap();
                    localDevServer.put("name", RestConfig.LOCAL_DEV_ENDPOINT_NAME);
                    localDevServer.put("url", RestConfig.LOCAL_DEV_ENDPOINT_URL);
                    localDevServer.put("debug", true);
                    servers.add(localDevServer);
                }
                //*/

                final  ArrayList<HashMap<String, Object>> fServers = servers;

                // Back to the UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // Write the servers into the spinner
                        ServersAdapter adapter = new ServersAdapter(self, fServers);
                        fieldServer.setAdapter(adapter);

                        // Get the last selected one and select it again (remember last used option)
                        int position = sp.getInt(EveService.HTTP_ENDPOINT_SELECTED_POSITION, 0);
                        if(position < fieldServer.getCount()) { // Smaller because position is 0-based and the count 1-based
                            fieldServer.setSelection(position);
                        } else {
                            // Out of range; previously selected server is no longer available (changed list or couldnt [fully] load?)
                        }

                        // Enable the login button again
                        loginButton.setEnabled( true );

                    }
                });

            }
        }).start();
    }

    /* START -- Activity specific menu adjustments */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // Inflate [old style] menu for login screen
        MenuInflater inflater = getMenuInflater();
        menu.clear();
        inflater.inflate(R.menu.main, menu);

        // Activity specific menu item changes
        menu.findItem(R.id.action_logout).setVisible(false);
        menu.findItem(R.id.action_settings_two_level_login).setVisible(false);
        menu.findItem(R.id.action_settings_geofence).setVisible(false);
        menu.findItem(R.id.action_settings_profile).setVisible(false);
        //menu.findItem(R.id.action_settings_geofence_map).setVisible(false);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Otherwise fallback to BaseActivity menu items handlers
        return super.onOptionsItemSelected(item);
    }
    /* END -- Activity specific menu adjustments */

    @Override
    protected void onNewIntent(Intent intent) {

        System.err.println("Incoming Intent...");
        super.onNewIntent(intent);

        setIntent( intent );

        checkStartUpStuff(intent);
    }

    private void checkStartUpStuff(Intent intent) {

        System.out.println("checkStartUpStuff LoginActivity");

        // Incoming intent stuff
        Bundle extras = intent.getExtras();
        if (extras != null) {

            // On incoming failed login
            if (extras.containsKey(EveService.LOGIN_STATE_FIELDNAME) == true){

                Log.w(TAG, "[checkStartUpStuff] Extra " + EveService.LOGIN_STATE_FIELDNAME + " = " + extras.getString( EveService.LOGIN_STATE_FIELDNAME ) + ".");

                // On incoming failed username/password, on incoming failed reconnect, on incoming failed system error
                if(extras.getString( EveService.LOGIN_STATE_FIELDNAME ).equals( EveService.LOGIN_STATE__FAILED ) ||
                        extras.getString( EveService.LOGIN_STATE_FIELDNAME ).equals( EveService.LOGIN_STATE__FAILED_INIT_AGENT ) ||
                        extras.getString( EveService.LOGIN_STATE_FIELDNAME ).equals( EveService.LOGIN_STATE__FAILED_RECONNECT ) ||
                        extras.getString( EveService.LOGIN_STATE_FIELDNAME ).equals( EveService.LOGIN_STATE__FAILED_SYSTEM ) ){

                    loginFailed( extras.getString( EveService.LOGIN_STATE_FIELDNAME ) );
                }

                // On incoming logout
                if(extras.getString( EveService.LOGIN_STATE_FIELDNAME ).equals( EveService.LOGIN_STATE__LOGOUT )){
                    Log.w(TAG, "[checkStartUpStuff] Extra " + EveService.LOGIN_STATE_FIELDNAME + " = " + EveService.LOGIN_STATE__LOGOUT + ".");
                }

                // Remove this extra now
                intent.removeExtra(EveService.LOGIN_STATE_FIELDNAME);

            }


            // Try to bind the service and check if we can redirect to the main activity (by triggering [another] login)
            // to make sure the regular login flow is kept.

            // NOTE: This can happen when the user has a bad connection; gets redirected to the login screen;
            // is automatically logged in in the background; but in the 'active apps list' still has the Login screen as
            // current activity.

            // Don't try this if the user just logged out or had some error
            if (extras.containsKey(EveService.LOGIN_STATE_FIELDNAME) == false /* &&
                    !extras.getString(EveService.LOGIN_STATE_FIELDNAME).equals( EveService.LOGIN_STATE__LOGOUT )*/){

                final EditText fieldUsername = (EditText) findViewById(R.id.input_username);
                final EditText fieldPassword = (EditText) findViewById(R.id.input_password);

                // Only try this if we still have a username/password (probably unchanged after auto logout) on the screen

                if (!fieldUsername.getText().equals("") && !fieldPassword.getText().equals("")) {

                    requestService( new onEveServiceReady() {
                        @Override
                        public void onEveServiceReady(EveService es) {

                            final EveService _es = es;

                            ( new Thread() {

                                @Override
                                public void run() {

                                    try {

                                        //if( _es.getMobileAgent().isConnected() ){
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.w(TAG, "Trying auto login from LoginActivity");
                                                LoginActivity.this.findViewById(R.id.btn_login).performClick();
                                            }
                                        });
                                        //}

                                    } catch (Exception e) {
                                        Log.w(TAG, "No agent and/or connected agent; not automatically loggin in");
                                        e.printStackTrace();
                                    }

                                }

                            }).start();
                        }
                    });

                }

                // Remove processed extra value from intent
                getIntent().removeExtra(EveService.LOGIN_STATE_FIELDNAME);

            }


        }

    }


    @Subscribe
    public void onLoginStateChange(LoginStateChangeEvent lsce) {

        // Incoming message of failed login (from EveService)
        if (lsce.getState().equals(EveService.LOGIN_STATE__FAILED) ||
                lsce.getState().equals(EveService.LOGIN_STATE__FAILED_SYSTEM) ||
                lsce.getState().equals(EveService.LOGIN_STATE__FAILED_RECONNECT) ||
                lsce.getState().equals(EveService.LOGIN_STATE__FAILED_INIT_AGENT)
                ) {
            Log.w(TAG, "[onLoginStateChange] State: " + lsce.getState());
            loginFailed( lsce.getState() );
        }

        // ...
    }

    /* Loginstate actions */
    private void loginFailed(String type) {
        Log.w(TAG, "[Crouton] Login failed");

        //Crouton.showText(this, getResources().getString(R.string.login_unknownerror), Style.ALERT, R.id.login_form);

        // Due to duplicate events coming in (oncreate/onresume [onCheckstartup] + intent extra + busprovider events),
        // only show the last (current) failed crouton message
        Crouton.cancelAllCroutons();

        // Determine which error to display
        if(type.equals(EveService.LOGIN_STATE__FAILED) || type.equals(EveService.LOGIN_STATE__FAILED_INIT_AGENT)){
            Crouton.showText(this, getResources().getString(R.string.login_warning_wrong_password), Style.ALERT, contentContainer);
        } else if(type.equals(EveService.LOGIN_STATE__FAILED_RECONNECT)){
            Crouton.showText(this, getResources().getString(R.string.login_warning_reconnect), Style.ALERT, contentContainer);
        } else if(type.equals(EveService.LOGIN_STATE__FAILED_SYSTEM)){
            Crouton.showText(this, getResources().getString(R.string.login_warning_system), Style.ALERT, contentContainer);
        } else {
            Crouton.showText(this, getResources().getString(R.string.login_unknownerror), Style.ALERT, contentContainer);
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        checkStartUpStuff(getIntent());
    }

}


