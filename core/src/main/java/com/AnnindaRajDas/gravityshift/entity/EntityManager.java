package com.AnnindaRajDas.gravityshift.entity;

import com.AnnindaRajDas.gravityshift.physics.PhysicsStepListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Manages the collection and lifecycle of active game entities.
 * Handles safe addition, removal, per-frame updates, and disposal without
 * concurrent modification issues during iteration.
 *
 * Implements PhysicsStepListener to coordinate pre-step and post-step transform snapshots
 * across all active entities for fixed-timestep render interpolation.
 */
public class EntityManager implements Disposable, PhysicsStepListener {

    // Active entities in the simulation
    private final Array<Entity> entities;
    // Buffer for entities queued to be added (prevents mutation while iterating active list)
    private final Array<Entity> pendingAdditions;

    private boolean disposed;

    public EntityManager() {
        this.entities = new Array<>(false, 16);
        this.pendingAdditions = new Array<>(false, 16);
        this.disposed = false;
    }

    

    /**
     * Queues an entity to be added safely on the next update cycle.
     * Prevents duplicate registration and rejects additions if disposed or if entity is already disposed.
     *
     * @param entity the entity to add
     */
    public void addEntity(Entity entity) {
        if (disposed) {
            throw new IllegalStateException("Cannot add entities to a disposed EntityManager.");
        }
        if (entity == null) {
            throw new IllegalArgumentException("Cannot add a null entity.");
        }
        if (entity.isDisposed()) {
            throw new IllegalArgumentException("Cannot add an already-disposed entity.");
        }

        // Prevent duplicate registration (identity check across active and pending)
        if (contains(entity)) {
            return;
        }

        // Initialize snapshot transforms immediately upon registration
        entity.resetInterpolation();
        pendingAdditions.add(entity);
    }

    @Override
    public void onPreStep() {
        if (disposed) {
            return;
        }
        flushPendingAdditions();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            if (!entity.isDisposed()) {
                entity.capturePreStepTransform();
                entity.onPrePhysicsStep(com.AnnindaRajDas.gravityshift.config.GameConfig.TIME_STEP);
            }
        }
    }

    private void flushPendingAdditions() {
        if (pendingAdditions.size > 0) {
            for (int i = 0; i < pendingAdditions.size; i++) {
                Entity pending = pendingAdditions.get(i);
                if (pending.isDisposed()) {
                    continue;
                }
                if (pending.isMarkedForRemoval()) {
                    pending.dispose();
                    if (!pending.isDisposed()) {
                        if (!entities.contains(pending, true)) {
                            entities.add(pending);
                        }
                    }
                } else if (!entities.contains(pending, true)) {
                    entities.add(pending);
                }
            }
            pendingAdditions.clear();
        }
    }

    @Override
    public void onPostStep() {
        if (disposed) {
            return;
        }
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            if (!entity.isDisposed()) {
                entity.capturePostStepTransform();
                entity.onPostPhysicsStep(com.AnnindaRajDas.gravityshift.config.GameConfig.TIME_STEP);
            }
        }
    }

    /**
     * Updates all managed entities and safely cleans up any marked for removal.
     * Guarantees that entities are only removed from the manager after their
     * physics bodies have been successfully destroyed.
     * Never invokes update() on an already-disposed entity.
     *
     * @param delta time elapsed in seconds
     */
    public void update(float delta) {
        if (disposed) {
            return;
        }

        // 1. Flush pending additions into active entity collection safely
        flushPendingAdditions();

        // 2. Iterate backwards for safe in-place removal and update
        for (int i = entities.size - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            if (entity.isDisposed()) {
                // Already disposed: remove immediately, never call update()
                entities.removeIndex(i);
            } else if (entity.isMarkedForRemoval()) {
                // Attempt to safely destroy body and release native resources
                entity.dispose();

                // Only remove from collection if body destruction succeeded
                if (entity.isDisposed()) {
                    entities.removeIndex(i);
                }
                // If destruction was deferred (e.g. world locked), the entity remains
                // in the list and removal will be retried on the next update.
            } else {
                entity.update(delta);
            }
        }
    }

    /**
     * Returns the number of currently active entities.
     *
     * @return count of active entities
     */
    public int getEntityCount() {
        return entities.size;
    }

    /**
     * Provides controlled, read-only indexed access to an active entity.
     *
     * @param index zero-based index of the entity
     * @return the Entity at the specified index
     */
    public Entity getEntityAt(int index) {
        if (index < 0 || index >= entities.size) {
            throw new IndexOutOfBoundsException(
                "Invalid entity index: " + index + ", active count: " + entities.size
            );
        }
        return entities.get(index);
    }

    /**
     * Checks if an entity is already managed or queued.
     *
     * @param entity the entity to query
     * @return true if entity is already active or pending
     */
    public boolean contains(Entity entity) {
        if (entity == null) return false;
        return entities.contains(entity, true) || pendingAdditions.contains(entity, true);
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Idempotently disposes all active and pending entities.
     *
     * Invariants guaranteed:
     * - Does NOT clear or abandon entities whose disposal could not complete (e.g., if the Box2D world was locked).
     * - Only marks the manager as disposed (isDisposed() == true) when ALL managed entities report isDisposed() == true.
     * - Undisposed entities are retained so disposal can be retried once the physics world is unlocked.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        
        // 1. Attempt to dispose pending entities; remove only those that successfully disposed
        for (int i = pendingAdditions.size - 1; i >= 0; i--) {
            Entity pending = pendingAdditions.get(i);
            pending.dispose();
            if (pending.isDisposed()) {
                pendingAdditions.removeIndex(i);
            }
        }

        // 2. Attempt to dispose active entities; remove only those that successfully disposed
        for (int i = entities.size - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            entity.dispose();
            if (entity.isDisposed()) {
                entities.removeIndex(i);
            }
        }

        // 3. Complete manager disposal only if every entity was successfully disposed
        if (entities.size == 0 && pendingAdditions.size == 0) {
            this.disposed = true;
        }
    }
}


