package com.AnnindaRajDas.gravityshift.physics;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;

/**
 * Manages the global Box2D world gravity vector across four cardinal directions.
 *
 * Demonstrates:
 * - Single Responsibility Principle (SRP): Encapsulates all world gravity state,
 *   vector calculations, and body awakening behavior.
 * - Encapsulation: Protects internal gravity state and exposes defensive copies of mutable vectors.
 * - Single Source of Truth: Exclusively synchronizes Box2D world gravity with the active Direction.
 */
public class GravityController {

    private final World world;
    private final float magnitude;
    private Direction currentDirection;

    // Reusable cache to retrieve world bodies without per-shift heap allocations
    private final Array<Body> bodiesCache = new Array<>();

    /**
     * Constructs a GravityController using the default global gravity magnitude.
     * Initializes world gravity to Direction.DOWN.
     *
     * @param world the Box2D physics world
     */
    public GravityController(World world) {
        this(world, GameConfig.GRAVITY_MAGNITUDE);
    }

    /**
     * Constructs a GravityController with a custom positive gravity magnitude.
     * Initializes world gravity to Direction.DOWN.
     *
     * @param world     the Box2D physics world
     * @param magnitude positive acceleration magnitude in m/s^2
     */
    public GravityController(World world, float magnitude) {
        if (world == null) {
            throw new IllegalArgumentException("World cannot be null for GravityController.");
        }
        if (magnitude <= 0.0f || !Float.isFinite(magnitude)) {
            throw new IllegalArgumentException("Gravity magnitude must be positive and finite. Given: " + magnitude);
        }
        this.world = world;
        this.magnitude = magnitude;
        this.currentDirection = Direction.DOWN;

        // Synchronize initial Box2D world gravity to DOWN
        this.world.setGravity(calculateGravityVector(Direction.DOWN));
    }

    /**
     * Shifts global world gravity to the specified cardinal direction.
     * Wakes all sleeping dynamic bodies while preserving their existing velocities.
     * Rejects changes while the simulation world is locked.
     *
     * @param direction target gravity direction (DOWN, UP, LEFT, RIGHT)
     * @throws IllegalArgumentException if direction is null
     * @throws IllegalStateException    if the Box2D world is locked
     */
    public void setGravityDirection(Direction direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Gravity direction cannot be null.");
        }

        // Idempotent: Avoid redundant world updates or awakening if direction is unchanged
        if (this.currentDirection == direction) {
            return;
        }

        if (world.isLocked()) {
            throw new IllegalStateException("Cannot change gravity while the physics world is locked.");
        }

        // Calculate and apply new Box2D world gravity vector
        Vector2 newGravityVector = calculateGravityVector(direction);
        world.setGravity(newGravityVector);
        this.currentDirection = direction;

        // Wake all dynamic bodies so they immediately respond to the new gravity field
        wakeDynamicBodies();
    }

    /**
     * Wakes all dynamic bodies in the world while leaving static and kinematic bodies untouched.
     * Existing linear and angular velocities are preserved.
     */
    private void wakeDynamicBodies() {
        bodiesCache.clear();
        world.getBodies(bodiesCache);
        for (int i = 0; i < bodiesCache.size; i++) {
            Body body = bodiesCache.get(i);
            if (body.getType() == BodyType.DynamicBody) {
                body.setAwake(true);
            }
        }
    }

    /**
     * Returns the currently active cardinal gravity direction.
     *
     * @return active gravity Direction
     */
    public Direction getGravityDirection() {
        return currentDirection;
    }

    /**
     * Returns a defensive copy of the active gravity acceleration vector in m/s^2.
     *
     * @return defensive copy of active gravity vector
     */
    public Vector2 getGravityVector() {
        return calculateGravityVector(currentDirection);
    }

    /**
     * Returns a defensive copy of the normalized cardinal gravity direction vector (unit length 1.0).
     *
     * @return defensive copy of normalized gravity direction
     */
    public Vector2 getNormalizedGravityDirection() {
        return calculateNormalizedDirection(currentDirection);
    }

    /**
     * Returns the scalar magnitude of gravity acceleration in m/s^2.
     *
     * @return positive gravity magnitude
     */
    public float getGravityMagnitude() {
        return magnitude;
    }

    /**
     * Computes the gravity vector for a specific cardinal direction.
     */
    private Vector2 calculateGravityVector(Direction dir) {
        return switch (dir) {
            case DOWN -> new Vector2(0.0f, -magnitude);
            case UP -> new Vector2(0.0f, magnitude);
            case LEFT -> new Vector2(-magnitude, 0.0f);
            case RIGHT -> new Vector2(magnitude, 0.0f);
        };
    }

    /**
     * Computes the normalized unit direction vector for a specific cardinal direction.
     */
    private Vector2 calculateNormalizedDirection(Direction dir) {
        return switch (dir) {
            case DOWN -> new Vector2(0.0f, -1.0f);
            case UP -> new Vector2(0.0f, 1.0f);
            case LEFT -> new Vector2(-1.0f, 0.0f);
            case RIGHT -> new Vector2(1.0f, 0.0f);
        };
    }
}

