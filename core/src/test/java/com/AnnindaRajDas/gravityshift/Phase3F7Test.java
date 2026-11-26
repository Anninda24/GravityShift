package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.debug.BackgroundMetricsSampler;
import com.AnnindaRajDas.gravityshift.debug.DebugOverlay;
import com.AnnindaRajDas.gravityshift.debug.FallbackRuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsSnapshot;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.physics.GameContactListener;
import com.AnnindaRajDas.gravityshift.physics.GravityController;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for Phase 3F.7:
 * Allocation Discipline & Runtime Debug Overlay.
 *
 * Covers:
 * 1-4. Core physics/movement/jump/gravity-cap/support regressions remain unchanged.
 * 5. Hot-path reuse invariants (Direction.VALUES identity, Entity interpolation vector reuse,
 *    zero per-step managed heap allocations measured via JVM ThreadMXBean after warmup).
 * 6. DebugOverlay enable/disable toggle behavior.
 * 7. RuntimeMetricsProvider handling of unavailable (-1L) metrics cleanly displaying N/A.
 * 8. Clear distinction between Java heap used and Java heap max.
 * 9. Low-frequency overlay sampling (does not query provider on every frame).
 * 10. Reusable CharSequence identity across repeated samples (no temporary Strings).
 * 11. Safe idempotent disposal and resize handling.
 * 12. GravityShiftGame metrics provider injection and fallback defaults.
 */
public class Phase3F7Test {

    private static final float EPS = 1e-4f;
    private static final float DT = GameConfig.TIME_STEP;

    @BeforeAll
    public static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    // 1. Existing movement and impulse behavior remains unchanged
    @Test
    public void test01_ImpulseMovementUnchanged() {
        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);

        Player player = Player.create(pc.getBodyFactory(), 5.0f, 1.565f);
        em.addEntity(player);

        for (int i = 0; i < 20; i++) pc.update(DT);
        assertTrue(player.isGrounded());

        player.setMoveDirection(1.0f);
        for (int i = 0; i < 15; i++) pc.update(DT);
        assertTrue(player.getBody().getLinearVelocity().x > 4.5f);

