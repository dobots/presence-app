package nl.dobots.presence;

/**
 * Created by dominik on 7-8-15.
 */
public interface PresenceUpdateListener {

	// inform listeners if a change in presence was detected, either present <-> non present
	// or a change in location
	void onPresenceUpdate(boolean present, String location, String additionalInfo);

}
