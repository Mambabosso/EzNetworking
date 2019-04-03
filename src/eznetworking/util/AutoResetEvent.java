package eznetworking.util;

import java.util.concurrent.TimeoutException;

public class AutoResetEvent {

    private final Object lockObject = new Object();
    private volatile boolean open = false;

    public AutoResetEvent(boolean open) {
        this.open = open;
    }

    public void waitOne() throws InterruptedException {
        synchronized (lockObject) {
            while (!open) {
                lockObject.wait();
            }
            open = false;
        }
    }

    public void waitOne(long timeout) throws InterruptedException, TimeoutException {
        synchronized (lockObject) {
            long millis = System.currentTimeMillis();
            while (!open) {
                lockObject.wait(timeout);
                if (System.currentTimeMillis() - millis >= timeout) {
                    open = false;
                    throw new TimeoutException();
                }
            }
            open = false;
        }
    }

    public void set() {
        synchronized (lockObject) {
            open = true;
            lockObject.notify();
        }
    }

    public void reset() {
        open = false;
    }
}