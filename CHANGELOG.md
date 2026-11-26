# Changelog

All notable changes to the **Gravity Shift** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

---

## [0.3.20] - Phase 3F.7.3: Safe Snapshot Publication & Sampler Lifecycle - 2026-11-26

### Changed
- **Immutable `AtomicReference` Snapshot Publication (`BackgroundMetricsSampler.java`)**:
  - Replaced the seqlock mutable-snapshot publication with `ImmutableMetricsSnapshot` (four `final long` primitive fields) published via `AtomicReference<ImmutableMetricsSnapshot>`.
  - Background sampler constructs a new `ImmutableMetricsSnapshot` once per `500ms` sample and atomically sets `latestSnapshot`.
  - Game thread performs a single non-blocking `latestSnapshot.get()` and copies the four `final long` values into its reusable `out` snapshot with zero allocations.
- **Explicit Worker Lifecycle State Machine (`BackgroundMetricsSampler.java`)**:
  - Added `WorkerState` (`DISABLED_STOPPED`, `ENABLED_RUNNING`, `DISABLED_TERMINATING`, `DISPOSED`) and `samplingDesired` flag.
  - `samplerThread` is never cleared while the worker is alive; re-enabling while `DISABLED_TERMINATING` sets `samplingDesired = true` without spawning a second thread, and the exiting worker's `finally` block starts the replacement cleanly once terminated.

### Added
- **Deterministic Latch-Based Lifecycle & Snapshot Tests (`Phase3F7Test.java`)**:
  - Updated `test16`–`test18` for immutable `AtomicReference` publication and zero-allocation game-thread reads.
  - Added `test19_WorkerLifecycle_DisableAndReenableDuringBlockedCall_NeverOverlaps` and `test20_WorkerLifecycle_DisposalDuringBlockedCall_NeverSpawnsReplacement`.
  - Full core regression suite expanded to **158/158 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 24 Phase 3F.5, 21 Phase 3F.6, 20 Phase 3F.7).

---

## [0.3.19] - Phase 3F.7.2: Metrics Snapshot Concurrency Correction - 2026-11-26

### Fixed
- **Snapshot Data Race (`BackgroundMetricsSampler.java`)**:
  - Replaced the double-buffer reference swap with a single-writer, lock-free sequence lock (`volatile long sequence`, even = consistent, odd = write in progress).
  - Background writer delegates into `scratchSnapshot` outside the seqlock window, increments `sequence` to odd, copies all four primitive `long` fields into `publishedSnapshot`, and increments `sequence` to even.
  - Game thread performs bounded retries (`MAX_READ_RETRIES = 4`) reading `seqBefore` (even), copying all four fields to locals, and validating `seqBefore == seqAfter`; retains previous valid values on retry exhaustion without blocking or allocating.
  - Hardened error handling to catch `Exception` rather than `Throwable` (allowing fatal `OutOfMemoryError`/`ThreadDeath` to propagate), restored `Thread.currentThread().interrupt()` on `InterruptedException`, and added thread-identity guarding (`samplerThread == self`) to prevent duplicate workers on rapid toggle.

### Added
- **Concurrency Stress Tests (`Phase3F7Test.java`)**:
  - Added `test16_Seqlock_SustainedConcurrentWriterReader_NoMixedGenerations`, `test17_Seqlock_BoundedRetriesAndRetainsPreviousValidSnapshot`, and `test18_BackgroundSampler_RapidToggleNeverCreatesDuplicateWorkers`.
  - Full core regression suite expanded to **156/156 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 24 Phase 3F.5, 21 Phase 3F.6, 18 Phase 3F.7).

---

## [0.3.18] - Phase 3F.7.1: Off-Thread Android Metrics Sampling & Direction Encapsulation - 2026-11-26

### Changed
- **Off-Thread Android GC & Heap Sampling (`BackgroundMetricsSampler.java`, `AndroidRuntimeMetricsProvider.java`, `RuntimeMetricsProvider.java`, `DebugOverlay.java`)**:
  - Moved `Debug.getRuntimeStat("art.gc.gc-count")`, `Debug.getNativeHeapAllocatedSize()`, and `Runtime` sampling off the game/render thread onto a single controlled daemon thread (`"RuntimeMetricsSampler"`) at `0.5s` intervals.
  - Implemented lock-free double-buffered `RuntimeMetricsSnapshot` publication (`snapshotA` / `snapshotB` with `volatile latestSnapshot` reference swap), guaranteeing non-blocking, zero-allocation reads on the game thread.
  - Added `setSamplingEnabled(boolean)` and `dispose()` lifecycle hooks on `RuntimeMetricsProvider`, wired from `DebugOverlay` so disabling or disposing immediately stops the background sampler without leaking threads.
