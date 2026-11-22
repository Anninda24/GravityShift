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
    // A 16:9 or 5:3 ratio viewport in meters. Box2D operates best between 0.1m - 10m.
    public static final float WORLD_WIDTH = 20.0f;   // 20 meters wide
    public static final float WORLD_HEIGHT = 12.0f;  // 12 meters high

    // Physics Simulation Constants
    public static final float TIME_STEP = 1.0f / 60.0f; // Fixed 60Hz physics step
    public static final int VELOCITY_ITERATIONS = 8;
    public static final int POSITION_ITERATIONS = 3;
    public static final float MAX_FRAME_DELTA = 0.1f;   // Hard clamp: maximum 6 steps per rendered frame
    public static final float MAX_FRAME_TIME = MAX_FRAME_DELTA; // Backward compatibility alias
    public static final int MAX_PHYSICS_STEPS_PER_FRAME = 6;

    // Global Gravity Magnitude (m/s^2)
    public static final float GRAVITY_MAGNITUDE = 9.81f;

    // Physics Material Defaults
    public static final float DEFAULT_DENSITY = 1.0f;
    public static final float DEFAULT_FRICTION = 0.4f;
    public static final float DEFAULT_RESTITUTION = 0.1f;

    // Debugging Flags
    public static final boolean DEBUG_PHYSICS = true;
}
