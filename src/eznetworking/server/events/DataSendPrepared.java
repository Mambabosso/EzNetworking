package eznetworking.server.events;

import eznetworking.server.connection.Connection;
import eznetworking.server.Server;
import eznetworking.util.Progress;

public interface DataSendPrepared {
    public void prepared(Server sender, Connection client, int type, int length, Progress<Integer> progress);
}
