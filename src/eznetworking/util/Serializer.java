package eznetworking.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class Serializer {

    public static <T extends Serializable> byte[] serialize(T tClass) {
        try {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(tClass);
                oos.flush();
                oos.close();
                return bos.toByteArray();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T deserialize(byte[] bytes) {
        try {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
                ObjectInputStream ois = new ObjectInputStream(bis);
                Object obj = ois.readObject();
                ois.close();
                return (T) obj;
            }
        } catch (Exception ex) {
            return null;
        }
    }

}
