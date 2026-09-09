package org.portmaster.gunslugs;

import java.util.concurrent.locks.LockSupport;

/** Match C.a.f's Android 24 ms minimum update interval, even without vsync. */
public final class FramePacer {
    private final long interval;
    private long previous;
    public FramePacer(int millis) {
        if (millis < 16 || millis > 100) throw new IllegalArgumentException("frameMillis must be 16..100");
        interval = millis * 1000000L;
    }
    public void reset() { previous = 0; }
    public void awaitFrame() {
        long now = System.nanoTime();
        if (previous != 0) {
            long remaining;
            while ((remaining = interval - (now - previous)) > 0) {
                LockSupport.parkNanos(remaining);
                if (Thread.currentThread().isInterrupted()) break;
                now = System.nanoTime();
            }
        }
        // Never accumulate missed frames or burst through updates after suspend.
        previous = now;
    }
}
