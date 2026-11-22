package com.AnnindaRajDas.gravityshift.render;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.Entity;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;

/**
 * Handles rendering the game world and its visual elements using ShapeRenderer.
 * Strictly operates in world units (meters), completely decoupled from screen pixels.
 */
public class WorldRenderer implements Disposable {

    private final ShapeRenderer shapeRenderer;

    // Palette Colors
    private static final Color COLOR_GRID = new Color(0.14f, 0.17f, 0.23f, 0.7f);
    private static final Color COLOR_GRID_MAJOR = new Color(0.20f, 0.25f, 0.35f, 0.9f);
    private static final Color COLOR_BOUNDARY = new Color(0.35f, 0.45f, 0.60f, 1.0f);
    private static final Color COLOR_GROUND = new Color(0.22f, 0.55f, 0.45f, 1.0f);
    private static final Color COLOR_GROUND_OUTLINE = new Color(0.35f, 0.75f, 0.60f, 1.0f);
    private static final Color COLOR_PLATFORM = new Color(0.25f, 0.40f, 0.58f, 1.0f);
    private static final Color COLOR_PLATFORM_OUTLINE = new Color(0.40f, 0.60f, 0.85f, 1.0f);

    // Entity Colors
    private static final Color COLOR_PLAYER = new Color(0.95f, 0.55f, 0.20f, 1.0f);
    private static final Color COLOR_PLAYER_OUTLINE = new Color(1.0f, 0.75f, 0.35f, 1.0f);
    private static final Color COLOR_PLAYER_VISOR = new Color(0.20f, 0.85f, 0.95f, 1.0f);

    public WorldRenderer() {
        this.shapeRenderer = new ShapeRenderer();
    }

    /**
     * Renders world geometry and active entities using the provided interpolation alpha.
     *
     * @param camera        the orthographic camera defining the world view
     * @param entityManager manager providing active entities to render
     * @param alpha         fixed-timestep interpolation factor in [0.0, 1.0]
     */
    public void render(OrthographicCamera camera, EntityManager entityManager, float alpha) {
        // Set projection matrix to world-space camera
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Enable alpha blending for subtle grid rendering
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        renderGrid();
        renderBoundaries();
        renderPlatforms();
        renderEntities(entityManager, alpha);

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Renders world geometry and active entities without interpolation (alpha = 1.0).
     *
     * @param camera        the orthographic camera defining the world view
     * @param entityManager manager providing active entities to render
     */
    public void render(OrthographicCamera camera, EntityManager entityManager) {
        render(camera, entityManager, 1.0f);
    }

    /**
     * Renders world geometry using the provided camera projection.
     *
     * @param camera the orthographic camera defining the world view
     */
    public void render(OrthographicCamera camera) {
        render(camera, null);
    }

    /**
     * Draws a subtle 1-meter coordinate grid with highlighted 5-meter major axes.
     */
    private void renderGrid() {
        shapeRenderer.begin(ShapeType.Line);

        // Vertical grid lines (every 1 meter)
        for (float x = 0; x <= GameConfig.WORLD_WIDTH; x += 1.0f) {
            boolean isMajor = ((int) x % 5 == 0);
            shapeRenderer.setColor(isMajor ? COLOR_GRID_MAJOR : COLOR_GRID);
            shapeRenderer.line(x, 0, x, GameConfig.WORLD_HEIGHT);
        }

        // Horizontal grid lines (every 1 meter)
        for (float y = 0; y <= GameConfig.WORLD_HEIGHT; y += 1.0f) {
            boolean isMajor = ((int) y % 5 == 0);
            shapeRenderer.setColor(isMajor ? COLOR_GRID_MAJOR : COLOR_GRID);
            shapeRenderer.line(0, y, GameConfig.WORLD_WIDTH, y);
        }

        shapeRenderer.end();
    }

    /**
     * Draws the outer bounding box of the virtual world.
     */
    private void renderBoundaries() {
        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(COLOR_BOUNDARY);
        // Outer border rectangle (20m x 12m)
        shapeRenderer.rect(0.05f, 0.05f, GameConfig.WORLD_WIDTH - 0.1f, GameConfig.WORLD_HEIGHT - 0.1f);
        shapeRenderer.end();
    }

    /**
     * Draws static visual platforms (ground and floating puzzle ledge).
     */
    private void renderPlatforms() {
        // --- 1. Filled geometry ---
        shapeRenderer.begin(ShapeType.Filled);

        // Ground platform: x=1m to 19m, y=0.5m, height=0.8m
        shapeRenderer.setColor(COLOR_GROUND);
        shapeRenderer.rect(1.0f, 0.5f, 18.0f, 0.8f);

        // Suspended puzzle platform: x=6m to 14m, y=5.5m, height=0.6m
        shapeRenderer.setColor(COLOR_PLATFORM);
        shapeRenderer.rect(6.0f, 5.5f, 8.0f, 0.6f);

        shapeRenderer.end();

        // --- 2. Platform outlines for visual contrast ---
        shapeRenderer.begin(ShapeType.Line);

        shapeRenderer.setColor(COLOR_GROUND_OUTLINE);
        shapeRenderer.rect(1.0f, 0.5f, 18.0f, 0.8f);

        shapeRenderer.setColor(COLOR_PLATFORM_OUTLINE);
        shapeRenderer.rect(6.0f, 5.5f, 8.0f, 0.6f);

        shapeRenderer.end();
    }

    /**
     * Renders all active entities using ShapeRenderer in world coordinates using interpolated transforms.
     *
     * @param entityManager manager providing active entities to render
     * @param alpha         fixed-timestep interpolation factor in [0.0, 1.0]
     */
    private void renderEntities(EntityManager entityManager, float alpha) {
        if (entityManager == null || entityManager.getEntityCount() == 0) {
            return;
        }

        // 1. Filled geometry
        shapeRenderer.begin(ShapeType.Filled);
        for (int i = 0; i < entityManager.getEntityCount(); i++) {
            Entity entity = entityManager.getEntityAt(i);
            if (entity instanceof Player player) {
                float x = player.getInterpolatedX(alpha) - player.getWidth() / 2.0f;
                float y = player.getInterpolatedY(alpha) - player.getHeight() / 2.0f;

                // Player main body
                shapeRenderer.setColor(COLOR_PLAYER);
                shapeRenderer.rect(x, y, player.getWidth(), player.getHeight());

                // Stylized cyan visor near the top of the character
                shapeRenderer.setColor(COLOR_PLAYER_VISOR);
                float visorHeight = 0.25f;
                float visorWidth = player.getWidth() * 0.7f;
                float visorX = x + (player.getWidth() - visorWidth) / 2.0f;
                float visorY = y + player.getHeight() * 0.65f;
                shapeRenderer.rect(visorX, visorY, visorWidth, visorHeight);
            }
        }
        shapeRenderer.end();

        // 2. Outline geometry
        shapeRenderer.begin(ShapeType.Line);
        for (int i = 0; i < entityManager.getEntityCount(); i++) {
            Entity entity = entityManager.getEntityAt(i);
            if (entity instanceof Player player) {
                float x = player.getInterpolatedX(alpha) - player.getWidth() / 2.0f;
                float y = player.getInterpolatedY(alpha) - player.getHeight() / 2.0f;

                shapeRenderer.setColor(COLOR_PLAYER_OUTLINE);
                shapeRenderer.rect(x, y, player.getWidth(), player.getHeight());
            }
        }
        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}

