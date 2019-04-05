package eznetworking.packet;

import java.io.Serializable;
import java.time.LocalDateTime;

import eznetworking.util.SHA256Hash;
import eznetworking.util.Serializer;
import eznetworking.util.UniqueId;

public final class Packet implements Serializable, Comparable<Packet> {

    private static final long serialVersionUID = 3841842235028607064L;

    private final String id;

    private final LocalDateTime creationDateTime;

    private final String header;

    private final String source;

    private final String destination;

    private final Class<?> payloadClass;

    private final byte[] payloadBytes;

    private Packet(String header, String source, String destination, Class<?> payloadClass, byte[] payloadBytes) {
        this.id = UniqueId.generate();
        this.creationDateTime = LocalDateTime.now();
        this.header = header;
        this.source = source;
        this.destination = destination;
        this.payloadClass = payloadClass;
        this.payloadBytes = payloadBytes;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getCreationDateTime() {
        return creationDateTime;
    }

    public String getHeader() {
        return header;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public Class<?> getPayloadClass() {
        return payloadClass;
    }

    public <T> T unpack(Class<T> tClass) {
        if (!payloadClass.equals(tClass)) {
            throw new IllegalArgumentException();
        }
        return Serializer.deserialize(payloadBytes);
    }

    public String getReplyHeader() {
        return String.format("%s::%s", "REPLY", id);
    }

    public String getSHA256Hash() {
        return SHA256Hash.getHash(payloadBytes);
    }

    @Override
    public int compareTo(Packet packet) {
        if (packet != null) {
            return header.compareTo(packet.header);
        }
        return 0;
    }

    public static <T extends Serializable> Packet create(String header, String source, String destination, T tClass) {
        if (header != null && !header.trim().isEmpty() && tClass != null) {
            byte[] bytes = Serializer.serialize(tClass);
            if (bytes != null) {
                return new Packet(header, source, destination, tClass.getClass(), bytes);
            }
        }
        return null;
    }

    public static <T extends Serializable> Packet create(String header, T tClass) {
        return Packet.create(header, null, null, tClass);
    }

}