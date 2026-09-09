package org.portmaster.gunslugs;

public final class VerifyTiming {
    public static void main(String[] args) throws Exception {
        FramePacer pacer = new FramePacer(24);
        long start = System.nanoTime();
        for (int i = 0; i < 101; i++) pacer.awaitFrame();
        double seconds = (System.nanoTime() - start) / 1e9;
        if (seconds < 2.4) throw new AssertionError("Game updates ran too fast: " + seconds);
        Thread.sleep(100);
        pacer.awaitFrame();
        start = System.nanoTime();
        pacer.awaitFrame();
        if (System.nanoTime() - start < 24000000L) throw new AssertionError("Catch-up burst after stall");
        pacer.reset();
        pacer.awaitFrame();
        System.out.println("TIMING_OK 100 intervals in " + seconds + " seconds; no catch-up burst");
    }
}
