package com.AnnindaRajDas.gravityshift.debug;

/**
 * Single-writer, lock-free sequence-locked background sampler wrapping a {@link RuntimeMetricsProvider}.
 * Isolates platform metric queries (such as Android's {@code Debug.getRuntimeStat})
 * off the game/render thread onto a single controlled daemon thread.
 *
 * Memory visibility & consistency guarantees (Java Memory Model):
 * - A {@code volatile long sequence} counter coordinates a single background writer and game-thread reader.
 * - Odd {@code sequence} values indicate a write in progress; even values indicate a completed generation.
 * - The writer performs {@code delegate.sample(scratchSnapshot)} outside the seqlock window, then:
 *     1. Writes {@code sequence = seq + 1} (odd, volatile write barrier).
 *     2. Copies the four primitive {@code long} fields into {@code publishedSnapshot}.
 *     3. Writes {@code sequence = seq + 2} (even, volatile write flushes all 4 fields).
 * - The reader reads {@code seqBefore = sequence} (volatile read), copies the four fields into locals,
 *   then reads {@code seqAfter = sequence} (volatile read). If {@code seqBefore == seqAfter} and even,
 *   the volatile-read/volatile-write happens-before edge guarantees all four fields belong to the same
 *   generation with zero torn or mixed-generation reads.
 * - Retries are bounded by {@link #MAX_READ_RETRIES}; if contention persists, the previous valid
 *   values in {@code out} are retained and the game thread never blocks or allocates.
 */
public class BackgroundMetricsSampler implements RuntimeMetricsProvider {

    public static final int MAX_READ_RETRIES = 4;

    private final RuntimeMetricsProvider delegate;
    private final long sampleIntervalMs;

    private final RuntimeMetricsSnapshot scratchSnapshot = new RuntimeMetricsSnapshot();
    private final RuntimeMetricsSnapshot publishedSnapshot = new RuntimeMetricsSnapshot();
    private volatile long sequence = 0L;

    private final Object lifecycleLock = new Object();
    private volatile boolean samplingActive = false;
    private volatile boolean disposed = false;
    private volatile Thread samplerThread = null;

    public BackgroundMetricsSampler(RuntimeMetricsProvider delegate, long sampleIntervalMs) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate RuntimeMetricsProvider cannot be null.");
        }
        if (sampleIntervalMs <= 0L) {
            throw new IllegalArgumentException("sampleIntervalMs must be positive.");
        }
        this.delegate = delegate;
        this.sampleIntervalMs = sampleIntervalMs;
    }

    @Override
    public void sample(RuntimeMetricsSnapshot out) {
        if (out == null) {
            return;
        }
        trySampleConsistent(out);
    }

    /**
     * Attempts a bounded lock-free seqlock read into {@code out}.
     * If a consistent even-sequence snapshot is not observed within {@link #MAX_READ_RETRIES},
     * {@code out} is left untouched (retaining its previous valid values) and {@code false} is returned.
     *
     * @param out target snapshot to populate
     * @return true if a consistent snapshot was copied, false if bounded retries were exhausted
     */
    public boolean trySampleConsistent(RuntimeMetricsSnapshot out) {
        if (out == null) {
            return false;
        }
        for (int attempt = 0; attempt < MAX_READ_RETRIES; attempt++) {
            long seqBefore = sequence;
            if ((seqBefore & 1L) != 0L) {
                continue;
            }
            long gc = publishedSnapshot.gcInvocationCount;
            long used = publishedSnapshot.javaHeapUsedBytes;
            long max = publishedSnapshot.javaHeapMaxBytes;
            long nat = publishedSnapshot.nativeHeapBytes;
            long seqAfter = sequence;

            if (seqBefore == seqAfter) {
                out.gcInvocationCount = gc;
                out.javaHeapUsedBytes = used;
                out.javaHeapMaxBytes = max;
                out.nativeHeapBytes = nat;
                return true;
            }
        }
        return false;
    }

    @Override
    public void setSamplingEnabled(boolean enabled) {
        synchronized (lifecycleLock) {
            if (disposed) {
                return;
            }
            if (enabled == samplingActive) {
                return;
            }
            samplingActive = enabled;
            if (enabled) {
                startThreadLocked();
            } else {
                stopThreadLocked();
            }
        }
    }

    @Override
    public void dispose() {
        synchronized (lifecycleLock) {
            if (disposed) {
                return;
            }
            disposed = true;
            samplingActive = false;
            stopThreadLocked();
        }
    }

    public boolean isSamplingActive() {
        return samplingActive && !disposed;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public long getSequence() {
        return sequence;
    }

    private void startThreadLocked() {
        if (samplerThread != null && samplerThread.isAlive()) {
            return;
        }
        Thread t = new Thread(this::runSamplingLoop, "RuntimeMetricsSampler");
        t.setDaemon(true);
        samplerThread = t;
        t.start();
    }

    private void stopThreadLocked() {
        Thread t = samplerThread;
        if (t != null) {
            samplerThread = null;
            t.interrupt();
        }
    }

    private void runSamplingLoop() {
        Thread self = Thread.currentThread();
        while (samplingActive && !disposed && samplerThread == self) {
            try {
                scratchSnapshot.reset();
                delegate.sample(scratchSnapshot);
            } catch (Exception ignored) {
                // Non-fatal provider exceptions must never crash gameplay; publish N/A (-1L).
                // Fatal Errors (OutOfMemoryError, ThreadDeath) are intentionally not caught.
                scratchSnapshot.reset();
            }

            if (!samplingActive || disposed || samplerThread != self) {
                break;
            }

            // Single-writer seqlock publication
            long currentSeq = sequence;
            sequence = currentSeq + 1L; // odd: write in progress
            publishedSnapshot.gcInvocationCount = scratchSnapshot.gcInvocationCount;
            publishedSnapshot.javaHeapUsedBytes = scratchSnapshot.javaHeapUsedBytes;
            publishedSnapshot.javaHeapMaxBytes = scratchSnapshot.javaHeapMaxBytes;
            publishedSnapshot.nativeHeapBytes = scratchSnapshot.nativeHeapBytes;
            sequence = currentSeq + 2L; // even: generation complete

            try {
                Thread.sleep(sampleIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
