package com.AnnindaRajDas.gravityshift.debug;

/**
 * Platform-neutral interface for querying runtime GC and heap metrics
 * without introducing platform-specific APIs into the core module.
 */
public interface RuntimeMetricsProvider {

    /**
     * Samples current runtime metrics into the provided reusable snapshot instance.
     * Implementations must populate {@code out} in-place without allocating per-frame objects.
     * Metrics that cannot be queried reliably must be set to {@code -1L}.
     *
     * @param out preallocated snapshot container to populate
     */
    void sample(RuntimeMetricsSnapshot out);
}
