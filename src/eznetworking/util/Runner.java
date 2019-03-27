package eznetworking.util;

public final class Runner {

    public static void run(Runnable r) {
        Thread t = new Thread(() -> {
            try {
                r.run();
            } catch (Exception ex) {
            }
        });
        t.start();
    }

}
