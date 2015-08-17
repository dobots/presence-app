package nl.dobots.presence.gui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.R;
import nl.dobots.presence.cfg.Settings;
import nl.dobots.presence.ask.AskWrapper;
import nl.dobots.presence.ask.Cryptography;


/**
 * Created by christian on 23/06/15.
 */
public class LoginActivity extends Activity {

	public static String TAG = LoginActivity.class.getCanonicalName();

	private Button _btnLogin;
	private EditText _edtUsername;
	private EditText _edtPassword;
	private EditText _edtServer;

	private Settings _settings;
	private AskWrapper _ask;

	private boolean _credentialsValid;

//    public static boolean isLoginActivityActive;

	// Authentication properties
//    private String _sessionToken;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
		if (android.os.Build.VERSION.SDK_INT > 9) {
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);
		}
//        isLoginActivityActive = true;

		_settings = Settings.getInstance();
		_ask = AskWrapper.getInstance();

		_credentialsValid = false;

		// Set last known username and password in the edittext field
		_btnLogin = (Button) findViewById(R.id.button_login);
		_edtUsername = (EditText) findViewById(R.id.input_username);
		_edtPassword = (EditText) findViewById(R.id.input_password);
		_edtServer = (EditText) findViewById(R.id.input_server);

		// Prefill username/password fields
		_edtUsername.setText(_settings.getUsername());
		_edtPassword.setText(_settings.getPassword()); // Not the MD5 hash, but the original password
		_edtServer.setText(_settings.getServer());

		// Give focus to the password form field when we've preset the username
//        if (!PresenceApp.INSTANCE.username.equals(PresenceApp.INSTANCE.usernameDefault) ) {
//            _edtPassword.requestFocus();
//        }

		// Some versions/devices set hint in password field to a monospace font (different from the username field). Fix:
		_edtPassword.setTypeface(Typeface.DEFAULT);

		// On login
		_btnLogin = (Button) findViewById(R.id.button_login);
		_btnLogin.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {

				_settings.setUsername(_edtUsername.getText().toString().toLowerCase());
				String password = _edtPassword.getText().toString();

				// If the password is 32 characters long; consider it being the md5 hash and use that one directly instead
				if(password.length() == 32)
					_settings.setPassword(password);
				else
					_settings.setPassword(Cryptography.md5(password));

				Log.w(TAG, "Login - Using REST endpoint ");

				if (!_ask.isLoginCredentialsValid(_settings.getUsername(), _settings.getPassword())) {
					Log.w(TAG, "Failed - No username and/or password given via the login form");
					Toast.makeText(getApplicationContext(), "Please fill in your username and password.", Toast.LENGTH_SHORT).show();
				} else {
					_ask.login(_settings.getUsername(), _settings.getPassword(), _settings.getServer(),
							new AskWrapper.PresenceCallback() {
								@Override
								public void onSuccess(boolean present, String location) {
									_credentialsValid = true;
									hideKeyboard();
									Toast.makeText(getApplicationContext(),"Welcome " + _settings.getUsername() + " !",Toast.LENGTH_SHORT).show();
									finish();
								}

								@Override
								public void onError(String errorMessage) {
									Toast.makeText(getApplicationContext(), "Failed to log in! Check your internet and/or username and password.",Toast.LENGTH_SHORT).show();
								}
							}
					);
				}
			}
		});

		PresenceDetectionApp.getInstance().pauseDetection();
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
//        isLoginActivityActive = false;
		if (_ask.isLoggedIn() && _credentialsValid) {
			_settings.writePersistentCredentials(getApplicationContext());
		}
	}

	@Override
	public void onResume() {
		super.onResume();
//        isLoginActivityActive = true;
	}

	@Override
	public void onPause() {
		super.onPause();
//        isLoginActivityActive = false;
	}
//
//    private void writePersistentSettings() {
//        //store the settings in the Shared Preference file
//        SharedPreferences settings = getSharedPreferences(PresenceApp.SETTING_FILE, 0);
//        SharedPreferences.Editor editor = settings.edit();
//        editor.remove("usernameKey");
//        editor.remove("passwordKey");
//        editor.remove("serverKey");
//        editor.putString("usernameKey", PresenceApp.INSTANCE.username);
//        editor.putString("passwordKey", PresenceApp.INSTANCE.password);
//        editor.putString("serverKey", PresenceApp.INSTANCE.server);
//        // Commit the edits!
//        editor.commit();
//    }

	public void hideKeyboard() {
		InputMethodManager inputMethodManager = (InputMethodManager)  this.getSystemService(Activity.INPUT_METHOD_SERVICE);
		inputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
	}

	public static void show(Context context) {
		context.startActivity(new Intent(context, LoginActivity.class));
	}
}
