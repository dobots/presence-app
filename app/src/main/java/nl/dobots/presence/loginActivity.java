package nl.dobots.presence;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by christian on 23/06/15.
 */
public class loginActivity extends Activity {


    public static boolean isLoginActivityActive;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_listview);
        isLoginActivityActive = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isLoginActivityActive = false;
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
}
