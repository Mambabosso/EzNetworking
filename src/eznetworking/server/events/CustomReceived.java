package eznetworking.server.events;

import eznetworking.server.Connection;
import eznetworking.server.Server;

public interface CustomReceived {
    public void received(Server sender, Connection client, int type, byte[] data);
}
