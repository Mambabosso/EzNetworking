package eznetworking.client.events;

import eznetworking.client.Client;

public interface BytesReceived {
    public void received(Client sender, byte[] data);
}
