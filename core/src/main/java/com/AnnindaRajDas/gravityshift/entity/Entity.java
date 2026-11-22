package com.AnnindaRajDas.gravityshift.entity;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;

/**
 * Abstract base class representing any physical or interactive object in the game world.
 * Encapsulates the Box2D Body reference and provides controlled access to spatial coordinates.
 *
 * Demonstrates:
 * - Abstraction: Exposes common lifecycle and position contracts while hiding subclass details.
 * - Encapsulation: Protects internal body state and prevents accidental native leaks.
 * - Idempotent Lifecycle: Safe disposal with body destruction synchronization.
 */
public abstract class Entity implements Disposable {

    protected final Body body;
    private boolean markedForRemoval;
    private boolean disposed;

    // Snapshot state for fixed-timestep render interpolation (zero-allocation reusable storage)
    private final Vector2 previousPosition = new Vector2();
    private final Vector2 currentPosition = new Vector2();
    private final Vector2 interpolatedPosition = new Vector2();
    private float previousAngle;
    private float currentAngle;
    private float interpolatedAngle;

    public Entity(Body body) {
        if (body == null) {
            throw new IllegalArgumentException("Entity body cannot be null.");
        }
        this.body = body;
        this.markedForRemoval = false;
        this.disposed = false;

        // Bind this entity instance to the Box2D body's user data for collision dispatch
        this.body.setUserData(this);

        // Initialize snapshot transforms to current body transform to prevent spawn streaks
        resetInterpolation();
    }

    /**
     * Updates domain logic, state timers, or animation states.
     * Called once per frame by EntityManager after the physics step has completed.
     *
     * @param delta time elapsed since last frame in seconds
     */
    public abstract void update(float delta);

    /**
     * Returns a copy of the center of mass position of the entity in world meters.
     * Uses defensive copying (.cpy()) to prevent external mutation of Box2D's internal Vector2.
     *
     * @return current Vector2 position (from Box2D body)
     */
    public Vector2 getPosition() {
        return body.getPosition().cpy();
    }

    public float getX() {
        return body.getPosition().x;
    }

    public float getY() {
        return body.getPosition().y;
    }

    public float getAngle() {
        return body.getAngle();
    }

    /**
     * Captures the entity's physics transform prior to a Box2D simulation step.
     */
    public void capturePreStepTransform() {
        if (body != null && !disposed) {
            Vector2 pos = body.getPosition();
            previousPosition.set(pos.x, pos.y);
            previousAngle = body.getAngle();
        }
    }

    /**
     * Captures the entity's physics transform following a Box2D simulation step.
     */
    public void capturePostStepTransform() {
        if (body != null && !disposed) {
            Vector2 pos = body.getPosition();
            currentPosition.set(pos.x, pos.y);
            currentAngle = body.getAngle();
        }
    }

    /**
     * Resets interpolation snapshots to the current body transform.
     * Prevents visual streaks when spawning, teleporting, or resetting entities.
     */
    public void resetInterpolation() {
        if (body != null && !disposed) {
            Vector2 pos = body.getPosition();
            previousPosition.set(pos.x, pos.y);
            currentPosition.set(pos.x, pos.y);
            interpolatedPosition.set(pos.x, pos.y);
            previousAngle = body.getAngle();
            currentAngle = previousAngle;
            interpolatedAngle = previousAngle;
        }
    }

    /**
     * Alias for {@link #resetInterpolation()}, synchronizing snapshot transforms to current body state.
     */
    public void syncTransform() {
        resetInterpolation();
    }

    /**
     * Computes and returns the linearly interpolated position for rendering using the provided alpha.
     * Never mutates the Box2D body transform.
     * Reuses an internal Vector2 instance without per-frame allocations.
     *
     * @param alpha interpolation factor in [0.0, 1.0]
     * @return reference to internal interpolatedPosition
     */
    public Vector2 getInterpolatedPosition(float alpha) {
        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        interpolatedPosition.x = previousPosition.x + (currentPosition.x - previousPosition.x) * clampedAlpha;
        interpolatedPosition.y = previousPosition.y + (currentPosition.y - previousPosition.y) * clampedAlpha;
        return interpolatedPosition;
    }

    /**
     * Computes the interpolated X coordinate in world meters for rendering without any vector allocation.
     *
     * @param alpha interpolation factor in [0.0, 1.0]
     * @return interpolated X coordinate
     */
    public float getInterpolatedX(float alpha) {
        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        return previousPosition.x + (currentPosition.x - previousPosition.x) * clampedAlpha;
    }

    /**
     * Computes the interpolated Y coordinate in world meters for rendering without any vector allocation.
     *
     * @param alpha interpolation factor in [0.0, 1.0]
     * @return interpolated Y coordinate
     */
    public float getInterpolatedY(float alpha) {
        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        return previousPosition.y + (currentPosition.y - previousPosition.y) * clampedAlpha;
    }

    /**
     * Computes the interpolated angle in radians using the shortest angular path across the wrap boundary.
     * Never mutates the Box2D body transform.
     *
     * @param alpha interpolation factor in [0.0, 1.0]
     * @return interpolated angle in radians
     */
    public float getInterpolatedAngle(float alpha) {
        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        float diff = currentAngle - previousAngle;
        // Wrap diff to [-PI, PI] to interpolate along the shortest arc
        diff = (float) (diff - (2.0 * Math.PI) * Math.round(diff / (2.0 * Math.PI)));
        interpolatedAngle = previousAngle + diff * clampedAlpha;
        return interpolatedAngle;
    }

    public Vector2 getPreviousPosition() {
        return previousPosition;
    }

    public Vector2 getCurrentPosition() {
        return currentPosition;
    }

    public float getPreviousAngle() {
        return previousAngle;
    }

    public float getCurrentAngle() {
        return currentAngle;
    }

    public Body getBody() {
        return body;
    }

    public boolean isMarkedForRemoval() {
        return markedForRemoval;
    }

    public void markForRemoval() {
        this.markedForRemoval = true;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Safely releases this entity's native Box2D Body.
     *
     * Invariants guaranteed:
     * - Idempotent: Calling dispose() multiple times is a safe no-op once completed.
     * - World Safety: If the Box2D World is currently locked (in a simulation step),
     *   destruction is deferred and 'disposed' remains false so EntityManager can retry safely.
     * - Memory Hygiene: Clears body userData before destruction to prevent dangling pointers.
     * - Non-Destructive: Does NOT dispose of the shared Box2D World.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        World world = body.getWorld();
        if (world != null) {
            if (world.isLocked()) {
                // Cannot destroy Box2D bodies while the simulation step is locked.
                // Leave disposed = false so the caller knows destruction could not be completed yet.
                return;
            }
            body.setUserData(null);
            world.destroyBody(body);
        }

        this.disposed = true;
    }
}
