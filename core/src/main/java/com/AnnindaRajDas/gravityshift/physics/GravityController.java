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

    public static final float MAX_GRAVITY_SWITCH_SPEED = GameConfig.MAX_GRAVITY_SWITCH_SPEED;

    private final World world;
    private final float magnitude;
    private Direction currentDirection;

    // Reusable caches to avoid per-shift heap allocations
    private final Array<Body> bodiesCache = new Array<>();
    private final Vector2 gravityVectorBuffer = new Vector2();

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
     * Validates that the world is unlocked, clamps any dynamic body speeds exceeding
     * MAX_GRAVITY_SWITCH_SPEED (15 m/s) while preserving direction, switches world gravity,
     * and wakes all dynamic bodies.
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

        // 1. Validate that the physics world is not locked
        if (world.isLocked()) {
            throw new IllegalStateException("Cannot change gravity while the physics world is locked.");
        }

        // 2-4. Read current world-space velocities and clamp magnitude > 15 m/s while preserving direction
        bodiesCache.clear();
        world.getBodies(bodiesCache);
        for (int i = 0; i < bodiesCache.size; i++) {
            Body body = bodiesCache.get(i);
            if (body.getType() == BodyType.DynamicBody) {
                clampBodyVelocityToMaxSpeed(body, MAX_GRAVITY_SWITCH_SPEED);
            }
        }

        // 5. Change the Box2D world gravity vector
        setGravityVectorBuffer(direction);
        world.setGravity(gravityVectorBuffer);
        this.currentDirection = direction;

        // 6. Wake all dynamic bodies so they immediately respond to the new gravity field
        wakeDynamicBodiesCached();
    }

    /**
     * Clamps a dynamic body's world-space linear velocity magnitude to at most maxSpeed,
     * preserving travel direction and leaving speeds <= maxSpeed untouched.
     * Allocation-free and safe against zero velocity or non-finite inputs.
     *
     * @param body     the Box2D body to inspect and clamp if necessary
     * @param maxSpeed maximum permitted speed in m/s
     */
    public static void clampBodyVelocityToMaxSpeed(Body body, float maxSpeed) {
        if (body == null || body.getType() != BodyType.DynamicBody) {
            return;
        }
        if (!Float.isFinite(maxSpeed) || maxSpeed <= 0.0f) {
            return;
        }
        Vector2 velocity = body.getLinearVelocity();
        float vx = velocity.x;
        float vy = velocity.y;
        if (!Float.isFinite(vx) || !Float.isFinite(vy)) {
            body.setLinearVelocity(0.0f, 0.0f);
            return;
        }
        double speedSq = (double) vx * vx + (double) vy * vy;
        double maxSpeedSq = (double) maxSpeed * maxSpeed;
        if (speedSq <= maxSpeedSq) {
            return;
        }
        double speed = Math.sqrt(speedSq);
        if (speed <= 0.0 || !Double.isFinite(speed)) {
            return;
        }
        double scale = (double) maxSpeed / speed;
        float newVx = (float) (vx * scale);
        float newVy = (float) (vy * scale);

        // Guard against 1-ULP upward float32 rounding causing speedSq > maxSpeedSq
        if ((double) newVx * newVx + (double) newVy * newVy > maxSpeedSq) {
            double safeScale = Math.nextDown(scale);
            newVx = (float) (vx * safeScale);
            newVy = (float) (vy * safeScale);
            if ((double) newVx * newVx + (double) newVy * newVy > maxSpeedSq) {
                newVx = (newVx > 0.0f) ? Math.nextDown(newVx) : ((newVx < 0.0f) ? Math.nextUp(newVx) : 0.0f);
                newVy = (newVy > 0.0f) ? Math.nextDown(newVy) : ((newVy < 0.0f) ? Math.nextUp(newVy) : 0.0f);
            }
        }
        body.setLinearVelocity(newVx, newVy);
    }

    private void setGravityVectorBuffer(Direction dir) {
        switch (dir) {
            case DOWN -> gravityVectorBuffer.set(0.0f, -magnitude);
            case UP -> gravityVectorBuffer.set(0.0f, magnitude);
            case LEFT -> gravityVectorBuffer.set(-magnitude, 0.0f);
            case RIGHT -> gravityVectorBuffer.set(magnitude, 0.0f);
        }
    }

    /**
     * Wakes all dynamic bodies currently cached in bodiesCache while leaving static and kinematic
     * bodies untouched. Existing linear and angular velocities are preserved.
     */
    private void wakeDynamicBodiesCached() {
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

