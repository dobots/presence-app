package nl.dobots.presence.rest;

import java.util.concurrent.TimeUnit;

/**
 * Created by Jordi on 25-8-2014.
 */
public class RestConfig {

    // Backend servers URL
    public static final String SERVERS_URL = "http://backend.ask-cs.com/standby/servers";

    // REST API Endpoint URL
    //public static final String LOCAL_DEVENDPOINT = "http://dev.ask-cs.com";
    public static final String LOCAL_DEV_ENDPOINT_NAME = "LOCAL";
    public static final String LOCAL_DEV_ENDPOINT_URL = "http://4b5c1960.ngrok.com"; // CLI: ./ngrok.exe 9000 + UI @ http://127.0.0.1:4040

    // Connection timeout (set in the constructor)
    public final static int HTTP_CONNECTION_TIMEOUT = 25000;
    public final static TimeUnit HTTP_CONNECTION_TIMEOUT_TIME_UNIT = TimeUnit.MILLISECONDS;

    // Read timeout (set in the constructor)
    public final static int HTTP_READ_TIMEOUT = 25000;
    public final static TimeUnit HTTP_READ_TIMEOUT_TIME_UNIT = TimeUnit.MILLISECONDS;

    // Local Shared Preferences token key
    public static final String PREF_TOKEN_FIELD = "apitoken";

    // Response field name containing the session token
    public static final String HEADER_SESSION_KEY = "X-SESSION_ID";
    public static final String GET_SESSION_KEY = "sid";

}
