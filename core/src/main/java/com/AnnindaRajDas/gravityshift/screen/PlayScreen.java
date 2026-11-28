package com.AnnindaRajDas.gravityshift.screen;

import com.AnnindaRajDas.gravityshift.GravityShiftGame;
import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.debug.DebugOverlay;
import com.AnnindaRajDas.gravityshift.debug.FallbackRuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
import com.AnnindaRajDas.gravityshift.entity.ExitPortal;
import com.AnnindaRajDas.gravityshift.entity.GravityCube;
import com.AnnindaRajDas.gravityshift.entity.LaserHazard;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.entity.PressurePlate;
import com.AnnindaRajDas.gravityshift.entity.SlidingDoor;
import com.AnnindaRajDas.gravityshift.level.ChamberDefinition;
import com.AnnindaRajDas.gravityshift.level.TmxChamberLoader;
import com.AnnindaRajDas.gravityshift.input.CompositeInputProvider;
import com.AnnindaRajDas.gravityshift.input.KeyboardInput;
import com.AnnindaRajDas.gravityshift.input.PlayerInputController;
import com.AnnindaRajDas.gravityshift.input.TouchHud;
import com.AnnindaRajDas.gravityshift.input.TouchInput;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerBus;
import com.AnnindaRajDas.gravityshift.render.WorldRenderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
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
    private final TriggerBus triggerBus;
    private final GravityCube gravityCube;
    private final PressurePlate pressurePlate;
    private final SlidingDoor slidingDoor;
    private final LaserHazard laserHazard;
    private boolean pendingRespawn = false;
    private boolean pendingNextChamber = false;
    private int currentChamberNumber = 1;
    private ChamberDefinition currentChamber;
    private final KeyboardInput keyboardInput;
    private final TouchInput touchInput;
    private final CompositeInputProvider inputProvider;
    private final PlayerInputController playerInputController;
    private final TouchHud touchHud;
    private final InputMultiplexer inputMultiplexer;
    private final DebugOverlay debugOverlay;
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
        this.triggerBus = new TriggerBus();
        this.gravityCube = GravityCube.create(physicsController.getBodyFactory(), 6.5f, 1.5f);
        this.pressurePlate = PressurePlate.create(
                physicsController.getBodyFactory(), 11.0f, 1.0f, 2.0f, 0.6f, "door_1", triggerBus);
        this.slidingDoor = SlidingDoor.create(
                physicsController.getBodyFactory(), 16.0f, 1.0f, 0.6f, 3.5f, "door_1", triggerBus);
        this.laserHazard = LaserHazard.create(
                physicsController.getBodyFactory(), 9.0f, 9.5f, 2.0f, 0.35f,
                () -> pendingRespawn = true);

        this.keyboardInput = new KeyboardInput();
        this.touchInput = new TouchInput();
        this.inputProvider = new CompositeInputProvider(keyboardInput, touchInput);
        this.playerInputController = new PlayerInputController(player, inputProvider);
        this.touchHud = new TouchHud(touchInput);
        this.inputMultiplexer = (this.hudStage != null)
                ? new InputMultiplexer(this.hudStage, this.touchInput)
                : new InputMultiplexer(this.touchInput);
        this.entityManager.addEntity(player);
        this.entityManager.addEntity(gravityCube);
        this.entityManager.addEntity(pressurePlate);
        this.entityManager.addEntity(slidingDoor);
        this.entityManager.addEntity(laserHazard);
        this.entityManager.addEntity(ExitPortal.create(
                physicsController.getBodyFactory(), 17.5f, 1.0f, 1.5f, 2.0f,
                () -> pendingNextChamber = true));
        if (Gdx.app != null) {
            Gdx.app.log("Phase5Diag", "PlayScreen initialized: plate=" + pressurePlate.getChannelId());
        }

        // Bind EntityManager as step listener to capture pre/post physics snapshots for render interpolation
        this.physicsController.setStepListener(this.entityManager);

        // Initialize lightweight debug overlay with platform metrics provider
        RuntimeMetricsProvider provider = (game != null && game.getMetricsProvider() != null)
            ? game.getMetricsProvider()
            : new FallbackRuntimeMetricsProvider();
        this.debugOverlay = new DebugOverlay(provider, GameConfig.DEBUG_OVERLAY);
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
    public void show() {
        super.show();
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(inputMultiplexer);
        }
    }

    @Override
    public void render(float delta) {
        // 1. Process controller inputs (keyboard + touch) before entity and physics updates
        playerInputController.update();
        Direction requestedGravity = playerInputController.consumeGravityRequest(getGravityDirection());
        if (requestedGravity != null) {
            setGravityDirection(requestedGravity);
        }

        // 2. Update entity domain logic and lifecycle
        entityManager.update(delta);

        // 3. Advance the physics simulation using the fixed timestep accumulator
        physicsController.update(delta);

        // 3b. Process deferred respawn or chamber transition outside Box2D world lock
        if (pendingNextChamber) {
            pendingNextChamber = false;
            int next = (currentChamberNumber % 8) + 1;
            currentChamberNumber = next;
            currentChamber = TmxChamberLoader.loadChamber(currentChamberNumber);
            if (!player.isDisposed()) {
                player.getBody().setTransform(currentChamber.getSpawnX(), currentChamber.getSpawnY(), 0.0f);
                player.getBody().setLinearVelocity(0.0f, 0.0f);
                player.resetInterpolation();
            }
            setGravityDirection(Direction.DOWN);
            if (playerInputController != null) {
                playerInputController.reset();
            }
        }
        if (pendingRespawn || (Gdx.input != null && Gdx.input.isKeyJustPressed(Input.Keys.R))) {
            pendingRespawn = false;
            if (!player.isDisposed()) {
                float sx = (currentChamber != null) ? currentChamber.getSpawnX() : 3.0f;
                float sy = (currentChamber != null) ? currentChamber.getSpawnY() : 5.0f;
                player.getBody().setTransform(sx, sy, 0.0f);
                player.getBody().setLinearVelocity(0.0f, 0.0f);
                player.resetInterpolation();
            }
            setGravityDirection(Direction.DOWN);
            if (playerInputController != null) {
                playerInputController.reset();
            }
        }

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
        touchHud.render(hudCamera, getGravityDirection());

        // 9. Optional F3 toggle & lightweight zero-per-frame-allocation debug overlay
        if (Gdx.input != null && Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            debugOverlay.setEnabled(!debugOverlay.isEnabled());
        }
        if (debugOverlay.isEnabled()) {
            int fps = (Gdx.graphics != null) ? Gdx.graphics.getFramesPerSecond() : 0;
            debugOverlay.update(delta, fps);
            debugOverlay.render(hudCamera);
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (hudStage != null && hudStage.getViewport() != null) {
            hudStage.getViewport().update(width, height, true);
        }
        if (touchInput != null) {
            touchInput.resize(width, height);
        }
        if (debugOverlay != null) {
            debugOverlay.resize(width, height);
        }
    }

    @Override
    public void pause() {
        super.pause();
        if (playerInputController != null) {
            playerInputController.reset();
        }
    }

    @Override
    public void hide() {
        super.hide();
        if (playerInputController != null) {
            playerInputController.reset();
        }
    }

    public DebugOverlay getDebugOverlay() {
        return debugOverlay;
    }

    public void setDebugOverlayEnabled(boolean enabled) {
        if (debugOverlay != null) {
            debugOverlay.setEnabled(enabled);
        }
    }

    public boolean isDebugOverlayEnabled() {
        return debugOverlay != null && debugOverlay.isEnabled();
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

        // 0. Clear input processor and reset active pointers
        if (playerInputController != null) {
            playerInputController.reset();
        }
        if (Gdx.input != null && Gdx.input.getInputProcessor() == inputMultiplexer) {
            Gdx.input.setInputProcessor(null);
        }
        if (touchHud != null) {
            touchHud.dispose();
        }

        // 1. Dispose HUD stage
        if (hudStage != null) {
            hudStage.dispose();
        }

        // 2. Dispose entity bodies
        entityManager.dispose();

        // 3. Release rendering and debug overlay resources
        if (debugOverlay != null) {
            debugOverlay.dispose();
        }
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