- **Direction Encapsulation (`Direction.java`, `Player.java`)**:
  - Changed `Direction.VALUES` to `private static final` and exposed `Direction.count()` and `Direction.fromIndex(int)` so external callers cannot mutate the cached enum array while preserving allocation-free iteration in `Player.dispose()`.

### Added
- **Phase 3F.7.1 Tests (`Phase3F7Test.java`)**:
  - Added `test13_BackgroundSampler_ExecutesOffGameThreadAndNonBlockingReads`, `test14_BackgroundSampler_DisableStopsSamplingAndDisposalReleases`, and `test15_BackgroundSampler_ProviderExceptionNeverCrashesGameplay`.
  - Full core regression suite expanded to **153/153 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 24 Phase 3F.5, 21 Phase 3F.6, 15 Phase 3F.7).

---

## [0.3.17] - Phase 3F.7: Allocation Discipline & Runtime Debug Overlay - 2026-11-26

### Changed
- **Hot-Path Allocation Audit & Cleanup (`Direction.java`, `Player.java`, `GravityController.java`)**:
  - Audited `render()`, `update()`, `PhysicsController.update()`, `onPrePhysicsStep()`, `onPostPhysicsStep()`, `Player` movement/impulses, `GravityController`, `EntityManager`, `GameContactListener`, and `WorldRenderer`.
  - Added cached `Direction.VALUES` to replace `Direction.values()` array copies in `Player.dispose()`.
  - Updated `GravityController` constructor to initialize gravity using preallocated `gravityVectorBuffer` rather than `calculateGravityVector(Direction.DOWN)`.

### Added
- **Platform-Neutral Runtime Metrics & Debug Overlay (`core`, `android`, `lwjgl3`)**:
  - Added `RuntimeMetricsProvider`, `RuntimeMetricsSnapshot`, `FallbackRuntimeMetricsProvider`, and `DebugOverlay` in `core/debug`.
  - Implemented `AndroidRuntimeMetricsProvider` (`android`) querying ART GC count (`Debug.getRuntimeStat("art.gc.gc-count")`), native heap (`Debug.getNativeHeapAllocatedSize()`), and Java heap (`Runtime`), returning `-1L` (`N/A`) when unsupported.
  - Implemented `DesktopRuntimeMetricsProvider` (`lwjgl3`) querying `GarbageCollectorMXBean` collection counts and Java heap (`Runtime`), reporting `N/A` for native heap where standard JVM does not expose Box2D C++ native heap.
  - Wired `DebugOverlay` into `PlayScreen` HUD rendering (`F3` desktop toggle + `setDebugOverlayEnabled(boolean)`), sampling at `0.5s` intervals into a single preallocated `StringBuilder` via pure integer math and drawing from cached `GlyphLayout` / `BitmapFontCache` with zero per-frame allocations.
- **Phase 3F.7 Verification Suite (`Phase3F7Test.java`)**:
  - Added 12 tests covering movement/jump/cap/platform regressions, `Direction.VALUES` & `Entity` vector reuse, JVM `ThreadMXBean` zero-byte steady-state allocation verification across 300 frames, overlay enable/disable, `N/A` fallback handling, heap used vs max formatting, low-frequency sampling, `CharSequence` buffer identity, and disposal.
  - Full core regression suite expanded to **150/150 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 24 Phase 3F.5, 21 Phase 3F.6, 12 Phase 3F.7).

---

## [0.3.16] - Phase 3F.6: World-Space Momentum Cap - 2026-11-26

### Added
- **World-Space 15 m/s Momentum Cap (`GameConfig.java`, `GravityController.java`, `Player.java`)**:
  - Defined `GameConfig.MAX_GRAVITY_SWITCH_SPEED = 15.0f` (`15.0 m/s`).
  - Implemented allocation-free `GravityController.clampBodyVelocityToMaxSpeed(Body, float)` using double-precision Euclidean magnitude computation ($v = \sqrt{v_x^2 + v_y^2}$). When $v > 15\text{ m/s}$, scales the vector cleanly ($\vec{v}_{\text{new}} = \vec{v}_{\text{old}} \frac{15}{v}$) while preserving exact travel direction, guarding against zero velocity, non-finite inputs, and 1-ULP upward float32 rounding.
  - Enforced strict box2d-safe transition ordering in `GravityController.setGravityDirection`: (1) validate unlocked world, (2–4) read dynamic body velocities, apply 15 m/s magnitude cap, preserve resulting velocities, (5) update world gravity via reusable `gravityVectorBuffer`, (6) wake affected dynamic bodies.
  - Enforced transition cap and unlocked-world guard in `Player.onGravityDirectionChanged` as well so no code path can bypass the 15 m/s limit.
