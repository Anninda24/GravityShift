# Gravity Shift — Core Design Rules & Invariants

This document codifies the locked design rules, physics parameters, input conventions, and technical invariants for **Gravity Shift**. Any modification to these rules must be deliberate, documented, and reflected across regression suites.

---

## 1. Target Platforms & Priority

- **Primary Target**: Android (Mobile-first vertical slice, touch controls, 60–120 Hz displays).
- **Secondary / Dev Target**: Desktop (LWJGL3) for rapid compilation, headless replay verification, and development tools.
- **Device Gate**: No phase is considered complete until it runs smoothly and has been verified on a physical Android device.

---

## 2. Input Mapping & Control Conventions

### Movement (Screen-Relative)
The player input is a 2D vector in screen space, projected onto the active surface tangent:
- **Vertical Gravity (`DOWN`, `UP`)**: Left/Right (`A`/`D`, Left/Right Arrow, or virtual horizontal stick) moves the player horizontally.
- **Horizontal Gravity (`LEFT`, `RIGHT`)**: Up/Down (`W`/`S`, Up/Down Arrow, or virtual vertical stick) moves the player up/down along the wall.
- **Policy**: The player never has to mentally rotate controls. The physical direction on screen matches finger/key movement.

### Gravity Input
- **Desktop**: Keys `1`–`4` (cardinal assignment: `1=DOWN`, `2=LEFT`, `3=UP`, `4=RIGHT`) or `Q`/`E` (counter-clockwise / clockwise rotation).
- **Touch (Mobile)**: Swipe in the desired gravity direction on the right half of the screen, or tap dedicated on-screen cardinal gravity buttons (fallback).
- **Optional Stretch**: Device tilt with hysteresis (triggers at $>35^\circ$, resets past $<20^\circ$).

---

## 3. Physics & Momentum Rules

### Momentum Conservation on Gravity Shift
- When gravity direction switches, the player's world-space linear velocity is **preserved**.
- The newly applied gravity vector accelerates the player naturally along the new gravitational axis.
- **Velocity Cap**: Total linear speed is strictly clamped at $15\text{ m/s}$ to prevent runaway slams and tunneling.

### Clamped-Impulse Movement Model
- Direct velocity overwrites on the dynamic body are **prohibited**.
- Movement applies a computed impulse toward the desired tangential velocity per physics step:
  - Max Ground Acceleration: $\sim 40\text{ m/s}^2$
  - Max Air Acceleration: $\sim 20\text{ m/s}^2$
- The gravity-parallel velocity component is **never** touched by movement logic.
- When standing on moving bodies (crates/platforms), movement operates relative to the supporting body's velocity.

### Player Body Geometry
- **Dimensions**: $0.8\text{m} \times 1.4\text{m}$ chamfered polygon (corner chamfer radius $\approx 0.1\text{m}$) to eliminate Box2D seam-snagging on tile seams.
- **Rotation**: `fixedRotation = true`.
- **Material**: Density $1.2\text{ kg/m}^2$, calibrated friction on torso and sensors to prevent wall clinging or snagging.

---

## 4. Jump Rule & Latch Invariant (State-Clean Lifecycle)

Replaces legacy contact-counter latching with a state-machine that includes coyote time and input buffering:

1. **Eligibility**: Jump is permitted if:
   - Player is grounded (`support sensor count > 0` along current `supportDirection`), **OR**
   - Player is within the **Coyote Window** ($\le 100\text{ ms}$ since leaving supporting surface).
2. **Jump Buffering**: Jump inputs pressed up to $100\text{ ms}$ prior to landing are buffered and execute on the first grounded frame.
3. **Launch State Transition**:
   - On jump launch: `jumpConsumed = true`, `leftSupportSinceLaunch = false`.
   - `leftSupportSinceLaunch` transitions to `true` when supporting contact count reaches $0$ after launch.
4. **Latch Reset Rule**:
   - The jump latch resets (`jumpConsumed = false`) **only** on a grounded frame where `leftSupportSinceLaunch == true`.
   - **Gravity shifts never reset the latch by themselves.**
   - Landing on a new surface resets the latch, including corner contacts, provided the player genuinely left support since the last jump.

---

## 5. Timestep & Rendering Architecture

- **Physics Timestep**: Fixed $60\text{Hz}$ ($\Delta t = \frac{1}{60}\text{s} \approx 0.01667\text{s}$).
- **Delta Clamp**: Accumulator delta time is clamped to $0.1\text{s}$ (max 6 physics steps per rendered frame) to prevent spiral-of-death breakdowns during frame spikes.
- **Render Interpolation**: Physics state uses accumulator alpha $\alpha = \frac{\text{accumulator}}{\text{TIME\_STEP}}$ to interpolate render transforms between previous and current physics states for smooth display on $90\text{Hz}$ and $120\text{Hz}$ screens.
- **Viewport System**:
  - **Game World**: `ExtendViewport` with a minimum $20\text{m} \times 12\text{m}$ chamber world area. Chambers are always fully visible; excess screen displays aesthetic background framing.
  - **HUD & Touch Controls**: Rendered via a separate `ScreenViewport` stage to ensure UI and virtual controls remain crisp and ergonomically positioned across any mobile aspect ratio (including 20:9 and 21:9 phones).

---

## 6. Level Format, Seam Merging & State Lifecycle

- **Level Format**: Tiled `.tmx` maps loaded via LibGDX `TmxMapLoader`.
  - Tile layer defines static solid chamber boundaries.
  - Object layer defines spawn locations, exit portal, cubes, pressure plates, doors, and hazard volumes.
- **Seam Merging**: Solid geometry loader merges adjacent collinear tiles into unified rectangles or continuous `ChainShape` contours prior to fixture generation, eliminating Box2D internal edge collisions.
- **Level State Machine**:
  $$\text{LOADING} \longrightarrow \text{PLAYING} \longrightarrow \text{DEAD} \longrightarrow \text{RESPAWNING} \longrightarrow \text{COMPLETE}$$
- **Instant Restart**: Dedicated restart button (`R` key or on-screen icon) immediately respawns the player and resets chamber state without reloading asset files. No state-undo.

---

## 7. Zero-GC Allocation Discipline

- **Game Loop Allocations Prohibited**: Zero `new` instantiations in `render()`, `update()`, `step()`, or contact callback paths.
- **Memory Pooling**: Vector math uses reusable object pools, pre-allocated math buffers, or LibGDX pooled instances.
- **Diagnostics**: A debug overlay tracking active heap usage, frame time, and garbage collection frequency must remain active during development.

---

## 8. Feature Scope & Explicit Exclusions

- **MVP Scope**: Exactly 8 polished, tested puzzle chambers with progressive difficulty.
- **Confirmed Core Mechanics**: Four-way gravity, player movement/jumping, weighted dynamic cubes, pressure plates, sliding doors, and one hazard type (laser barrier or crusher).
- **Explicitly Excluded / Cut Features**:
  - Gravity Gun (permanently cut).
  - Electrified contact strips (deferred).
  - Smooth multi-screen camera framing (chambers are single-screen 20x12m).
  - Complex multi-level undos.
- **Stretch Goals (Phase 9 only if time permits)**: Tilt gravity input, particle emitters, haptic vibration, ambient soundtrack, extra chambers (up to 15).
