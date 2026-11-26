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

    /**
     * Optional lifecycle hook notifying the provider whether overlay sampling is active.
     * Providers that use background sampling should start/stop sampling accordingly.
     *
     * @param enabled true if the debug overlay is enabled, false if disabled
     */
    default void setSamplingEnabled(boolean enabled) {
    }

    /**
     * Optional lifecycle hook to release any background sampling resources on disposal.
     */
    default void dispose() {
    }
}
