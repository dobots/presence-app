package nl.dobots.presence.rest;


import nl.dobots.presence.JOM;
import nl.dobots.presence.StandByJacksonConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import retrofit.*;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jordi on 26-8-2014.
 */
public class RestApi {

    // Singleton instance
    private static RestApi _instance = null;

    // User credentials
    private String _username;
    private String _password;

    // Endpoint
    // We need some default (cannot be blank), but it will never perform calls on this URL; login() will override
    private String _endpoint = "http://foobar.com";

    // Selected location (Two-level login)
    private String _location = null;

    // Authentication properties
    private String _sessionToken;

    // REST Adapter
    private RestAdapter _restAdapter = null;
    private JacksonConverter _converter = new StandByJacksonConverter(new ObjectMapper());

    // Log level
    RestAdapter.LogLevel _logLevel = RestAdapter.LogLevel.BASIC;

    // Error handler
    ErrorHandler _errorHandler = new ErrorHandler() {
        @Override
        public Throwable handleError(RetrofitError retrofitError) {
            return processError(retrofitError);
            //return retrofitError;
        }
    };

    // OkHttpClient
    private OkHttpClient _okHttpClient = new OkHttpClient();
    private OkClient _okClient = new OkClient( _okHttpClient );

    // Authenticated request interceptor
    RequestInterceptor _authenticatedRequestInterceptor = new RequestInterceptor() {
        @Override
        public void intercept(RequestInterceptor.RequestFacade request) {

            // Add the token as header to every request that is made by this adapter
            request.addHeader(RestConfig.HEADER_SESSION_KEY, _sessionToken);

            // For local testing we also send the session id as GET query parameter (ngrok doesnt proxy headers)
            request.addEncodedQueryParam(RestConfig.GET_SESSION_KEY, _sessionToken);
        }
    };

    // Profiler
    Profiler _profiler = new Profiler() {
        @Override
        public Object beforeCall() {
            return null;
        }

        @Override
        public void afterCall(RequestInformation requestInfo, long elapsedTime, int statusCode, Object beforeCallData) {
            debug("Running "+requestInfo.getMethod()+" " + requestInfo.getRelativePath() + " ["+statusCode+"] took " + elapsedTime + "ms");
        }
    };

    // REST Service
    StandByService _service;


    protected RestApi(){

        debug("Initialize RestApi object");

        debug("Set HTTP Clients connection timeout to: " + RestConfig.HTTP_CONNECTION_TIMEOUT + " " + RestConfig.HTTP_CONNECTION_TIMEOUT_TIME_UNIT);
        debug("Set HTTP Clients read timeout to: " + RestConfig.HTTP_READ_TIMEOUT + " " + RestConfig.HTTP_READ_TIMEOUT_TIME_UNIT);

        // Set timeout value
        _okHttpClient.setConnectTimeout(RestConfig.HTTP_CONNECTION_TIMEOUT, RestConfig.HTTP_CONNECTION_TIMEOUT_TIME_UNIT);
        _okHttpClient.setReadTimeout(RestConfig.HTTP_READ_TIMEOUT, RestConfig.HTTP_READ_TIMEOUT_TIME_UNIT);

        // setup the default adapter with StandBy REST service
        setupDefaultAdapterAndService();
    }

    private void setupDefaultAdapterAndService(){

        debug("Setup default adapter and service");

        // Setup REST API adapter
        _restAdapter = new RestAdapter.Builder()
                .setEndpoint( _endpoint )
                .setConverter( _converter )
                .setClient( _okClient )
                .setErrorHandler( _errorHandler )
                .setLogLevel( _logLevel )
                .setProfiler( _profiler )
                .build();

        // Create a service to access the StandBy REST API via the adapter
        _service = _restAdapter.create(StandByService.class);

    }

    public static RestApi getInstance() {
        if(_instance == null) {
            _instance = new RestApi();
        }
        return _instance;
    }

    public ArrayList<HashMap<String, Object>> getBackendServers() throws IOException{
        return getBackendServers(null);
    }

    public ArrayList<HashMap<String, Object>> getBackendServers(String clientName) throws IOException{

        // Build URL to load
        String serverlistUrl = RestConfig.SERVERS_URL;

        // Append the clientname as tag to act as a filter on the returned servers list
        if(clientName != null){
            serverlistUrl += "?tag=" + clientName;
        }

        // Create request
        Request request = new Request.Builder()
                .url( serverlistUrl )
                .build();

        // Execute request
        Response response = _okHttpClient.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

        // Convert JSON string to an ArrayList
        String serversList = response.body().string();
        ArrayList<HashMap<String, Object>> servers = JOM.getInstance().readValue(serversList, new TypeReference< ArrayList<HashMap<String, Object>> >(){} );

        return servers;

    }

    // Login using stored server (endpoint) + location (two-level login)
    public void login(String username, String password) throws Exception{
        login(username, password, _endpoint, _location);
    }

    // Login using stored location (two-level login)
    public void login(String username, String password, String server) throws Exception{
        login(username, password, server, _location);
    }