        pc.dispose();
    }

    // 2. Gravity switching preserves direction and enforces 15 m/s magnitude cap
    @Test
    public void test02_GravitySwitchMomentumCapUnchanged() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);

        player.getBody().setLinearVelocity(18.0f, -24.0f); // speed 30 -> (9, -12)
        pc.getGravityController().setGravityDirection(Direction.LEFT);
        player.onGravityDirectionChanged(Direction.LEFT);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(9.0f, vel.x, EPS);
        assertEquals(-12.0f, vel.y, EPS);
        assertTrue(vel.len() <= GameConfig.MAX_GRAVITY_SWITCH_SPEED);

        pc.dispose();
    }

    // 3. Jump, coyote time, and jump buffering remain intact
    @Test
    public void test03_JumpCoyoteAndBufferingUnchanged() {
        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);

        Player player = Player.create(pc.getBodyFactory(), 5.0f, 1.565f);
        em.addEntity(player);
        player.getBody().setLinearVelocity(0f, -2.0f);

        assertFalse(player.jump());
        assertTrue(player.isJumpBuffered());

        for (int i = 0; i < 6; i++) pc.update(DT);
        assertTrue(player.isGrounded());
        assertTrue(player.isJumpConsumed());
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        pc.dispose();
    }

    // 4. Support tracking and moving-platform behavior remain unchanged
    @Test
    public void test04_MovingPlatformSupportUnchanged() {
        World world = new World(new Vector2(0f, -9.81f), true);
        world.setContactListener(new GameContactListener());
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(10.0f, 0.5f);
        Body platform = world.createBody(bd);
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(8.0f, 0.5f);
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.friction = 0.4f;
        platform.createFixture(fd).setUserData(FixtureType.GROUND);
        shape.dispose();
        platform.setLinearVelocity(2.5f, 0.0f);

        Player player = Player.create(new com.AnnindaRajDas.gravityshift.physics.BodyFactory(world), 5.0f, 1.0f);
        player.setMoveDirection(0.0f);
        for (int i = 0; i < 20; i++) {
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
        }
        assertSame(platform, player.getActiveSupportingBody());
        assertEquals(2.5f, player.getBody().getLinearVelocity().x, 0.1f);

        world.dispose();
    }

    // 5. Hot-path vector reuse & safe Direction indexed access & JVM thread allocation check
    @Test
    public void test05_HotPathReuseAndThreadAllocationCheck() {
        // Direction.count() / fromIndex() avoids exposing a public mutable array or calling Enum.values()
        assertEquals(4, Direction.count());
        assertSame(Direction.DOWN, Direction.fromIndex(0));
        assertSame(Direction.UP, Direction.fromIndex(1));
        assertSame(Direction.LEFT, Direction.fromIndex(2));
        assertSame(Direction.RIGHT, Direction.fromIndex(3));

        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        em.addEntity(player);

        // Interpolated position reuses the same internal Vector2 instance
        Vector2 p1 = player.getInterpolatedPosition(0.25f);
        Vector2 p2 = player.getInterpolatedPosition(0.75f);
        assertSame(p1, p2, "getInterpolatedPosition must reuse internal Vector2");

        RuntimeMetricsProvider mockProvider = out -> {
            out.gcInvocationCount = 3L;
            out.javaHeapUsedBytes = 10L * 1024L * 1024L;
            out.javaHeapMaxBytes = 128L * 1024L * 1024L;
            out.nativeHeapBytes = 4L * 1024L * 1024L;
        };
        DebugOverlay overlay = new DebugOverlay(mockProvider, true, 0.5f);

        // Warmup loop so JIT/caches settle
        player.setMoveDirection(1.0f);
        for (int i = 0; i < 60; i++) {
            em.update(DT);
            pc.update(DT);
            overlay.update(DT, 60);
        }

        // Measure allocations on current thread during steady-state physics + update + overlay loop
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean threadBean && threadBean.isThreadAllocatedMemorySupported()) {
            long tid = Thread.currentThread().threadId();
            long beforeBytes = threadBean.getThreadAllocatedBytes(tid);

            for (int i = 0; i < 300; i++) {
                player.setMoveDirection((i % 20 < 10) ? 1.0f : -1.0f);
                em.update(DT);
                pc.update(DT);
                overlay.update(DT, 60);
            }

            long afterBytes = threadBean.getThreadAllocatedBytes(tid);
            assertEquals(0L, afterBytes - beforeBytes,
                "300 steady-state update+physics+overlay iterations must allocate 0 bytes on JVM thread");
        }

        overlay.dispose();
        pc.dispose();
    }

    // 6. DebugOverlay enable/disable safely suppresses sampling when disabled
    @Test
    public void test06_DebugOverlayEnableDisableSafety() {
        final int[] calls = { 0 };
        RuntimeMetricsProvider countingProvider = out -> {
            calls[0]++;
            out.gcInvocationCount = calls[0];
            out.javaHeapUsedBytes = 5L * 1024L * 1024L;
            out.javaHeapMaxBytes = 64L * 1024L * 1024L;
            out.nativeHeapBytes = 2L * 1024L * 1024L;
        };

        DebugOverlay overlay = new DebugOverlay(countingProvider, false, 0.5f);
        assertFalse(overlay.isEnabled());

        for (int i = 0; i < 120; i++) {
            overlay.update(DT, 60);
        }
        assertEquals(0, calls[0], "Disabled overlay must not invoke metricsProvider");
        assertEquals(0, overlay.getSampleCount());

        // Enable overlay -> next update immediately samples once
        overlay.setEnabled(true);
        assertTrue(overlay.isEnabled());
        overlay.update(DT, 60);
        assertEquals(1, calls[0], "Enabling overlay must trigger immediate initial sample");

        // Disable again -> no further samples
        overlay.setEnabled(false);
        for (int i = 0; i < 60; i++) {
            overlay.update(DT, 60);
        }
        assertEquals(1, calls[0], "Re-disabled overlay must stop sampling");

        overlay.dispose();
    }

    // 7. Metrics provider unavailable (-1L) metrics display N/A cleanly without crashing
    @Test
    public void test07_UnavailableMetricsDisplayNAWithoutCrashing() {
        RuntimeMetricsProvider unavailableProvider = out -> {
            out.gcInvocationCount = -1L;
            out.javaHeapUsedBytes = -1L;
            out.javaHeapMaxBytes = -1L;
            out.nativeHeapBytes = -1L;
        };

        DebugOverlay overlay = new DebugOverlay(unavailableProvider, true, 0.5f);
        overlay.update(DT, 60);

        String text = overlay.getDisplayText().toString();
        assertTrue(text.contains("FPS: 60"));
        assertTrue(text.contains("GC: N/A"));
        assertTrue(text.contains("Heap: N/A"));
        assertTrue(text.contains("Native: N/A"));

        // Partial availability: used heap present, max unavailable
        RuntimeMetricsProvider partialProvider = out -> {
            out.gcInvocationCount = 7L;
            out.javaHeapUsedBytes = 12L * 1024L * 1024L + (512L * 1024L); // 12.5 MB
            out.javaHeapMaxBytes = -1L;
            out.nativeHeapBytes = -1L;
        };
        DebugOverlay partialOverlay = new DebugOverlay(partialProvider, true, 0.5f);
        partialOverlay.update(DT, 120);

        String partialText = partialOverlay.getDisplayText().toString();
        assertTrue(partialText.contains("FPS: 120"));
        assertTrue(partialText.contains("GC: 7"));
        assertTrue(partialText.contains("Heap: 12.5 MB / max N/A"));
        assertTrue(partialText.contains("Native: N/A"));

        overlay.dispose();
        partialOverlay.dispose();
    }

    // 8. Clear distinction between Java heap used and Java heap max
    @Test
    public void test08_JavaHeapUsedAndMaxClearlyDistinguished() {
        RuntimeMetricsProvider provider = out -> {
            out.gcInvocationCount = 4L;
            out.javaHeapUsedBytes = 14L * 1024L * 1024L;
            out.javaHeapMaxBytes = 256L * 1024L * 1024L;
            out.nativeHeapBytes = 8L * 1024L * 1024L;
        };

        DebugOverlay overlay = new DebugOverlay(provider, true, 0.5f);
        overlay.update(DT, 90);

        String text = overlay.getDisplayText().toString();
        assertTrue(text.contains("Heap: 14.0 MB / 256.0 MB"),
            "Overlay must clearly display used / max heap: " + text);
        assertTrue(text.contains("Native: 8.0 MB"));
        assertTrue(text.contains("GC: 4"));

        overlay.dispose();
    }

    // 9. Low-frequency sampling: 60 frames over 1.0s at 0.5s interval triggers only 3 samples (t=0, 0.5, 1.0)
    @Test
    public void test09_LowFrequencySamplingDoesNotQueryEveryFrame() {
        final int[] sampleCalls = { 0 };
        RuntimeMetricsProvider provider = out -> {
            sampleCalls[0]++;
            out.gcInvocationCount = sampleCalls[0];
        };

        DebugOverlay overlay = new DebugOverlay(provider, true, 0.5f);

        // First frame -> initial sample (count = 1)
        overlay.update(DT, 60);
        assertEquals(1, sampleCalls[0]);

        // Next 29 frames (< 0.5s total) -> must NOT sample again
        for (int i = 0; i < 29; i++) {
            overlay.update(DT, 60);
        }
        assertEquals(1, sampleCalls[0], "Must not sample before 0.5s interval elapses");

        // 1 more frame reaches 30 * (1/60) = 0.5s -> triggers 2nd sample
        overlay.update(DT, 60);
        assertEquals(2, sampleCalls[0], "Must trigger 2nd sample at 0.5s");

        overlay.dispose();
    }

    // 10. Reusable CharSequence identity preserved across samples (no temporary String/buffer swaps)
    @Test
    public void test10_ReusableTextBufferIdentityPreservedAcrossSamples() {
        RuntimeMetricsProvider provider = new FallbackRuntimeMetricsProvider();
        DebugOverlay overlay = new DebugOverlay(provider, true, 0.1f);

        CharSequence initialRef = overlay.getDisplayText();
        for (int i = 0; i < 30; i++) {
            overlay.update(0.1f, 60 + i);
            assertSame(initialRef, overlay.getDisplayText(),
                "Display text CharSequence buffer identity must never be replaced");
        }
        assertTrue(overlay.getSampleCount() >= 30);

        overlay.dispose();
    }

    // 11. Disposal and resize safety
    @Test
    public void test11_DisposalIdempotentAndResizeSafe() {
        DebugOverlay overlay = new DebugOverlay(new FallbackRuntimeMetricsProvider(), true);
        overlay.resize(2344, 1080);
        overlay.update(DT, 120);

        overlay.dispose();
        assertTrue(overlay.isDisposed());
        assertFalse(overlay.isEnabled());

        // Safe no-ops after disposal
        overlay.resize(1920, 1080);
        overlay.update(DT, 120);
        overlay.render(null);
        overlay.dispose();
        assertTrue(overlay.isDisposed());

        // FallbackMetricsProvider handles null safely
        FallbackRuntimeMetricsProvider fallback = new FallbackRuntimeMetricsProvider();
        fallback.sample(null);
        RuntimeMetricsSnapshot snap = new RuntimeMetricsSnapshot();
        fallback.sample(snap);
        assertTrue(snap.javaHeapUsedBytes >= 0L);
        assertEquals(-1L, snap.gcInvocationCount);
        assertEquals(-1L, snap.nativeHeapBytes);
    }

    // 12. GravityShiftGame metrics provider wiring and defensive null handling
    @Test
    public void test12_GameMetricsProviderWiringAndConstructorGuards() {
        GravityShiftGame defaultGame = new GravityShiftGame();
        assertNotNull(defaultGame.getMetricsProvider());
        assertTrue(defaultGame.getMetricsProvider() instanceof FallbackRuntimeMetricsProvider);

        RuntimeMetricsProvider custom = out -> out.gcInvocationCount = 99L;
        GravityShiftGame customGame = new GravityShiftGame(custom);
        assertSame(custom, customGame.getMetricsProvider());

        GravityShiftGame nullSafeGame = new GravityShiftGame(null);
        assertNotNull(nullSafeGame.getMetricsProvider());

        assertThrows(IllegalArgumentException.class, () -> new DebugOverlay(null, true));
        assertThrows(IllegalArgumentException.class, () -> new DebugOverlay(custom, true, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> new DebugOverlay(custom, true, -1.0f));
    }

    // 13. BackgroundMetricsSampler executes off game thread and game-thread reads never block
    @Test
    public void test13_BackgroundSampler_ExecutesOffGameThreadAndNonBlockingReads() throws InterruptedException {
        final Thread[] samplingThread = { null };
        final int[] sampleCalls = { 0 };
        Thread testThread = Thread.currentThread();

        RuntimeMetricsProvider slowDelegate = out -> {
            samplingThread[0] = Thread.currentThread();
            sampleCalls[0]++;
            out.gcInvocationCount = 42L;
            out.javaHeapUsedBytes = 16L * 1024L * 1024L;
            out.javaHeapMaxBytes = 256L * 1024L * 1024L;
            out.nativeHeapBytes = 8L * 1024L * 1024L;
        };

        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(slowDelegate, 40L);
        DebugOverlay overlay = new DebugOverlay(sampler, true, 0.05f);

        // Wait briefly for background thread to publish at least one sample
        long deadline = System.currentTimeMillis() + 1000L;
        while (sampleCalls[0] == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }

        assertTrue(sampleCalls[0] >= 1, "Background sampler must execute");
        assertNotNull(samplingThread[0]);
        assertNotSame(testThread, samplingThread[0],
            "Platform metric sampling must NOT execute on the game/test thread");

        // Game-thread read must be immediate, non-blocking, and reflect published values
        RuntimeMetricsSnapshot out = new RuntimeMetricsSnapshot();
        long t0 = System.nanoTime();
        sampler.sample(out);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(elapsedMs < 10L, "Game-thread sample() must never block");
        assertEquals(42L, out.gcInvocationCount);
        assertEquals(16L * 1024L * 1024L, out.javaHeapUsedBytes);

        overlay.dispose();
        assertTrue(sampler.isDisposed());
    }

    // 14. Disabling overlay stops background sampling and disposal releases sampler cleanly
    @Test
    public void test14_BackgroundSampler_DisableStopsSamplingAndDisposalReleases() throws InterruptedException {
        final int[] calls = { 0 };
        RuntimeMetricsProvider counting = out -> {
            calls[0]++;
            out.gcInvocationCount = calls[0];
        };

        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(counting, 25L);
        DebugOverlay overlay = new DebugOverlay(sampler, true, 0.05f);

        long deadline = System.currentTimeMillis() + 1000L;
        while (calls[0] < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(calls[0] >= 2);

        // Disable overlay -> stops background sampling
        overlay.setEnabled(false);
        assertFalse(sampler.isSamplingActive());
        Thread.sleep(60L);
        int countAfterDisable = calls[0];
        Thread.sleep(100L);
        assertEquals(countAfterDisable, calls[0], "Disabled overlay must stop background sampling");

        overlay.dispose();
        assertTrue(sampler.isDisposed());
        assertFalse(sampler.isSamplingActive());
    }

    // 15. Background provider exceptions never crash gameplay and safely report N/A
    @Test
    public void test15_BackgroundSampler_ProviderExceptionNeverCrashesGameplay() throws InterruptedException {
        RuntimeMetricsProvider failing = out -> {
            throw new RuntimeException("Simulated platform metric failure");
        };

        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(failing, 25L);
        DebugOverlay overlay = new DebugOverlay(sampler, true, 0.05f);

        Thread.sleep(80L);
        overlay.update(DT, 60);

        String text = overlay.getDisplayText().toString();
        assertTrue(text.contains("GC: N/A"));
        assertTrue(text.contains("Heap: N/A"));
        assertTrue(text.contains("Native: N/A"));

        overlay.dispose();
    }

    // 16. Sustained concurrent writer/reader stress test: immutable generations never mix
    @Test
    public void test16_ImmutableSnapshot_SustainedConcurrentWriterReader_NoMixedGenerations() {
        final long[] gen = { 0L };
        RuntimeMetricsProvider correlatedWriter = out -> {
            long g = ++gen[0];
            out.gcInvocationCount = g;
            out.javaHeapUsedBytes = g * 1000L;
            out.javaHeapMaxBytes = g * 2000L;
            out.nativeHeapBytes = g * 3000L;
        };

        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(correlatedWriter, 1L);
        sampler.setSamplingEnabled(true);

        RuntimeMetricsSnapshot out = new RuntimeMetricsSnapshot();
        long lastGc = -1L;
        int observedGenerations = 0;

        for (int i = 0; i < 50_000; i++) {
            sampler.sample(out);
            if (out.gcInvocationCount > 0L) {
                long g = out.gcInvocationCount;
                assertEquals(g * 1000L, out.javaHeapUsedBytes, "Torn javaHeapUsedBytes for gen " + g);
                assertEquals(g * 2000L, out.javaHeapMaxBytes, "Torn javaHeapMaxBytes for gen " + g);
                assertEquals(g * 3000L, out.nativeHeapBytes, "Torn nativeHeapBytes for gen " + g);
                assertTrue(g >= lastGc, "Generation must never regress");
                if (g > lastGc) {
                    observedGenerations++;
                    lastGc = g;
                }
            }
            if (i % 1000 == 0) {
                Thread.yield();
            }
        }

        assertTrue(observedGenerations >= 2, "Reader must observe multiple immutable generations");
        sampler.dispose();
    }

    // 17. Game-thread sample() is non-blocking and zero-allocation
    @Test
    public void test17_ImmutableSnapshot_GameThreadReadZeroAllocationsAndNonBlocking() throws InterruptedException {
        RuntimeMetricsProvider fastWriter = out -> {
            out.gcInvocationCount = 100L;
            out.javaHeapUsedBytes = 200L;
            out.javaHeapMaxBytes = 300L;
            out.nativeHeapBytes = 400L;
        };

        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(fastWriter, 2L);
        sampler.setSamplingEnabled(true);
        Thread.sleep(20L);

        RuntimeMetricsSnapshot out = new RuntimeMetricsSnapshot();
        for (int i = 0; i < 100; i++) sampler.sample(out); // warmup

        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean threadBean && threadBean.isThreadAllocatedMemorySupported()) {
            long tid = Thread.currentThread().threadId();
            long before = threadBean.getThreadAllocatedBytes(tid);
            for (int i = 0; i < 10_000; i++) {
                sampler.sample(out);
            }
            long after = threadBean.getThreadAllocatedBytes(tid);
            assertEquals(0L, after - before, "Game-thread sample() must allocate 0 bytes");
        }

        assertEquals(100L, out.gcInvocationCount);
        assertEquals(200L, out.javaHeapUsedBytes);
        sampler.dispose();
    }

    // 18. Rapid enable/disable never creates duplicate sampler threads
    @Test
    public void test18_BackgroundSampler_RapidToggleNeverCreatesDuplicateWorkers() throws InterruptedException {
        RuntimeMetricsProvider noop = out -> out.gcInvocationCount = 1L;
        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(noop, 20L);

        for (int i = 0; i < 20; i++) {
            sampler.setSamplingEnabled(true);
            sampler.setSamplingEnabled(false);
        }
        sampler.setSamplingEnabled(true);
        Thread.sleep(50L);

        int aliveSamplers = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("RuntimeMetricsSampler".equals(t.getName()) && t.isAlive()) {
                aliveSamplers++;
            }
        }
        assertTrue(aliveSamplers <= 1, "Must never spawn duplicate RuntimeMetricsSampler threads, found " + aliveSamplers);

        sampler.dispose();
        Thread.sleep(60L);
        assertTrue(sampler.isDisposed());
        assertFalse(sampler.isSamplingActive());
    }

    // 19. Deterministic latch test: disable + re-enable during blocked provider call never overlaps workers
    @Test
    public void test19_WorkerLifecycle_DisableAndReenableDuringBlockedCall_NeverOverlaps() throws InterruptedException {
        CountDownLatch firstWorkerEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        CountDownLatch secondWorkerEntered = new CountDownLatch(1);
        AtomicInteger activeWorkers = new AtomicInteger(0);
        AtomicInteger maxConcurrentWorkers = new AtomicInteger(0);
        AtomicInteger totalCalls = new AtomicInteger(0);

        RuntimeMetricsProvider blockingDelegate = out -> {
            int concurrent = activeWorkers.incrementAndGet();
            maxConcurrentWorkers.accumulateAndGet(concurrent, Math::max);
            int callNum = totalCalls.incrementAndGet();
            try {
                if (callNum == 1) {
                    firstWorkerEntered.countDown();
                    try {
                        releaseFirstWorker.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        // Simulate non-interruptible provider call finishing naturally
                    }
                } else {
                    secondWorkerEntered.countDown();
                }
                out.gcInvocationCount = callNum;
            } finally {
                activeWorkers.decrementAndGet();
            }
        };

        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(blockingDelegate, 10L);
        sampler.setSamplingEnabled(true);
        assertTrue(firstWorkerEntered.await(1, TimeUnit.SECONDS), "First worker must enter provider call");

        // Disable while first worker is blocked -> must enter DISABLED_TERMINATING
        sampler.setSamplingEnabled(false);
        assertEquals(BackgroundMetricsSampler.WorkerState.DISABLED_TERMINATING, sampler.getWorkerState());

        // Re-enable BEFORE first worker finishes -> must NOT start a second worker!
        sampler.setSamplingEnabled(true);
        assertEquals(BackgroundMetricsSampler.WorkerState.DISABLED_TERMINATING, sampler.getWorkerState());
        assertEquals(1, activeWorkers.get(), "No second worker may start while first worker is still alive");

        // Release first worker -> exits, then replacement worker starts cleanly
        releaseFirstWorker.countDown();
        assertTrue(secondWorkerEntered.await(1, TimeUnit.SECONDS),
            "Replacement worker must start after first worker finishes when samplingDesired=true");
        assertEquals(1, maxConcurrentWorkers.get(), "Max concurrent workers must strictly remain 1");

        sampler.dispose();
    }

    // 20. Deterministic latch test: disposal during blocked provider call prevents future worker creation
    @Test
    public void test20_WorkerLifecycle_DisposalDuringBlockedCall_NeverSpawnsReplacement() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        RuntimeMetricsProvider blockingDelegate = out -> {
            callCount.incrementAndGet();
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        };

        BackgroundMetricsSampler sampler = new BackgroundMetricsSampler(blockingDelegate, 10L);
        sampler.setSamplingEnabled(true);
        assertTrue(entered.await(1, TimeUnit.SECONDS));

        // Dispose while blocked -> game thread returns immediately, state is DISPOSED
        sampler.dispose();
        assertTrue(sampler.isDisposed());
        assertEquals(BackgroundMetricsSampler.WorkerState.DISPOSED, sampler.getWorkerState());

        // Re-enable after dispose -> must be ignored
        sampler.setSamplingEnabled(true);
        assertEquals(BackgroundMetricsSampler.WorkerState.DISPOSED, sampler.getWorkerState());

        // Release blocked call -> worker exits, no second call occurs
        release.countDown();
        Thread.sleep(60L);
        assertEquals(1, callCount.get(), "No new worker or sample may run after disposal");
    }
}
