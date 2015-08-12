package nl.dobots.presence.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import nl.dobots.presence.gui.MainActivity;
import nl.dobots.presence.srv.BleScanService;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 3-8-15
 * <p/>
 * This receiver only handles one broadcast, which is the android.intent.action.BOOT_COMPLETED
 * broadcast issued after the device successfully booted. On reception of this broadcast, we
 * start our BleScanService.
 *
 * @author Dominik Egger
 *
 */
public class BootCompleteReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent startServiceIntent = new Intent(context, BleScanService.class);
		context.startService(startServiceIntent);
	}
}
