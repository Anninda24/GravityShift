package com.AnnindaRajDas.gravityshift.screen;

import com.AnnindaRajDas.gravityshift.GravityShiftGame;
import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.input.PlayerInputController;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.AnnindaRajDas.gravityshift.render.WorldRenderer;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Main gameplay screen where the physics world, entities, and puzzle
 * interactions run.
 * Coordinates input polling, entity updates, fixed timestep physics, rendering,
 * dual-viewport dispatch (ExtendViewport for world, ScreenViewport for HUD),
 * and global gravity-transition orchestration.
 */
public class PlayScreen extends BaseScreen {

    private final PhysicsController physicsController;
    private final EntityManager entityManager;
    private final WorldRenderer worldRenderer;
    private final Box2DDebugRenderer debugRenderer;
    private final Stage hudStage;
    private final Player player;
    private final PlayerInputController playerInputController;
    private boolean disposed = false;

    public PlayScreen(GravityShiftGame game) {
        this(game, null);
    }

    public PlayScreen(GravityShiftGame game, Stage hudStage) {
        super(game);
        this.physicsController = new PhysicsController();
        this.entityManager = new EntityManager();
        this.worldRenderer = new WorldRenderer();
        this.debugRenderer = new Box2DDebugRenderer();

        // Initialize HUD stage for screen-space UI elements
        this.hudStage = (hudStage != null) ? hudStage : new Stage(hudViewport);

        // Spawn player at (x=3.0m, y=5.0m) above the ground platform
        this.player = Player.create(physicsController.getBodyFactory(), 3.0f, 5.0f);
        this.playerInputController = new PlayerInputController(player);
        this.entityManager.addEntity(player);

        // Bind EntityManager as step listener to capture pre/post physics snapshots for render interpolation
        this.physicsController.setStepListener(this.entityManager);
    }

    /**
     * Orchestrates a global gravity shift across the physics world and entity
     * layer.
     * Changes Box2D world gravity via GravityController and synchronizes player
     * support direction.
     *
     * Invariants enforced:
     * - Rejects null direction with IllegalArgumentException.
     * - Rejects invocation after disposal with IllegalStateException.
     * - Idempotent early exit if the requested direction is already active.
     * - Safe ordering: GravityController executes first (enforcing world-lock
     * guards);
     * Player support direction is synchronized only after the world gravity change
     * succeeds.
     *
     * @param direction cardinal direction to shift gravity towards
     * @throws IllegalArgumentException if direction is null
     * @throws IllegalStateException    if the screen is disposed or the physics
     *                                  world is locked
     */
    public void setGravityDirection(Direction direction) {
        if (disposed) {
            throw new IllegalStateException("Cannot change gravity on a disposed PlayScreen.");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Gravity direction cannot be null.");
        }

        // Idempotent: avoid redundant work if direction is unchanged
        if (physicsController.getGravityController().getGravityDirection() == direction) {
            return;
        }

        // 1. Shift world gravity first (GravityController validates locked-world guard)
        physicsController.getGravityController().setGravityDirection(direction);

        // 2. Synchronize player support direction only after world gravity change
        // succeeds
        if (player != null && !player.isDisposed()) {
            player.onGravityDirectionChanged(direction);
        }
    }

    public Direction getGravityDirection() {
        return physicsController.getGravityController().getGravityDirection();
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void render(float delta) {
        // 1. Process keyboard controller inputs before entity and physics updates
        playerInputController.update();

        // 2. Update entity domain logic and lifecycle
        entityManager.update(delta);

        // 3. Advance the physics simulation using the fixed timestep accumulator
        physicsController.update(delta);

        // 4. Clear screen buffer with sleek background color
        ScreenUtils.clear(0.08f, 0.09f, 0.12f, 1.0f);

        // 5. Apply world camera and viewport projection
        viewport.apply();
        camera.update();

        // 6. Delegate visual world rendering with interpolation alpha
        worldRenderer.render(camera, entityManager, physicsController.getInterpolationAlpha());

        // 7. Delegate physics debug wireframe rendering if enabled
        if (GameConfig.DEBUG_PHYSICS) {
            debugRenderer.render(physicsController.getWorld(), camera.combined);
        }

        // 8. Apply HUD viewport and render UI/HUD stage in screen-pixel space
        hudViewport.apply();
        if (hudStage != null) {
            hudStage.act(delta);
            hudStage.draw();
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (hudStage != null && hudStage.getViewport() != null) {
            hudStage.getViewport().update(width, height, true);
        }
    }

    public Stage getHudStage() {
        return hudStage;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerInputController getPlayerInputController() {
        return playerInputController;
    }

    public PhysicsController getPhysicsController() {
        return physicsController;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        super.dispose();

        // 1. Dispose HUD stage
        if (hudStage != null) {
            hudStage.dispose();
        }

        // 2. Dispose entity bodies
        entityManager.dispose();

        // 3. Release rendering resources
        worldRenderer.dispose();
        debugRenderer.dispose();

        // 3. Verify all entity bodies were destroyed
        if (!entityManager.isDisposed()) {
            throw new IllegalStateException(
                    "EntityManager disposal incomplete before PhysicsController disposal.");
        }

        // 4. Destroy the Box2D world LAST
        physicsController.dispose();
    }
}
