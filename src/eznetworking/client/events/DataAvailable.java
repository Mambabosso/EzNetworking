package eznetworking.client.events;

import eznetworking.client.Client;
import eznetworking.util.Progress;

public interface DataAvailable {
    public void available(Client sender, int type, int length, Progress<Integer> progress);
}