- **Phase 3F.6 Regression & Cap Test Suite (`Phase3F6Test.java`)**:
  - Added 21 tests covering below-cap preservation, exact $15.0\text{ m/s}$ axis and diagonal boundaries, over-cap magnitude clamping and angle preservation, 4-way transitions (`DOWN -> LEFT -> UP -> RIGHT -> DOWN`), repeated transitions, natural post-transition gravity acceleration, impulse movement, jumping, coyote/buffer, physical departure, moving platforms, and Box2D world-lock safety.
  - Full core regression suite expanded to **138/138 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 24 Phase 3F.5, 21 Phase 3F.6).

---

## [0.3.15] - Phase 3F.5.2: Support Capacity Guarantee & Final Device Gate - 2026-11-26

### Changed
- **Support-Body Capacity Overflow Policy (`Player.java`)**:
  - Replaced silent ignore on capacity overflow (`MAX_SUPPORT_BODIES_PER_DIR = 8`) with a deterministic zero-allocation LRU eviction + untracked overflow-count fallback policy (`untrackedSupportContacts`).
  - When a 9th distinct supporting body contacts a directional sensor, the oldest tracked entry is evicted into `untrackedSupportContacts` while the 9th body is tracked at the top of the direction's stack (`getActiveSupportingBody()`).
  - If all 8 tracked bodies detach while an overflow-evicted contact remains active (`hasSupportOverflow`), `getActiveSupportingBody()` returns `null` (falling back safely to `0.0f` stationary support tangent rather than ever referencing a stale/detached body).

### Added
- **9th-Body Overflow Regression Tests (`Phase3F5Test.java`)**:
  - `test23_RealBox2D_NinthDistinctSupportingBody_TriggersOverflowAndSelectsNinthBody`: Verifies 9 simultaneous real Box2D wall contacts on `PLAYER_LEFT_SENSOR`, LRU eviction, active 9th-body velocity tracking, detachment reversion, and clean zeroing.
  - `test24_SupportOverflow_EvictedOldestRemainsLast_SafeNullFallbackZeroStale`: Verifies safe `null` fallback when only an evicted oldest body remains in contact.
  - Total test suite expanded to **117/117 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 24 Phase 3F.5).

### Verified
- **Physical-Device Verification (realme RMX5555, `bf53c7c1`)**:
  - Executed temporary on-device physics harness covering all 4 gravity directions (`DOWN`, `UP`, `LEFT`, `RIGHT`), grounded acceleration, stopping, direction reversal, jumping while moving, airborne steering, gravity switching while moving and while airborne, gravity-parallel velocity preservation, moving-platform riding without sliding, and airborne detachment without stale velocity (`25/25` checks passed, `0` failed, `0` exceptions). Removed temporary harness and verified clean production build.

---

## [0.3.14] - Phase 3F.5.1: Support-Body Tracking Audit - 2026-11-26

### Fixed
- **Stale Moving-Platform Reference (`Player.java`)**:
  - Replaced single-slot `Body[4]` tracking with a preallocated fixed-capacity per-direction stack (`MAX_SUPPORT_BODIES_PER_DIR = 8`) and per-body contact reference counting (`supportBodies`, `supportBodyCounts`, `supportBodyCount`).
  - When Platform A and Platform B both contact the same directional sensor and Platform B detaches while Platform A remains, Platform B is removed and active support reverts to Platform A immediately instead of retaining a stale reference to Platform B.
  - Multiple fixtures belonging to the same `Body` increment/decrement that body's reference count, preventing premature invalidation when crossing fixture seams.

### Added
- **Real Box2D Support-Body Lifecycle Tests (`Phase3F5Test.java`)**:
  - Added `test21_RealBox2D_TwoBodiesSameSensor_RevertsToRemainingBodyWithoutStaleVelocity` and `test22_RealBox2D_MultipleFixturesSameBody_NoPrematureInvalidation`.
  - Total test suite expanded to **115/115 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 22 Phase 3F.5).

---

## [0.3.13] - Phase 3F.5: Impulse-Based Movement - 2026-11-22