    // Login using all custom data
    public void login(String username, String password, String server, String location) throws Exception{

        // Store user credentials
        _username = username;
        _password = password;

        // Store API endpoint
        _endpoint = server;

        // Store login location (two-level login)
        _location = location;

        // Need to set it up again since the login needs to be done on the correct endpoint
        setupDefaultAdapterAndService();

        debug("Login user " + _username + " on server " + _endpoint);

        // Try to obtain a valid REST API token
        try {

            // Login on the REST API and obtain the token
            _service = _restAdapter.create(StandByService.class);
            Map<String, String> loginResponse = _service.login(_username, _password, _location);
            _sessionToken = loginResponse.get(RestConfig.HEADER_SESSION_KEY);

            // Debug
            debug("new token retrieved for user " + _username + ": " + _sessionToken);
            debug(loginResponse);

            // Setup OkHttp client
            OkHttpClient okHttpClient = new OkHttpClient();

            // Setup (override existing) REST API adapter with token
            _restAdapter = new RestAdapter.Builder()
                    .setEndpoint( _endpoint )
                    .setConverter( _converter )
                    .setClient( _okClient )
                    .setErrorHandler( _errorHandler )
                    .setLogLevel( _logLevel )
                    .setProfiler( _profiler )
                    .setRequestInterceptor(_authenticatedRequestInterceptor)
                    .build();

            // ((Re)create a service to access the StandBy REST API via the adapter
            _service = _restAdapter.create(StandByService.class);

        }catch (RetrofitError retrofitError) {

            // Throw error if we get a 404 or 500 etc. Only accept 200 (login failed)
            if (retrofitError.getResponse() != null && retrofitError.getResponse().getStatus() != 200) {
                throw retrofitError; // 'throw up' the error to make this login fail from the login screen
            }

        } catch(Exception e){
            e.printStackTrace();
        }

    }

    // Set the location (two-level login) for an existing session
    public List<Boolean> setLoginLocation(String location){

        // store location (Two-level login)
        _location = location;

        // Set location
        return _service.setLocation(location);
    }

    public String getLocationLogin(){
        // Note: No call to the remote server since we already store the location name internally and manage the remote session with it anyway
        // Null or empty means the default "all" group
        return _location;
    }

    public void reconnect(){

        debug("Reconnecting user " + _username);

        // Use an REST adapter without the session id set
        // NOTE: Otherwise the login function will return a 403 (due to invalid given session id)
        setupDefaultAdapterAndService();

        // Username and password? Try to login
        if(_username != null && _password != null) {

            debug("Reconnect: Start new login");

            try {
                login(_username, _password);
            }catch(Exception e){
                // Don't throw the exception since that would break the used retry mechanism.
                // This function will only retry to reconnect;
                // a next 'real' request will be used to determine if this went successful (it will check for 403)
                e.printStackTrace();
            }

        } else {
            debug("Reconnect: No username and/or password provided");
        }

    }

    public void logout(){

        debug("Logging out user " + _username);

        // Reset the REST API object
        _instance = new RestApi();
    }

    public StandByService getStandByApi(){
        return _service;
    }

    protected RetrofitError processError(RetrofitError retrofitError){

        //System.out.println("[REST API] Error: " + retrofitError.getMessage());
        System.out.println("- - - - - - - - - - - - - - - -");
        retrofitError.printStackTrace();
        System.out.println("- - - - - - - - - - - - - - - -");

        if(retrofitError.isNetworkError()){
            System.out.println("[REST API] Local network error");
        }

        // Throw custom error if Retrofit has an exception without an error
        // Currently happens when the server is offline/does not respond at all
        try {

            if(retrofitError.getResponse() != null && retrofitError.getMessage() != null) {
                return retrofitError;
            }

            if(retrofitError.getResponse() != null) {
                return RetrofitError.unexpectedError("", new Throwable("Server foutmelding: De StandBy server kan uw aanvraag momenteel niet verwerken. Probeer opnieuw. [Code: "+retrofitError.getResponse().getStatus()+"]"));
            }


            if (retrofitError.getMessage() == null) {

                if(retrofitError.getCause().getClass().equals(SocketTimeoutException.class)){
                    return RetrofitError.unexpectedError("", new Throwable("Server reageert te langzaam. Probeer het later opnieuw"));
                } else {
                    return RetrofitError.unexpectedError("", new Throwable("Server reageert momenteel niet. Probeer het later opnieuw"));
                }

            } else {
                return RetrofitError.unexpectedError("", new Throwable("Internet foutmelding: De StandBy applicatie krijgt geen antwoord van de StandBy server. Controleer uw internetverbinding"));
            }

        }catch (Exception e){

            // No message? (Some exceptions don't or the try block above failed), then use a default error
            if (e.getMessage() == null) {
                return RetrofitError.unexpectedError("", new Throwable("Server reageert momenteel niet. Probeer het later opnieuw"));
            } else {
                // Otherwise (re-)use the more specific error generated by the above try-block
                return RetrofitError.unexpectedError("", new Throwable( e.getMessage() ));
            }
        }

        //return retrofitError;
    }

    private void debug(Object response){
        System.out.println("[REST API] " + response.toString() );
    }

    // Special APIs (local)
    public String getPhotoUrl(String userUuid, String avatarId, int width, int height) {
        // Add the session id to make sure the image is allowed to be downloaded
        // Add a timestamp to prevent caching by the Picasso library on Android devices
        return  _endpoint + "/user/avatar/" + userUuid + "/photo?width=" + width + "&height=" + height + "&"+RestConfig.GET_SESSION_KEY+"="+_sessionToken+"&id="+avatarId;
    }

    // Public for testing
    public String getSessionToken(){
        return _sessionToken;
    }

}
