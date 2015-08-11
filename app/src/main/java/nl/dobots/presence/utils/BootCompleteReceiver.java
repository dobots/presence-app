package nl.dobots.presence.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import nl.dobots.presence.gui.MainActivity;
import nl.dobots.presence.srv.BleScanService;

/**
 * Created by dominik on 3-8-15.
 *
 * This receiver only handles one broadcast, which is the android.intent.action.BOOT_COMPLETED
 * broadcast issued after the device successfully booted. On reception of this broadcast, we
 * start our BleScanService.
 */
public class BootCompleteReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent startServiceIntent = new Intent(context, BleScanService.class);
		context.startService(startServiceIntent);
	}
}
