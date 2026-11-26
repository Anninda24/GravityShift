package com.AnnindaRajDas.gravityshift.config;

/**
 * Global game constants and configuration parameters.
 * Encapsulates world dimensions, physics simulation parameters, and defaults.
 */
public final class GameConfig {

    // Prevent instantiation
    private GameConfig() {}

    // Title
    public static final String TITLE = "Gravity Shift";

    // Desktop Window Dimensions (Landscape 5:3 ratio matching world dimensions)
    public static final int DESKTOP_WIDTH = 960;
    public static final int DESKTOP_HEIGHT = 576;

    // Virtual World Dimensions (in Meters for Box2D compatibility)
    // Minimum 20x12 meter chamber area. ExtendViewport expands beyond this on wider/taller screens.
    public static final float WORLD_WIDTH = 20.0f;       // 20 meters wide
    public static final float WORLD_HEIGHT = 12.0f;      // 12 meters high
    public static final float MIN_WORLD_WIDTH = 20.0f;   // Minimum world width in meters
    public static final float MIN_WORLD_HEIGHT = 12.0f;  // Minimum world height in meters

    // Physics Simulation Constants
    public static final float TIME_STEP = 1.0f / 60.0f; // Fixed 60Hz physics step
    public static final int VELOCITY_ITERATIONS = 8;
    public static final int POSITION_ITERATIONS = 3;
    public static final float MAX_FRAME_DELTA = 0.1f;   // Hard clamp: maximum 6 steps per rendered frame
    public static final float MAX_FRAME_TIME = MAX_FRAME_DELTA; // Backward compatibility alias
    public static final int MAX_PHYSICS_STEPS_PER_FRAME = 6;

    // Global Gravity & Momentum Constants
    public static final float GRAVITY_MAGNITUDE = 9.81f;
    public static final float MAX_GRAVITY_SWITCH_SPEED = 15.0f; // 15 m/s cap on gravity transition

    // Physics Material Defaults
    public static final float DEFAULT_DENSITY = 1.0f;
    public static final float DEFAULT_FRICTION = 0.4f;
    public static final float DEFAULT_RESTITUTION = 0.1f;

    // Movement & Acceleration Parameters (Impulse-based movement)
    public static final float MAX_GROUND_ACCEL = 40.0f;        // 40 m/s^2 max ground acceleration
    public static final float MAX_AIR_ACCEL = 20.0f;           // 20 m/s^2 max air acceleration

    // Player Jump Timing Constants (Fixed Timestep Physics Duration)
    public static final float COYOTE_DURATION = 0.100f;       // 100ms coyote time window
    public static final float JUMP_BUFFER_DURATION = 0.100f;   // 100ms jump input buffer window

    // Debugging Flags
    public static final boolean DEBUG_PHYSICS = true;
}
