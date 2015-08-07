package nl.dobots.presence.ask;

import java.util.Map;

import nl.dobots.presence.ask.rest.RestApi;

/**
 * Created by dominik on 4-8-15.
 */
public class AskWrapper {

	private static AskWrapper instance = null;
	private boolean _loggedIn;

	private RestApi _restApi;

	private AskWrapper() {
		_restApi = RestApi.getInstance();
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

	public boolean login(String username, String password, String server) {
//		if (isLoggedIn()) return true;

		_loggedIn = false;
		if(isLoginCredentialsValid(username, password)) {
			try {
				_restApi.login(username, password, server);
				Map<String, Object> presence = _restApi.getStandByApi().getPresence(false);
				if (presence != null) {
					_loggedIn = true;
					return true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public boolean isLoggedIn() {
		return _loggedIn;
	}

	public void setLoggedIn(boolean loggedIn) {
		_loggedIn = loggedIn;
	}

	public void updatePresence(boolean present, String location) {
		_restApi.getStandByApi().setLocationPresenceManually(present, location);
	}

}
