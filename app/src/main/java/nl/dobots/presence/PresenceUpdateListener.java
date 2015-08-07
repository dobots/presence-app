package nl.dobots.presence;

/**
 * Created by dominik on 7-8-15.
 */
public interface PresenceUpdateListener {

	void onPresenceUpdate(boolean present, String location, String additionalInfo);

}
