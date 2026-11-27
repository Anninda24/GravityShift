package com.AnnindaRajDas.gravityshift.physics;

/**
 * Strongly-typed enumeration identifying the collision role and surface type
 * of Box2D fixtures in the physics simulation.
 */
public enum FixtureType {
    /** Walkable platforms and horizontal chamber floors */
    GROUND,

    /** Vertical perimeter boundaries and puzzle walls */
    WALL,

    /** Upper perimeter chamber ceilings and horizontal overhangs */
    CEILING,

    /** Main solid collision torso of the player character */
    PLAYER_BODY,

    /** Trigger sensor attached to the player bottom for downward support detection */
    PLAYER_BOTTOM_SENSOR,

    /** Trigger sensor attached to the player top for upward support detection */
    PLAYER_TOP_SENSOR,

    /** Trigger sensor attached to the player left side for leftward support detection */
    PLAYER_LEFT_SENSOR,

    /** Trigger sensor attached to the player right side for rightward support detection */
    PLAYER_RIGHT_SENSOR,

    /** Movable gravity-responsive puzzle cube */
    GRAVITY_CUBE,

    /** Sensor fixture for a puzzle pressure plate */
    PRESSURE_PLATE_SENSOR,

    /** Logic-driven sliding door */
    SLIDING_DOOR,

    /** Lethal laser hazard sensor */
    HAZARD_LASER;

    /**
     * Checks if this fixture type represents one of the player's directional support sensors.
     *
     * @return true if directional player sensor
     */
    public boolean isPlayerSensor() {
        return this == PLAYER_BOTTOM_SENSOR
                || this == PLAYER_TOP_SENSOR
                || this == PLAYER_LEFT_SENSOR
                || this == PLAYER_RIGHT_SENSOR;
    }

    /**
     * Checks if this fixture type represents a solid physical surface that can support entities.
     *
     * @return true if ground, wall, or ceiling surface
     */
    public boolean isSupportingSurface() {
        return this == GROUND
                || this == WALL
                || this == CEILING;
    }

    /**
     * Evaluates compatibility between this player sensor and a contacted physical surface.
     * Enforces strict directional orientation:
     * - PLAYER_BOTTOM_SENSOR accepts GROUND
     * - PLAYER_TOP_SENSOR accepts CEILING
     * - PLAYER_LEFT_SENSOR accepts WALL
     * - PLAYER_RIGHT_SENSOR accepts WALL
     *
     * @param surface the contacted surface fixture type
     * @return true if the surface is physically compatible with this sensor's orientation
     */
    public boolean isCompatibleWith(FixtureType surface) {
        if (surface == null) {
            return false;
        }
        return switch (this) {
            case PLAYER_BOTTOM_SENSOR -> surface == GROUND;
            case PLAYER_TOP_SENSOR -> surface == CEILING;
            case PLAYER_LEFT_SENSOR -> surface == WALL;
            case PLAYER_RIGHT_SENSOR -> surface == WALL;
            default -> false;
        };
    }
}
