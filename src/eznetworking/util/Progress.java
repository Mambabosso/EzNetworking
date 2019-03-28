package eznetworking.util;

import java.util.ArrayList;

public class Progress<T extends Number> {

    public interface ProgressStarted<T extends Number> {
        public void started(Progress<T> sender, T value);
    }

    public interface ProgressChanged<T extends Number> {
        public void changed(Progress<T> sender, T value);
    }

    public interface ProgressFinished<T extends Number> {
        public void finished(Progress<T> sender, T value);
    }

    private ArrayList<ProgressStarted<T>> progressStartedEvents = new ArrayList<>();
    private ArrayList<ProgressChanged<T>> progressChangedEvents = new ArrayList<>();
    private ArrayList<ProgressFinished<T>> progressFinishedEvents = new ArrayList<>();

    private void triggerProgressStarted(T value) {
        Runner.run(() -> {
            for (ProgressStarted<T> ps : progressStartedEvents) {
                ps.started(this, value);
            }
        });
    }

    public void addProgressStartedListener(ProgressStarted<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        progressStartedEvents.add(listener);
    }

    public boolean removeProgressStartedListener(ProgressStarted<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return progressStartedEvents.remove(listener);
    }

    private void triggerProgressChanged(T value) {
        Runner.run(() -> {
            for (ProgressChanged<T> pg : progressChangedEvents) {
                pg.changed(this, value);
            }
        });
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

    private void triggerProgressFinished(T value) {
        Runner.run(() -> {
            for (ProgressFinished<T> pf : progressFinishedEvents) {
                pf.finished(this, value);
            }
        });
    }

    public void addProgressFinishedListener(ProgressFinished<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        progressFinishedEvents.add(listener);
    }

    public boolean removeProgressFinishedListener(ProgressFinished<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }
        return progressFinishedEvents.remove(listener);
    }

    public void started(T value) {
        triggerProgressStarted(value);
    }

    public void changed(T value) {
        triggerProgressChanged(value);
    }

    public void finished(T value) {
        triggerProgressFinished(value);
    }

}
