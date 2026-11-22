# Changelog

All notable changes to the **Gravity Shift** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

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
