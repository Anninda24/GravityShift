package com.AnnindaRajDas.gravityshift.debug;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-worker background metrics sampler wrapping a {@link RuntimeMetricsProvider}.
 * Isolates platform metric queries (such as Android's {@code Debug.getRuntimeStat})
 * off the game/render thread onto a single controlled daemon thread.
 *
 * Snapshot publication & JMM safety:
 * - Sampled metrics are published as {@link ImmutableMetricsSnapshot} instances with {@code final}
 *   primitive fields via {@link AtomicReference#set(Object)}.
 * - The game thread's {@link #sample(RuntimeMetricsSnapshot)} performs a single non-blocking
 *   {@link AtomicReference#get()} and copies the four {@code final long} fields into the caller's
 *   reusable {@code out} snapshot, performing zero allocations and never blocking.
 *
 * Worker lifecycle guarantees:
 * - Explicit {@link WorkerState} ({@code DISABLED_STOPPED}, {@code ENABLED_RUNNING},
 *   {@code DISABLED_TERMINATING}, {@code DISPOSED}) prevents overlapping workers during rapid
 *   disable/re-enable cycles even when a provider call is non-interruptible.
 */
public class BackgroundMetricsSampler implements RuntimeMetricsProvider {

    public enum WorkerState {
        DISABLED_STOPPED,
        ENABLED_RUNNING,
        DISABLED_TERMINATING,
        DISPOSED
    }

    /**
     * Immutable snapshot published atomically from the background sampler thread.
     * All fields are {@code final} primitives, guaranteeing safe publication under the JMM.
     */
    public static final class ImmutableMetricsSnapshot {
        public static final ImmutableMetricsSnapshot UNAVAILABLE =
            new ImmutableMetricsSnapshot(-1L, -1L, -1L, -1L);

        public final long gcInvocationCount;
        public final long javaHeapUsedBytes;
        public final long javaHeapMaxBytes;
        public final long nativeHeapBytes;

        public ImmutableMetricsSnapshot(long gcInvocationCount,
                                        long javaHeapUsedBytes,
                                        long javaHeapMaxBytes,
                                        long nativeHeapBytes) {
            this.gcInvocationCount = gcInvocationCount;
            this.javaHeapUsedBytes = javaHeapUsedBytes;
            this.javaHeapMaxBytes = javaHeapMaxBytes;
            this.nativeHeapBytes = nativeHeapBytes;
        }
    }

    private final RuntimeMetricsProvider delegate;
    private final long sampleIntervalMs;

    private final RuntimeMetricsSnapshot scratchSnapshot = new RuntimeMetricsSnapshot();
    private final AtomicReference<ImmutableMetricsSnapshot> latestSnapshot =
        new AtomicReference<>(ImmutableMetricsSnapshot.UNAVAILABLE);

    private final Object lifecycleLock = new Object();
    private WorkerState state = WorkerState.DISABLED_STOPPED;
    private boolean samplingDesired = false;
    private Thread samplerThread = null;

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
        ImmutableMetricsSnapshot snap = latestSnapshot.get();
        out.gcInvocationCount = snap.gcInvocationCount;
        out.javaHeapUsedBytes = snap.javaHeapUsedBytes;
        out.javaHeapMaxBytes = snap.javaHeapMaxBytes;
        out.nativeHeapBytes = snap.nativeHeapBytes;
    }

    /**
     * Returns the current immutable snapshot reference (for inspection and testing).
     */
    public ImmutableMetricsSnapshot getLatestImmutableSnapshot() {
        return latestSnapshot.get();
    }

    @Override
    public void setSamplingEnabled(boolean enabled) {
        synchronized (lifecycleLock) {
            if (state == WorkerState.DISPOSED) {
                return;
            }
            samplingDesired = enabled;
            if (enabled) {
                if (state == WorkerState.DISABLED_STOPPED && samplerThread == null) {
                    state = WorkerState.ENABLED_RUNNING;
                    startThreadLocked();
                }
                // If DISABLED_TERMINATING, do NOT start a second worker; the exiting worker's
                // finally block will observe samplingDesired == true and start the replacement.
            } else {
                if (state == WorkerState.ENABLED_RUNNING) {
                    state = WorkerState.DISABLED_TERMINATING;
                    if (samplerThread != null) {
                        samplerThread.interrupt();
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        synchronized (lifecycleLock) {
            if (state == WorkerState.DISPOSED) {
                return;
            }
            samplingDesired = false;
            state = WorkerState.DISPOSED;
            if (samplerThread != null) {
                samplerThread.interrupt();
            }
        }
    }

    public WorkerState getWorkerState() {
        synchronized (lifecycleLock) {
            return state;
        }
    }

    public boolean isSamplingActive() {
        synchronized (lifecycleLock) {
            return state == WorkerState.ENABLED_RUNNING && samplingDesired;
        }
    }

    public boolean isDisposed() {
        synchronized (lifecycleLock) {
            return state == WorkerState.DISPOSED;
        }
    }

    private void startThreadLocked() {
        Thread t = new Thread(this::runSamplingLoop, "RuntimeMetricsSampler");
        t.setDaemon(true);
        samplerThread = t;
        t.start();
    }

    private void runSamplingLoop() {
        try {
            while (true) {
                synchronized (lifecycleLock) {
                    if (!samplingDesired || state != WorkerState.ENABLED_RUNNING) {
                        break;
                    }
                }

                try {
                    scratchSnapshot.reset();
                    delegate.sample(scratchSnapshot);
                    latestSnapshot.set(new ImmutableMetricsSnapshot(
                        scratchSnapshot.gcInvocationCount,
                        scratchSnapshot.javaHeapUsedBytes,
                        scratchSnapshot.javaHeapMaxBytes,
                        scratchSnapshot.nativeHeapBytes
                    ));
                } catch (Exception ignored) {
                    // Non-fatal provider exceptions must never crash gameplay; publish UNAVAILABLE.
                    // Fatal Errors (OutOfMemoryError, ThreadDeath) are intentionally not caught.
                    latestSnapshot.set(ImmutableMetricsSnapshot.UNAVAILABLE);
                }

                synchronized (lifecycleLock) {
                    if (!samplingDesired || state != WorkerState.ENABLED_RUNNING) {
                        break;
                    }
                }

                try {
                    Thread.sleep(sampleIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            synchronized (lifecycleLock) {
                samplerThread = null;
                if (state == WorkerState.DISPOSED) {
                    // Stay DISPOSED; never spawn another worker
                } else if (samplingDesired) {
                    state = WorkerState.ENABLED_RUNNING;
                    startThreadLocked();
                } else {
                    state = WorkerState.DISABLED_STOPPED;
                }
            }
        }
    }
}
