package eznetworking.server.events;

import eznetworking.server.connection.Connection;
import eznetworking.server.Server;

public interface BytesReceived {
    public void received(Server sender, Connection client, byte[] data);
}
