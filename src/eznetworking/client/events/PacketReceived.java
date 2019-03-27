package eznetworking.client.events;

import eznetworking.client.Client;
import eznetworking.packet.Packet;

public interface PacketReceived {
    public void received(Client sender, Packet packet);
}
