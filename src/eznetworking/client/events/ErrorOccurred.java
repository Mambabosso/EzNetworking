package eznetworking.client.events;

import eznetworking.client.Client;

public interface ErrorOccurred {
    public void occurred(Client sender, Exception error);
}
