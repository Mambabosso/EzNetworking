package eznetworking.client.events;

import eznetworking.client.Client;

public interface CustomReceived {
    public void received(Client sender, int type, byte[] data);
}
