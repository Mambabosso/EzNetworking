package eznetworking.server.events;

import eznetworking.server.connection.Connection;
import eznetworking.server.Server;

public interface ClientDisconnected {
    public void disconnected(Server sender, Connection client);
}