### Changed
- **Fixed-Step Impulse-Based Movement Model (`Player.java`, `GameConfig.java`)**:
  - Replaced legacy direct velocity overwrites in `Player.update()` with fixed-step impulse acceleration inside `onPrePhysicsStep(stepDelta)`.
  - Implemented acceleration clamping toward desired tangential velocity:
    - Maximum Ground Acceleration: $40.0\text{ m/s}^2$ ($\Delta v_{\max} \approx 0.667\text{ m/s}$ per step at $60\text{Hz}$).
    - Maximum Air Acceleration: $20.0\text{ m/s}^2$ ($\Delta v_{\max} \approx 0.333\text{ m/s}$ per step at $60\text{Hz}$).
  - Strictly preserved gravity-parallel velocity: movement impulses apply $J = (m \times \Delta v_{\text{clamped}}, 0)$ under vertical gravity (`DOWN`/`UP`) and $J = (0, m \times \Delta v_{\text{clamped}})$ under horizontal gravity (`LEFT`/`RIGHT`), ensuring zero cancellation or alteration of freefall, jump launch, or collision normal reactions.
- **Moving-Platform Support Velocity Tracking (`Player.java`, `GameContactListener.java`)**:
  - Updated `GameContactListener` to pass supporting body references during sensor contact begin and end events.
  - Added zero-allocation `Body[] supportingBodies` array tracking active support surfaces across all four cardinal directions.
  - Grounded movement calculates desired velocity relative to the supporting body's tangential velocity ($v_{\text{desired}} = v_{\text{support}} + \text{moveDirection} \times \text{MOVE\_SPEED}$), eliminating unintended relative sliding when standing still on moving platforms.
  - Airborne movement safely clears support references and operates relative to the stationary world coordinate frame, preventing stale platform velocity inheritance.
- **Screen-Relative 4-Way Controls (`PlayerInputController.java`)**:
  - Maintained screen-relative input mapping: `A`/`D` and Left/Right arrows control horizontal movement under vertical gravity (`DOWN`/`UP`), while `W`/`S` and Up/Down arrows control vertical movement along walls under horizontal gravity (`LEFT`/`RIGHT`).

### Added
- **Phase 3F.5 Test Suite (`Phase3F5Test.java`)**:
  - Added 20 focused tests covering grounded acceleration, airborne steering, 4-way gravity-parallel preservation, moving platform relative movement and detachment, velocity reversal, zero-input stability, and full Box2D lifecycle integration.
  - Regression suite expanded to **113/113 passing tests** (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4, 20 Phase 3F.5) with 100% pass rate.

---

## [0.3.12] - Phase 3F.4.3: Jump Buffer Physics-Step Ordering Audit - 2026-11-22

### Changed
- **Physics Lifecycle Hook Architecture (`Player.java`)**:
  - Relocated fixed physics state machine execution (`stepPhysics`) from `onPrePhysicsStep` to `onPostPhysicsStep`.
  - Reconstructed exact runtime event sequence:
    1. `PhysicsController.update()` runs accumulator loop.
    2. `EntityManager.onPreStep()` dispatches `onPrePhysicsStep` to entities (no-op for jump eligibility).
    3. `World.step()` advances Box2D simulation; narrowphase contacts fire `GameContactListener.beginContact`/`endContact` synchronously.
    4. Contact counters (`bottomContacts`, etc.) and multi-surface counts are updated inside the collision step.
    5. `EntityManager.onPostStep()` dispatches `onPostPhysicsStep` to entities.
    6. `Player.stepPhysics()` executes at the post-physics boundary: evaluates newly acquired ground contacts from `World.step()`, resets the jump latch if `isGrounded() && leftSupportSinceLaunch`, executes buffered jumps within the 6-step window, and decrements timers.
  - Resolved the step-6 landing contradiction: Box2D contacts detected during the 6th `World.step()` are immediately visible to `onPostPhysicsStep` before the 6th buffer decrement completes, enabling deterministic buffered jump launch on step 6.

### Added
- **Real Box2D Physics Lifecycle Regression Suite (`Phase3F4Test.java`)**:
  - `test38_RealPhysicsLifecycle_LandingOnSixthStepExecutesBufferedJump`: Integrates real `PhysicsController`, `EntityManager`, and Box2D `World.step()`. Verifies that a player descending in air lands during the 6th `World.step()`, contact fires inside the solver, and `onPostPhysicsStep` successfully executes the buffered jump.
  - `test39_RealPhysicsLifecycle_LandingOnSeventhStepRejectsExpiredBuffer`: Verifies that descending for 6 full airborne steps expires the buffer to $0.0\text{f}$; landing on the 7th step cleanly rejects the expired buffer.
  - `test40_RealPhysicsLifecycle_PrePostStepSeparationIntegrity`: Verifies that `onPrePhysicsStep` alone does not consume buffers without contact, preserving clear separation of concerns.
  - Total test suite now stands at 93/93 passing tests (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 40 Phase 3F.4).

---

## [0.3.11] - Phase 3F.4.2: Exact Jump Timer Boundary Verification - 2026-11-22

