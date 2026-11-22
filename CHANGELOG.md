# Changelog

All notable changes to the **Gravity Shift** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

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
