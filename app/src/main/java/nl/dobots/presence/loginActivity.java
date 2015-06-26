package nl.dobots.presence;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import android.widget.Toast;


/**
 * Created by christian on 23/06/15.
 */
public class loginActivity extends Activity {

    private Button loginButton;
    private EditText fieldUsername;
    private EditText fieldPassword;
    private EditText fieldServer;
    private String password;

    public static String TAG = loginActivity.class.getCanonicalName();

    public static boolean isLoginActivityActive;

    // Authentication properties
    private String _sessionToken;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        isLoginActivityActive = true;

        // Set last known username and password in the edittext field
        loginButton = (Button) findViewById(R.id.btn_login);
        fieldUsername = (EditText) findViewById(R.id.input_username);
        fieldPassword = (EditText) findViewById(R.id.input_password);
        fieldServer = (EditText) findViewById(R.id.input_server);

        // Prefill username/password fields
        fieldUsername.setText(presenceApp.username);
        fieldPassword.setText(presenceApp.password); // Not the MD5 hash, but the original password
        fieldServer.setText(presenceApp.server);

        // Give focus to the password form field when we've preset the username
        if (!presenceApp.username.equals(presenceApp.usernameDefault) ) {
            fieldPassword.requestFocus();
        }

        // Some versions/devices set hint in password field to a monospace font (different from the username field). Fix:
        fieldPassword.setTypeface(Typeface.DEFAULT);

        // On login
        final Button button = (Button) findViewById(R.id.btn_login);
        button.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {

                System.err.println("[onClick] Login");

                presenceApp.username = fieldUsername.getText().toString().toLowerCase();
                password = fieldPassword.getText().toString();

                // If the password is 32 characters long; consider it being the md5 hash and use that one directly instead
                if(password.length() == 32)
                    presenceApp.password = password;
                else
                    presenceApp.password = Cryptography.md5(password);

                Log.w(TAG, "Login - Using REST endpoint ");

                if (presenceApp.username.equals("") || presenceApp.password.equals("")) {
                    Log.w(TAG, "Failed - No username and/or password given via the login form");
                    Toast.makeText(getApplicationContext(), "Please fill in your username and password.", Toast.LENGTH_SHORT).show();
                }
                else {

                    // Login request
                    try {
                        presenceApp.ra.login(presenceApp.username, presenceApp.password, presenceApp.server, presenceApp.closestDoBeacon.getBluetoothName());
                        if(presenceApp.ra.getStandByApi().setLocationPresenceManually(true, presenceApp.closestDoBeacon.getBluetoothName()).get(0)) {
                            presenceApp.isLoggedIn=true;
                            hideKeyboard();
                            Toast.makeText(getApplicationContext(),"Welcome " + presenceApp.username+ " !",Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        else
                            Toast.makeText(getApplicationContext(),"Couldn't reach the server ! Are you connected to internet?",Toast.LENGTH_LONG).show();
                    } catch(Exception e) {
                        e.printStackTrace();
                        return;
                    }

                }
            }
        });
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        isLoginActivityActive = false;
        if (presenceApp.isLoggedIn)
            writePersistentSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        isLoginActivityActive = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        isLoginActivityActive = false;
    }

    private void writePersistentSettings() {
        //store the settings in the Shared Preference file
        SharedPreferences settings = getSharedPreferences(presenceApp.SETTING_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove("usernameKey");
        editor.remove("passwordKey");
        editor.remove("serverKey");
        editor.putString("usernameKey", presenceApp.username);
        editor.putString("passwordKey", presenceApp.password);
        editor.putString("serverKey",presenceApp.server);
        // Commit the edits!
        editor.commit();
    }

    public void hideKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager)  this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
    }
}
