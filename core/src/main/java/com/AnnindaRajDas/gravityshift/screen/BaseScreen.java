package com.AnnindaRajDas.gravityshift.screen;

import com.AnnindaRajDas.gravityshift.GravityShiftGame;
import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Abstract base screen providing shared camera, viewport, and lifecycle management.
 * Follows the Template Method pattern and encourages code reuse across screens.
 */
public abstract class BaseScreen implements Screen {

    protected final GravityShiftGame game;
    protected final OrthographicCamera camera;
    protected final Viewport viewport;

    public BaseScreen(GravityShiftGame game) {
        this.game = game;
        this.camera = new OrthographicCamera();
        // FitViewport maintains constant world dimensions and aspect ratio,
        // adding letterbox/pillarbox margins if the display aspect ratio varies.
        this.viewport = new FitViewport(GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT, camera);
        this.camera.position.set(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f, 0f);
        this.camera.update();
    }

    @Override
    public void show() {
        // Subclasses can override to initialize screen-specific listeners or assets
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewport.update(width, height, true);
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
}