### Changed
- **Timer Execution Ordering & Discrete Semantics (`Player.java`)**:
  - Reordered `stepPhysics(float stepDelta)` execution stages: buffered jump execution is evaluated **before** buffer timer decrement. This ensures landings occurring on the final eligible step of the 100ms window (the 6th physics step at $t = 100\text{ms}$) execute rather than being prematurely discarded by decrementing first.
  - Implemented `TIMER_EPSILON = 1e-4f` ($0.1\text{ms}$) snap threshold for both `coyoteTimer` and `jumpBufferTimer`. Snaps residual IEEE 754 single-precision float residue ($3.725 \times 10^{-9}\text{s}$) to clean $0.0\text{f}$ after exactly 6 complete timer-decrement steps ($6 \times 1/60\text{s} = 0.100\text{s}$).
  - Documented exact discrete six-step window boundaries:
    - **Coyote Time**: Jumps at the last eligible step boundary (5 steps elapsed = $83.33\text{ms} < 100\text{ms}$) succeed; jumps after 6 complete timer-decrement steps ($100.0\text{ms}$) fail immediately.
    - **Jump Buffering**: Landing within the 6-step window (steps 1..6) executes the buffered jump; landing on step 7 (after 6 complete timer-decrement steps) fails to execute.

### Added
- **Automated Timer Boundary Regression Tests (`Phase3F4Test.java`)**:
  - Added 9 targeted timer boundary tests (`test29` through `test37`):
    - `test29_CoyoteTime_SucceedsAtLastEligibleStepBoundary`: Jump request at 5th step boundary ($83.33\text{ms}$) succeeds.
    - `test30_CoyoteTime_FailsImmediatelyAfterSixCompleteTimerDecrementSteps`: Jump request after 6 complete steps ($100\text{ms}$) fails.
    - `test31_CoyoteTime_TimerCannotBecomeNegative`: Coyote timer never drops below $0.0\text{f}$ over 120 steps.
    - `test32_CoyoteTime_RenderFrameDeltaPatternsDoNotChangeBoundary`: $120\text{Hz}$, $30\text{Hz}$, and jitter deltas do not alter fixed 6-step boundary.
    - `test33_JumpBuffer_ExecutesWhenLandingOccursWithinSixStepWindow`: Landing on the 6th step ($100\text{ms}$) executes buffered jump.
    - `test34_JumpBuffer_ExpiresAfterSixCompleteTimerDecrementSteps`: Buffer expiring after 6 steps cannot execute on step 7 landing.
    - `test35_JumpBuffer_ConsumedExactlyOnce`: Buffer clears upon execution and never re-triggers.
    - `test36_JumpBuffer_IllegitimateCornerTransitionCannotTriggerBufferedJump`: Corner gravity transition without detachment cannot fire buffer.
    - `test37_ExactTimerOrdering_BufferedJumpEvaluatedBeforeTimerDecrement`: Validates pre-decrement evaluation order in `stepPhysics`.
  - Full suite expanded to 90 passing tests (32 Phase 3F.1, 9 Phase 3F.2, 12 Phase 3F.3, 37 Phase 3F.4), 100% success rate.

---

## [0.3.10] - Phase 3F.4.1: Jump State Machine Invariant Correction - 2026-11-22

### Changed
- **Physical Departure Tracking Invariant (`Player.java`)**:
  - Replaced single-direction support departure detection with holistic multi-surface contact tracking via `getAllContactCount() == 0`.
  - In `executeJump()`, initialized `leftSupportSinceLaunch = (getAllContactCount() == 0)`. A coyote jump launched while touching an adjacent surface (such as a wall in a corner) correctly starts with `leftSupportSinceLaunch = false`.
  - In `onSensorContactEnd(FixtureType)`, `leftSupportSinceLaunch` transitions to `true` strictly when `getAllContactCount() == 0`. Losing one contact while an orthogonal or opposing surface contact persists (e.g. leaving floor while touching a wall) no longer falsely signals departure.
  - In `stepPhysics(float stepDelta)`, continuously evaluates `if (getAllContactCount() == 0) leftSupportSinceLaunch = true;` to guarantee state-machine convergence.
- **Fixed-Step Sole Ownership of Latch Reset (`Player.java`)**:
  - Removed all `jumpConsumed = false` assignments from Box2D collision callbacks (`onSensorContactBegin`).
  - Latch reset is exclusively evaluated and executed during `stepPhysics(float stepDelta)` at the discrete 60Hz physics-step boundary when `isGrounded() && leftSupportSinceLaunch`.
  - Narrowphase contact callbacks strictly manage sensor counters and coyote timers, ensuring state transitions remain deterministic and decoupled from physics solver iteration order.
