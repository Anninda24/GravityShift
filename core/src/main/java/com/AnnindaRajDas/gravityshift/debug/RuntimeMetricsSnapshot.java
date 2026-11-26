package com.AnnindaRajDas.gravityshift.debug;

/**
 * Reusable value container holding sampled runtime memory and GC metrics.
 * A value of -1 indicates that the metric is unavailable on the current platform.
 */
public final class RuntimeMetricsSnapshot {

    public long gcInvocationCount = -1L;
    public long javaHeapUsedBytes = -1L;
    public long javaHeapMaxBytes = -1L;
    public long nativeHeapBytes = -1L;

    /**
     * Resets all fields to -1 (unavailable).
     */
    public void reset() {
        gcInvocationCount = -1L;
        javaHeapUsedBytes = -1L;
        javaHeapMaxBytes = -1L;
        nativeHeapBytes = -1L;
    }
}
