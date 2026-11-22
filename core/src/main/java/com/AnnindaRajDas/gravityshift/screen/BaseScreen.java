package com.AnnindaRajDas.gravityshift.screen;

import com.AnnindaRajDas.gravityshift.GravityShiftGame;
import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Abstract base screen providing dual-viewport management:
 * - World Viewport (ExtendViewport): Minimum 20x12m chamber, expands visible world without scaling or distortion.
 * - HUD Viewport (ScreenViewport): Screen-space pixel coordinates for UI, menus, and controls.
 *
 * Follows the Template Method pattern and encapsulates clean separation between world meters and UI pixels.
 */
public abstract class BaseScreen implements Screen {

    protected final GravityShiftGame game;
    protected final OrthographicCamera camera;       // World camera (metres)
    protected final ExtendViewport viewport;          // World viewport (metres)
    protected final OrthographicCamera hudCamera;    // HUD camera (screen pixels)
    protected final ScreenViewport hudViewport;      // HUD viewport (screen pixels)

    public BaseScreen(GravityShiftGame game) {
        this.game = game;

        // 1. World Viewport (ExtendViewport)
        // Minimum world dimensions: 20x12 meters. Keeps entire minimum chamber visible.
        // Extra screen space extends the visible world rather than stretching or letterboxing.
        this.camera = new OrthographicCamera();
        this.viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);
        this.camera.position.set(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f, 0f);
        this.camera.update();

        // 2. HUD Viewport (ScreenViewport)
        // Screen-space 1:1 pixel coordinate mapping, independent of world meters.
        this.hudCamera = new OrthographicCamera();
        this.hudViewport = new ScreenViewport(hudCamera);
        this.hudCamera.update();
    }

    @Override
    public void show() {
        // Subclasses can override to initialize screen-specific listeners or assets
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;

        // Update world viewport; do not center on extended world size,
        // center explicitly on the 20x12 minimum chamber to keep extra space symmetric
        viewport.update(width, height, false);
        camera.position.set(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f, 0f);
        camera.update();

        // Update HUD viewport; centerCamera = true places (0,0) at bottom-left in screen pixels
        hudViewport.update(width, height, true);
    }

    @Override
    public void pause() {
        // Subclasses can override for mobile pause events
    }

    @Override
    public void resume() {
        // Subclasses can override for mobile resume events
    }

    @Override
    public void hide() {
        // Subclasses can override when transitioning away
    }

    @Override
    public void dispose() {
        // Subclasses can override to release screen-specific resources
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public OrthographicCamera getWorldCamera() {
        return camera;
    }

    public ExtendViewport getWorldViewport() {
        return viewport;
    }

    public OrthographicCamera getHudCamera() {
        return hudCamera;
    }

    public ScreenViewport getHudViewport() {
        return hudViewport;
    }
}
