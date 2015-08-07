package nl.dobots.presence.ask;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created with IntelliJ IDEA.
 * User: Jordi
 * Date: 17-10-13
 * Time: 10:38
 * To change this template use File | Settings | File Templates.
 */
public class Cryptography {

    /**
     * Get the MD5 hash of a given string
     * @param str
     * @return String hash
     */
    public static String md5(String str){

        MessageDigest m = null;
        try {
            m = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        m.update(str.getBytes(), 0, str.length());
        byte[] bytes = m.digest();
        StringBuffer hash = new StringBuffer(32);
        for ( int i = 0; i < bytes.length; i++ ) {
            hash.append( Integer.toHexString( (bytes[ i ] >>> 4) & 0xf ) );
            hash.append( Integer.toHexString( bytes[ i ] & 0xf ) );
        }
        return hash.toString();
    }
}
