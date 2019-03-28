package eznetworking.util;

public final class Runner {

    public static void run(Runnable r, boolean newThread) {
        if (newThread) {
            new Thread(() -> r.run()).start();
        } else {
            r.run();
        }
    }

    public static void run(Runnable r) {
        run(r, false);
    }

}