- **Anti-Corner-Exploit Guarantee**:
  - Completely eliminated the corner gravity-switch exploit: jumping while touching both floor and wall, losing floor contact, and switching gravity toward the wall cannot reset the jump latch without first detaching into free air (`getAllContactCount() == 0`).
  - Buffered jumps executing in multi-surface or corner configurations strictly respect physical departure invariants.

### Added
- **Automated Invariant Regression Tests (`Phase3F4Test.java`)**:
  - Added 10 targeted invariant regression tests (`test19` through `test28`):
    - `test19_TwoSimultaneousContacts_EndingOneDoesNotSetLeftSupport`: Losing 1 of 2 contacts does not trigger departure.
    - `test20_EndingFinalActiveContactAfterLaunchSetsLeftSupport`: Complete loss of all contacts triggers departure.
    - `test21_JumpingFromFloorWhileTouchingWallDoesNotCountAsCompleteDeparture`: Launch in corner initializes `leftSupportSinceLaunch = false`.
    - `test22_SwitchingGravityTowardWallWithoutCompleteDepartureDoesNotResetLatch`: Gravity switch in corner preserves consumed latch.
    - `test23_CoyoteJumpWhileTouchingAnotherSurfaceInitializesDepartureToFalse`: Coyote jump touching adjacent surface correctly tracks departure.
    - `test24_ContactCallbackCannotResetJumpConsumed`: Contact begin callback cannot prematurely reset jump latch.
    - `test25_LegitimateLandingAfterCompleteDepartureResetsLatchAtPhysicsStepBoundary`: Clean departure followed by landing resets latch at physics step boundary.
    - `test26_BufferedJumpExecutesAfterLegitimateLanding`: Pre-landing jump buffer cleanly triggers jump on valid touchdown.
    - `test27_BufferedJumpCannotExploitIllegitimateCornerTransition`: Pre-landing buffer cannot fire extra jump across corner gravity switch without detachment.
    - `test28_ExistingFourDirectionGravityAndJumpRegressionsRemainIntact`: Validates DOWN, UP, LEFT, RIGHT gravity jump directions and velocities remain identical ($6.5\text{ m/s}$).
  - Full suite expanded to 81 tests (32 in `Phase3F1Test`, 9 in `Phase3F2Test`, 12 in `Phase3F3Test`, 28 in `Phase3F4Test`), all passing with 100% success rate.

---

## [0.3.9] - Phase 3F.4: Coyote Time & Jump Buffering - 2026-11-22

### Added
- **100ms Fixed-Timestep Coyote Time (`Player.java`, `GameConfig.java`)**:
  - Implemented 100ms coyote time window (`COYOTE_DURATION = 0.100f`) permitting jump execution after walking or falling off a ledge.
  - Advancing strictly using discrete physics timestep slices (`GameConfig.TIME_STEP = 1/60s`) rather than variable render frame deltas, eliminating timer drift across 60Hz, 90Hz, and 120Hz displays.
  - Coyote eligibility opens when contact count reaches zero without an active jump launch; immediately closes upon jump execution.
- **100ms Jump Input Buffering (`Player.java`, `GameConfig.java`)**:
  - Implemented 100ms jump request buffer (`JUMP_BUFFER_DURATION = 0.100f`) retaining jump inputs pressed up to 100ms prior to landing.
  - Buffered jump executes deterministically on the next valid physics frame upon landing when support and anti-exploit latch criteria are satisfied.
  - Buffer expires cleanly if the player remains airborne for more than 100ms without reaching support.
- **Anti-Exploit Jump Latch & Departure Tracking (`Player.java`, `EntityManager.java`)**:
  - Implemented state-machine tracking `leftSupportSinceLaunch`: transitions to `false` on grounded jump launch, transitions to `true` only when support count reaches zero after departure.
  - Jump latch (`jumpConsumed`) resets **only** on a grounded frame where `leftSupportSinceLaunch == true`.
  - Corner contacts and gravity switches are prohibited from manufacturing extra jumps or resetting the consumed latch without genuine physical support departure.
- **Physics Step Entity Synchronization (`Entity.java`, `EntityManager.java`)**:
  - Added `onPrePhysicsStep(float stepDelta)` and `onPostPhysicsStep(float stepDelta)` lifecycle hooks to `Entity.java`.
  - Dispatched from `EntityManager` during discrete physics steps (`PhysicsStepListener`), guaranteeing zero timer drift when render accumulator executes 0, 1, or multiple physics steps per frame.
  - Integrated pending entity flushing in `onPreStep()` to ensure zero-lag registration for physics simulation.
