package nl.dobots.presence.rest;

import retrofit.client.Response;
import retrofit.http.*;
import retrofit.mime.TypedFile;

import java.util.*;

/**
 * Created by Jordi on 25-8-2014.
 */
public interface StandByService {

    @GET("/login")
    Map<String, String> login(@Query("uuid") String username, @Query("pass") String password, @Query("location") String location);

    @GET("/mobile/current")
    Map<String, Object> getCurrent(@Query("start") Long start, @Query("end") Long end, @Query("strict") Boolean strict, @Query("excludeAssemble") Boolean excludeAssemble);

    @GET("/mobile/groupsBalance")
    Map<String, List<Map<String, Object>>> getGroups(@Query("start") Long start, @Query("end") Long end, @Query("strict") Boolean strict);

    @GET("/mobile/groupsWithMembers")
    Map<String, Map<String, Map<String, Object>>> getGroupsWithMembers(@Query("keys") List<String> keys, @Query("strict") Boolean strict);

    @GET("/mobile/assemblies")
    List<Map<String,String>> getAssemblies();

    @GET("/mobile/states")
    Set<String> getStateLabels();

    @PUT("/mobile/slot")
    boolean setSlot(@Query("startTime") long startTime, @Query("endTime") long endTime, @Query("desc") String desc, @Query("occurence") String occurence);

    @GET("/shortage_precheck/{uuid}")
    ArrayList<Map<String,Object>> getShortagePrecheck(@EncodedPath("uuid") String uuid, @Query("startTime") long startTime, @Query("endTime") long endTime, @Query("desc") String desc, @Query("occurence") String occurence);

    @GET("/mobile/testversion")
    boolean testVersion(@Query("version") String version, @Query("cleanup") Boolean cleanup);

    @GET("/userdata")
    Map<String, Object> getOwnUserData();

    @GET("/userdata/{uuid}")
    Map<String, Object> getUserData(@EncodedPath("uuid") String uuid);

    @GET("/domain")
    List<String> getDomain();

    // Two level login APIs
    @GET("/domain/locations/{uuid}")
    List<HashMap<String, String>> getLocations(@EncodedPath("uuid") String uuid);

    @POST("/domain/location")
    List<Boolean> setLocation(@Body String location);

    @GET("/domain/location")
    List<String> getCurrentLocation();

    // Presence and location identification data
    // Presence locations (Not the group location from the domain agent)
    @GET("/mobile/locations")
    List<String> getPresenceLocations();

    // Get the SSIDs to check for
    @GET("/mobile/locationIdentifiers/ssid")
    List<String> getLocationIdentifiers();

    // Update the presencelocation based on the matched SSID
    @PUT("/mobile/presence/ssid")
    List<Boolean> setLocationPresenceBySsid(@Query("presence") boolean presence, @Query("ssid") String ssid);

    //@PUT("/mobile/presence/mac")
    //List<Boolean> setLocationPresenceByMac(@Query("presence") boolean presence, @Query("mac") String mac);

    //@PUT("/mobile/presence")
    //List<Boolean> setLocationPresenceByValue(@Query("presence") boolean presence, @Query("value") String value);

    // Manually update the users precent location ([false, null] = auto mode, [false, ""] = manual mode not present)
    @PUT("/mobile/presence/manual")
    List<Boolean> setLocationPresenceManually(@Query("presence") boolean presence, @Query("location") String location);

    @GET("/mobile/presence")
    Map<String, Object> getPresence(@Query("extra") Boolean extra);

    @GET("/network/all/members/presence")
    List<HashMap<String, Object>> getAllMembersPresence();

    // Avatars

    /**
     * Upload a new avatar
     *
     * @param memberId
     *         The uuid of the user
     * @param square
     *         Indicates that the back-end should crop the image to a square image before saving on disk
     *
     * @return {@code {"result":"ok"} } or an error
     */
    @Multipart
    @POST("/user/avatar/{memberId}/photo")
    Response uploadUserAvatar(@Path("memberId") String memberId, @Query("square") boolean square, @Part("file") TypedFile image);

    // Resources

    // Add/update resource
    @POST("/resources")
    Response setResources(@Body HashMap resources);

    // ACL (Access Control List)
    @GET("/acl")
    Map<String, Object> getAcl();

    // Settings (settings List)
    @GET("/settings")
    Map<String, Object> getSettings();

    // Channels data
    @GET("/channels")
    List<HashMap<String, String>> getChannels();

    @POST("/incident")
    List<Boolean> sendAlarm( @Body HashMap alarm );

    @PUT("/incident/response")
    List<Boolean> respondToAlarm(@Query("incidentId") String incidentId, @Query("response") Boolean response);
}
