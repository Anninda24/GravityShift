package com.AnnindaRajDas.gravityshift.android;

import android.os.Build;
import android.os.Debug;
import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsSnapshot;

/**
 * Android implementation of {@link RuntimeMetricsProvider}.
 * Queries ART GC invocation count via {@link Debug#getRuntimeStat(String)} (API 23+),
 * native heap via {@link Debug#getNativeHeapAllocatedSize()}, and Java heap via {@link Runtime}.
 */
public class AndroidRuntimeMetricsProvider implements RuntimeMetricsProvider {

    private static final String ART_GC_COUNT_STAT = "art.gc.gc-count";

    @Override
    public void sample(RuntimeMetricsSnapshot out) {
        if (out == null) {
            return;
        }

        // 1. Java heap used & max
        Runtime runtime = Runtime.getRuntime();
        out.javaHeapUsedBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long maxMem = runtime.maxMemory();
        out.javaHeapMaxBytes = (maxMem > 0L && maxMem != Long.MAX_VALUE) ? maxMem : -1L;

        // 2. Native heap allocation (includes Box2D C++ native memory)
        long nativeAllocated = Debug.getNativeHeapAllocatedSize();
        out.nativeHeapBytes = (nativeAllocated >= 0L) ? nativeAllocated : -1L;

        // 3. ART GC invocation count (API 23+); return -1L if unsupported rather than fabricating
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            out.gcInvocationCount = parseNonNegativeLong(Debug.getRuntimeStat(ART_GC_COUNT_STAT));
        } else {
            out.gcInvocationCount = -1L;
        }
    }

    /**
     * Parses a decimal non-negative integer string without allocating exceptions on invalid input.
     */
    private static long parseNonNegativeLong(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -1L;
        }
        long value = 0L;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                return -1L;
            }
            value = value * 10L + (c - '0');
        }
        return value;
    }
}