- **Automated Verification Suite (`Phase3F4Test.java`)**:
  - Implemented 18 comprehensive unit and integration tests covering grounded jumps, immediate departure jumps, coyote window success/expiry, buffered jump execution/expiration, latch anti-exploit, airborne jump rejection, gravity switching interactions, render-frame delta pattern invariance, accumulator step variations, four-way gravity compatibility, jump velocity directions, and lifecycle disposal.
  - All 71 project tests passing (32 in `Phase3F1Test`, 9 in `Phase3F2Test`, 12 in `Phase3F3Test`, 18 in `Phase3F4Test`).

---

## [0.3.8] - Phase 3F.3: Chamfered Player Shape & Friction Tuning - 2026-11-22

### Added
- **8-Vertex Chamfered Player Polygon (`Player.java`, `BodyFactory.java`)**:
  - Replaced rectangular collision fixture with an 8-vertex chamfered polygon fixture ($0.8\text{m} \times 1.4\text{m}$, corner chamfer offset $0.1\text{m}$).
  - Configured 8 counter-clockwise vertices within Box2D's 8-vertex limit (`b2_maxPolygonVertices = 8`):
    $(-0.3, -0.7)$, $(0.3, -0.7)$, $(0.4, -0.6)$, $(0.4, 0.6)$, $(0.3, 0.7)$, $(-0.3, 0.7)$, $(-0.4, 0.6)$, $(-0.4, -0.6)$.
  - Preserved strict convexity and counter-clockwise winding order for robust narrowphase GJK/EPA solver stability.
  - Eliminated tile seam catching/snagging across tiled platforms and corner transitions while preserving exact $0.8\text{m}$ width and $1.4\text{m}$ height bounds.
- **Support Sensor Alignment & Invariants**:
  - Preserved all 4 directional support sensors (`BOTTOM`, `TOP`, `LEFT`, `RIGHT`) with flat face spans ($0.6\text{m}$ horizontal, $1.2\text{m}$ vertical) matching the un-beveled edges.
  - Inset sensors prevent corner cross-talk and false grounding during edge collisions.
  - Preserved player mass density ($1.2\text{ kg/m}^2$) and `fixedRotation = true`.
  - Preserved settled player center-of-mass height ($y_{\text{center}} \approx 2.015\text{m}$ atop platform surface $y = 1.3\text{m}$ under DOWN gravity).
- **Physical Friction Calibration**:
  - Reduced player fixture friction from $0.4\text{f}$ to $0.2\text{f}$ (`Player.FRICTION = 0.2f`).
  - Tuned combined Box2D contact friction ($\sqrt{0.2 \times 0.4} \approx 0.283\text{f}$) to prevent wall sticking or drag during vertical descents/wall slides while maintaining crisp, controllable ground traction.
- **Automated Verification Suite (`Phase3F3Test.java`)**:
  - Added 12 dedicated automated tests covering 8-vertex polygon geometry, chamfer radius, bounding dimensions, convexity/CCW winding, fixed rotation, density 1.2, friction 0.2, 4 directional sensors, settled coordinates ($y \approx 2.015\text{m}$), wall contact support, and lifecycle invariants.
  - Confirmed 100% green pass rate across all 53 project tests (32 in `Phase3F1Test`, 9 in `Phase3F2Test`, 12 in `Phase3F3Test`).

---

## [0.3.7] - Phase 3F.2: Viewport Overhaul - 2026-11-22

### Added
- **Dual-Viewport Architecture (`BaseScreen.java`, `PlayScreen.java`)**:
  - Replaced legacy `FitViewport` with LibGDX `ExtendViewport` for world rendering with minimum 20x12 meter dimensions.
  - Introduced `ScreenViewport` for UI and future touch controls, mapping 1:1 with screen pixels independent of meters.
  - Implemented decoupled camera hierarchy: `worldCamera` (meters) and `hudCamera` (pixels).
  - Defined `MIN_WORLD_WIDTH = 20.0f` and `MIN_WORLD_HEIGHT = 12.0f` in `GameConfig.java`.
- **Chamber Centering & Symmetric Padding**:
  - Implemented explicit camera centering on the 20x12 chamber center `(10.0m, 6.0m)` during resize events (`viewport.update(width, height, false)`).
  - Guarantees the entire minimum chamber remains fully visible without stretching, cropping, or distortion across wide (16:9, 18:9, 20:9, 21:9) and tall (4:3, 1:1, 9:16) aspect ratios.
  - Extended viewport area displays dark background void rather than scaling world geometry.
- **HUD Stage & Clean Render Sequence (`PlayScreen.java`)**:
  - Added `hudStage = new Stage(hudViewport)` for screen-space UI elements with zero per-frame heap allocations.
  - Established rendering order: Clear -> Apply world viewport -> Render world & entities with interpolation -> Render Box2D debug lines -> Apply HUD viewport -> Render HUD stage.
  - Added lifecycle disposal for `hudStage`.
