package eznetworking.server.events;

import eznetworking.server.connection.Connection;
import eznetworking.server.Server;

public interface ClientConnected {
    public void connected(Server sender, Connection client);
}
