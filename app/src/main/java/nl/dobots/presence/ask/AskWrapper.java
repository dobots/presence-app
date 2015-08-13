package nl.dobots.presence.ask;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.Map;

import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.ask.rest.RestApi;
import retrofit.RetrofitError;

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
 * Created on 4-8-15
 *
 * @author Dominik Egger
 */
public class AskWrapper {

	public interface StatusCallback {
		void onSuccess();
		void onError();
	}

	public interface PresenceCallback {
		void onSuccess(boolean present, String location);
		void onError();
	}

	private static AskWrapper instance = null;
	private boolean _loggedIn;

	private RestApi _restApi;

	private Handler _networkHandler;
//	private Boolean _presence;

	private AskWrapper() {
		_restApi = RestApi.getInstance();

		HandlerThread networkThread = new HandlerThread("NetworkHandler");
		networkThread.start();
		_networkHandler = new Handler(networkThread.getLooper());

	}

	public static AskWrapper getInstance() {
		if (instance == null) {
			instance = new AskWrapper();
		}
		return instance;
	}

	public boolean isLoginCredentialsValid(String username, String password) {
		return !username.isEmpty() && !password.isEmpty();
	}

	public void login(final String username, final String password, final String server, final PresenceCallback callback) {
//		if (isLoggedIn()) return true;

		// can't execute network operations in the main thread, so we have to delegate
		// the call to the network handler
		if (Looper.myLooper() == Looper.getMainLooper()) {
			_networkHandler.post(new Runnable() {
				@Override
				public void run() {
					login(username, password, server, callback);
				}
			});
			return;
		}

		_loggedIn = false;
		if(isLoginCredentialsValid(username, password)) {
			try {
				_restApi.login(username, password, server);
				Map<String, Object> presenceObj = _restApi.getStandByApi().getPresence(false);
				if (presenceObj != null) {
					_loggedIn = true;
//					callback.onSuccess();
					Boolean present = (Boolean) presenceObj.get("present");
					String location = (String) presenceObj.get("location");
					callback.onSuccess(present, location);
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		callback.onError();
	}

	public boolean isLoggedIn() {
		return _loggedIn;
	}

	public void setLoggedIn(boolean loggedIn) {
		_loggedIn = loggedIn;
	}

	public void updatePresence(final boolean present, final String location, final StatusCallback callback) {

		// can't execute network operations in the main thread, so we have to delegate
		// the call to the network handler
		if (Looper.myLooper() == Looper.getMainLooper()) {
			_networkHandler.post(new Runnable() {
				@Override
				public void run() {
					updatePresence(present, location, callback);
				}
			});
			return;
		}

		try {
			_restApi.getStandByApi().setLocationPresenceManually(present, location);
			callback.onSuccess();
		} catch (RetrofitError e) {
			e.printStackTrace();
			callback.onError();
		}
	}

	public void getCurrentPresence(final PresenceCallback callback) {
		// can't execute network operations in the main thread, so we have to delegate
		// the call to the network handler
		if (Looper.myLooper() == Looper.getMainLooper()) {
			_networkHandler.post(new Runnable() {
				@Override
				public void run() {
					getCurrentPresence(callback);
				}
			});
			return;
		}

		try {
			Map<String, Object> presenceObj = _restApi.getStandByApi().getPresence(false);
			if (presenceObj != null) {
				Boolean present = (Boolean) presenceObj.get("present");
				String location = (String) presenceObj.get("location");
				callback.onSuccess(present, location);
			}
		} catch (RetrofitError e) {
			e.printStackTrace();
			callback.onError();
		}
	}

}
