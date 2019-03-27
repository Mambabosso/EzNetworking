package eznetworking.util;

import java.util.UUID;

public final class UniqueId {

    public static String generate() {
        return UUID.randomUUID().toString();
    }

}
