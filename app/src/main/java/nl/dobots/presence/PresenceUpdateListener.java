package nl.dobots.presence;

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
 * Created on 7-8-15
 *
 * @author Dominik Egger
 */
public interface PresenceUpdateListener {

	// inform listeners if a change in presence was detected, either present <-> non present
	// or a change in location
	void onPresenceUpdate(boolean present, String location, String additionalInfo);

}
