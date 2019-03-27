package eznetworking.util;

import java.security.MessageDigest;

public final class SHA256Hash {

    public static String getHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append("0");
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return null;
        }
    }

}
