package com.AnnindaRajDas.gravityshift.lwjgl3;

import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsSnapshot;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Desktop (LWJGL3 / standard JVM) implementation of {@link RuntimeMetricsProvider}.
 * Queries GC counts from cached {@link GarbageCollectorMXBean} instances and Java heap
 * from {@link Runtime#getRuntime()}. Native C/C++ heap is not directly exposed by standard
 * JVM APIs without non-portable instrumentation, so {@code nativeHeapBytes} reports -1 (N/A)
 * rather than substituting Java heap metrics.
 */
public class DesktopRuntimeMetricsProvider implements RuntimeMetricsProvider {

    private final List<GarbageCollectorMXBean> gcBeans;

    public DesktopRuntimeMetricsProvider() {
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }

    @Override
    public void sample(RuntimeMetricsSnapshot out) {
        if (out == null) {
            return;
        }

        // 1. Java heap used & max (avoids MemoryUsage allocation from MemoryMXBean)
        Runtime runtime = Runtime.getRuntime();
        out.javaHeapUsedBytes = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long maxMem = runtime.maxMemory();
        out.javaHeapMaxBytes = (maxMem > 0L && maxMem != Long.MAX_VALUE) ? maxMem : -1L;

        // 2. Total JVM GC collection count across active collectors
        long totalGcCount = 0L;
        boolean hasValidCount = false;
        if (gcBeans != null) {
            for (int i = 0; i < gcBeans.size(); i++) {
                long count = gcBeans.get(i).getCollectionCount();
                if (count >= 0L) {
                    totalGcCount += count;
                    hasValidCount = true;
                }
            }
        }
        out.gcInvocationCount = hasValidCount ? totalGcCount : -1L;

        // 3. C/C++ native heap (Box2D) is not reliably exposed on standard JVM -> report N/A (-1L)
        out.nativeHeapBytes = -1L;
    }
}
