package eznetworking.util;

import java.util.ArrayList;

public class Progress<T> {

    public interface ProgressChanged<T> {
        public void changed(Progress<T> sender, T value);
    }

    private ArrayList<ProgressChanged<T>> progressChangedEvents = new ArrayList<>();

    private void triggerProgressChanged(T value) {
        for (ProgressChanged<T> pg : progressChangedEvents) {
            pg.changed(this, value);
        }
    }

    public void addProgressChangedListener(ProgressChanged<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        progressChangedEvents.add(listener);
    }

    public boolean removeProgressChangedListener(ProgressChanged<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return progressChangedEvents.remove(listener);
    }

    public void report(T value) {
        triggerProgressChanged(value);
    }
}
