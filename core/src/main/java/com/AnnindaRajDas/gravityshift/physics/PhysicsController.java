package com.AnnindaRajDas.gravityshift.physics;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;

/**
 * Encapsulates the Box2D physics simulation world and handles fixed timestep stepping.
 * Controls world lifecycle, gravity configurations, and body factory delegation.
 */
public class PhysicsController implements Disposable {

    private final World world;
    private final GravityController gravityController;
    private final BodyFactory bodyFactory;
    private float accumulator;
    private PhysicsStepListener stepListener;
    private boolean disposed = false;

    public PhysicsController() {
        // Initialize native Box2D binaries once
        Box2D.init();

        // Initialize World with zero gravity; GravityController exclusively owns and sets initial gravity
        this.world = new World(new Vector2(0.0f, 0.0f), true);
        this.gravityController = new GravityController(world);
        this.world.setContactListener(new GameContactListener());
        this.bodyFactory = new BodyFactory(world);
        this.accumulator = 0.0f;

        // Construct the initial static collision environment
        createInitialEnvironment();
    }

    /**
     * Advances the physics simulation using an accumulator-based fixed timestep.
     * Raw variable frame delta time is clamped to MAX_FRAME_DELTA and consumed in discrete TIME_STEP slices.
     * Executes no more than MAX_PHYSICS_STEPS_PER_FRAME steps per frame to prevent simulation death spirals.
     * Safely ignores negative or non-finite deltas, and retains the fractional accumulator remainder for render interpolation.
     *
     * @param delta rendering delta time in seconds
     */
    public void update(float delta) {
        if (disposed) {
            throw new IllegalStateException("Cannot update a disposed PhysicsController.");
        }
        if (!Float.isFinite(delta) || delta <= 0.0f) {
            return;
        }

        // Clamp frame time to prevent simulation explosion ("spiral of death")
        // and cap physics catch-up to at most MAX_PHYSICS_STEPS_PER_FRAME steps per rendered frame
        float frameTime = Math.min(delta, GameConfig.MAX_FRAME_DELTA);
        accumulator += frameTime;

        int stepCount = 0;
        while (accumulator >= GameConfig.TIME_STEP && stepCount < GameConfig.MAX_PHYSICS_STEPS_PER_FRAME) {
            if (stepListener != null) {
                stepListener.onPreStep();
            }

            world.step(GameConfig.TIME_STEP, GameConfig.VELOCITY_ITERATIONS, GameConfig.POSITION_ITERATIONS);

            if (stepListener != null) {
                stepListener.onPostStep();
            }

            accumulator -= GameConfig.TIME_STEP;
            stepCount++;
        }

        // Discard excess accumulator time if the step limit was hit to prevent unbounded catch-up spirals,
        // while strictly preserving the fractional remainder for interpolation
        if (accumulator >= GameConfig.TIME_STEP) {
            accumulator %= GameConfig.TIME_STEP;
        }
        if (accumulator < 0.0f) {
            accumulator = 0.0f;
        }
    }

    /**
     * Computes the normalized interpolation alpha representing the fractional progression
     * between the previous physics state and the current physics state.
     * Guaranteed to be in [0.0, 1.0].
     *
     * @return interpolation factor alpha in [0.0, 1.0]
     */
    public float getInterpolationAlpha() {
        float alpha = accumulator / GameConfig.TIME_STEP;
        if (alpha < 0.0f) {
            return 0.0f;
        }
        if (alpha > 1.0f) {
            return 1.0f;
        }
        return alpha;
    }

    /**
     * Returns the fractional accumulator remainder in seconds.
     *
     * @return current accumulator remainder
     */
    public float getAccumulatorRemainder() {
        return accumulator;
    }

    public void setStepListener(PhysicsStepListener stepListener) {
        this.stepListener = stepListener;
    }

    public PhysicsStepListener getStepListener() {
        return stepListener;
    }

    /**
     * Builds the static physics boundaries and platforms corresponding to the 20x12 chamber.
     */
    private void createInitialEnvironment() {
        // Floor / Ground Platform (matches WorldRenderer visual ground: x=1m, y=0.5m, w=18m, h=0.8m)
        bodyFactory.createStaticBox(1.0f, 0.5f, 18.0f, 0.8f, FixtureType.GROUND);

        // Suspended Puzzle Platform (matches WorldRenderer visual ledge: x=6m, y=5.5m, w=8m, h=0.6m)
        bodyFactory.createStaticBox(6.0f, 5.5f, 8.0f, 0.6f, FixtureType.GROUND);

        // Perimeter Boundary Walls (0.5m thickness to prevent tunneling)
        // Left Wall
        bodyFactory.createStaticBox(0.0f, 0.0f, 0.5f, GameConfig.WORLD_HEIGHT, FixtureType.WALL);

        // Right Wall
        bodyFactory.createStaticBox(GameConfig.WORLD_WIDTH - 0.5f, 0.0f, 0.5f, GameConfig.WORLD_HEIGHT, FixtureType.WALL);

        // Ceiling
        bodyFactory.createStaticBox(0.0f, GameConfig.WORLD_HEIGHT - 0.5f, GameConfig.WORLD_WIDTH, 0.5f, FixtureType.CEILING);

        // Bottom Perimeter Base
        bodyFactory.createStaticBox(0.0f, 0.0f, GameConfig.WORLD_WIDTH, 0.5f, FixtureType.GROUND);
    }

    public World getWorld() {
        return world;
    }

    public BodyFactory getBodyFactory() {
        return bodyFactory;
    }

    public GravityController getGravityController() {
        return gravityController;
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            // Release native C++ Box2D world memory
            world.dispose();
        }
    }
}

