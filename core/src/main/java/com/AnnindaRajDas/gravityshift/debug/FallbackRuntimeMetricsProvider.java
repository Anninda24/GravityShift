package com.AnnindaRajDas.gravityshift.debug;

/**
 * Core-only fallback implementation of {@link RuntimeMetricsProvider}.
 * Reports standard JVM heap metrics via {@link Runtime#getRuntime()} and leaves
 * platform-specific GC count and native heap metrics as unavailable (-1L) rather
 * than fabricating or substituting metrics.
 */
public class FallbackRuntimeMetricsProvider implements RuntimeMetricsProvider {

    @Override
    public void sample(RuntimeMetricsSnapshot out) {
        if (out == null) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        out.javaHeapUsedBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long maxMemory = runtime.maxMemory();
        out.javaHeapMaxBytes = (maxMemory > 0L && maxMemory != Long.MAX_VALUE) ? maxMemory : -1L;
        out.gcInvocationCount = -1L;
        out.nativeHeapBytes = -1L;
    }
}
