package eznetworking.client.events;

import eznetworking.client.Client;
import eznetworking.util.Progress;

public interface DataSendPrepared {
    public void prepared(Client sender, int type, int length, Progress<Integer> progress);
}
