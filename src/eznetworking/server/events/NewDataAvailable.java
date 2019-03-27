package eznetworking.server.events;

import eznetworking.server.Connection;
import eznetworking.server.Server;
import eznetworking.util.Progress;

public interface NewDataAvailable {
    public void available(Server sender, Connection client, int type, int length, Progress<Integer> progress);
}
