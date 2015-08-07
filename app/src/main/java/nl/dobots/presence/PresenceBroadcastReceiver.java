package nl.dobots.presence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import nl.dobots.presence.gui.MainActivity;
import nl.dobots.presence.srv.BleScanService;

/**
 * Created by dominik on 3-8-15.
 */
public class PresenceBroadcastReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
//		Intent startServiceIntent = new Intent(context, BleScanService.class);
//		context.startService(startServiceIntent);
		Intent startActivityIntent = new Intent(context, MainActivity.class);
		context.startActivity(startActivityIntent);
	}
}
