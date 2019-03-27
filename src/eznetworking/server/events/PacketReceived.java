package eznetworking.server.events;

import eznetworking.packet.Packet;
import eznetworking.server.connection.Connection;
import eznetworking.server.Server;

public interface PacketReceived {
    public void received(Server sender, Connection client, Packet packet);
}