- **Automated Verification Suite (`Phase3F2Test.java`)**:
  - Added 9 comprehensive automated tests verifying minimum 20x12 dimensions, wide/tall aspect ratios, ultrawide layouts (20:9, 21:9), resize behavior, camera centering, world coordinate consistency, and HUD coordinate independence.
  - Verified that all 32 Phase 3F.1 regression tests remain 100% green (total 41 passing tests).

---

## [0.3.6] - Phase 3F.1: Timestep Clamping & Render Interpolation - 2026-11-22

### Added
- **Fixed Timestep Clamping (`GameConfig.java`, `PhysicsController.java`)**:
  - Defined `MAX_FRAME_DELTA = 0.1f` (100 ms) and `MAX_PHYSICS_STEPS_PER_FRAME = 6`.
  - Added robust guards for negative, NaN, and infinite frame deltas in `PhysicsController.update(float)`.
  - Implemented spiral-of-death protection by capping catch-up iterations to at most 6 steps per rendered frame.
  - Retained fractional accumulator remainder modulo `TIME_STEP` for render interpolation.
  - Exposed normalized read-only interpolation factor `getInterpolationAlpha()` strictly within `[0.0, 1.0]` and `getAccumulatorRemainder()`.
- **Decoupled Physics Step Listener (`PhysicsStepListener.java`)**:
  - Introduced `PhysicsStepListener` interface with `onPreStep()` and `onPostStep()` hooks.
  - Implemented listener in `EntityManager` to capture transforms across all active entities around each discrete step.
- **Zero-Allocation Entity Transform Snapshots (`Entity.java`)**:
  - Added pre-step and post-step transform storage (`previousPosition`, `currentPosition`, `previousAngle`, `currentAngle`).
  - Implemented allocation-free linear position interpolation: $P = P_{\text{prev}} + \alpha(P_{\text{curr}} - P_{\text{prev}})$.
  - Implemented shortest-arc angular interpolation across $[-\pi, \pi]$ boundary:
    $\Delta\theta = \theta_{\text{curr}} - \theta_{\text{prev}} - 2\pi \cdot \operatorname{round}((\theta_{\text{curr}} - \theta_{\text{prev}}) / 2\pi)$.
  - Implemented `syncTransform()` / `resetInterpolation()` for teleportation without rendering streaks.
- **Interpolated Rendering (`WorldRenderer.java`, `PlayScreen.java`)**:
  - Decoupled visual entity rendering from Box2D body state, consuming `getInterpolatedX(alpha)` and `getInterpolatedY(alpha)`.
  - Passed `physicsController.getInterpolationAlpha()` to `WorldRenderer.render(...)`.
- **Granular 32-Test Verification Suite (`Phase3F1Test.java`)**:
  - Expanded test suite from monolithic check into 32 distinct, individually named automated JUnit 5 tests.
  - 12 Phase 3F.1 dedicated unit tests.
  - 4 Four-way cardinal direction tests (`DOWN`, `UP`, `LEFT`, `RIGHT`).
  - 10 Integrated scenario tests (`T1`–`T10`).
  - 6 Sub-phase regressions (Phases 3C–3E.4).
- **Core Design Rules Specification (`DESIGN_RULES.md`)**:
  - Locked core decisions on mobile-first targets, screen-relative input mapping, momentum conservation with 15 m/s cap, coyote time / buffer rules, chamfered player geometry, and merged level tiles.

### Changed
- **Disposal Lifecycle Guards**:
  - Hardened `PhysicsController` against native double-free crashes (`disposed` flag, safe idempotent disposal, `isDisposed()` getter, and `IllegalStateException` on update after disposal).
  - Added `Gdx.app != null` check to `Player.logUnderflow` for safe headless test execution.

---

## [0.3.5] - Phase 3E.5: Four-Way Integration & Anti-Exploit Hardening - 2026-11-22

### Added
- Integrated cardinal gravity transitions across DOWN, UP, LEFT, and RIGHT.
- Strict jump anti-exploit enforcement: switching gravity across existing contacts does not refresh consumed jump latch.
- Support direction and world gravity synchronization via single orchestrator in `PlayScreen.setGravityDirection(Direction)`.

---

## [0.3.0] - Phase 3A–3D: Player Physics & Gravity Controller - 2026-11-20

### Added
- Dynamic player body with directional support sensors (BOTTOM, TOP, LEFT, RIGHT).
- `GameContactListener` with directional fixture filtering and underflow protection.
- `GravityController` single source of truth for Box2D world gravity vectors.
- Tangential gravity-relative movement and direction-independent jump impulse.
