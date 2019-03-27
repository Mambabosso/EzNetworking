package eznetworking.server.events;

import eznetworking.server.Server;

public interface ErrorOccurred {
    public void occurred(Server sender, Exception error);
}
