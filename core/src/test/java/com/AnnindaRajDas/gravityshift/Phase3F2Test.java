package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive verification suite for Phase 3F.2: Viewport Overhaul.
 * Verifies:
 * 1. Minimum 20x12 world viewport dimensions.
 * 2. Wide aspect ratios (16:9, 18:9).
 * 3. Tall aspect ratios (4:3, 1:1, 9:16).
 * 4. Ultrawide and mobile layouts (20:9, 21:9).
 * 5. Viewport resize behavior and aspect ratio parity.
 * 6. Camera centering on the 20x12 minimum chamber across all aspect ratios.
 * 7. World-coordinate consistency in meters.
 * 8. Independent HUD coordinate system (1:1 pixels).
 * 9. Separation of world and HUD viewports/projections.
 */
public class Phase3F2Test {

    private static final float EPS = 0.001f;

    @BeforeAll
    public static void setUp() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        Box2D.init();

        // Configure HdpiUtils and provide dummy proxies for headless test execution
        com.badlogic.gdx.graphics.glutils.HdpiUtils.setMode(com.badlogic.gdx.graphics.glutils.HdpiMode.Pixels);

        if (Gdx.gl == null) {
            Gdx.gl = (GL20) Proxy.newProxyInstance(
                    GL20.class.getClassLoader(),
                    new Class<?>[]{GL20.class},
                    (proxy, method, args) -> null
            );
        }

        if (Gdx.graphics == null) {
            Gdx.graphics = (com.badlogic.gdx.Graphics) Proxy.newProxyInstance(
                    com.badlogic.gdx.Graphics.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Graphics.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getWidth".equals(name) || "getBackBufferWidth".equals(name)) return 960;
                        if ("getHeight".equals(name) || "getBackBufferHeight".equals(name)) return 576;
                        return null;
                    }
            );
        }
    }

    // 1. Minimum 20 x 12 world viewport
    @Test
    public void test01_MinimumWorldViewportDimensions() {
        OrthographicCamera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);

        assertEquals(20.0f, viewport.getMinWorldWidth(), EPS);
        assertEquals(12.0f, viewport.getMinWorldHeight(), EPS);

        // At exactly 5:3 aspect ratio (960x576), world dimensions should match minimum exactly
        viewport.update(960, 576, false);
        assertEquals(20.0f, viewport.getWorldWidth(), EPS);
        assertEquals(12.0f, viewport.getWorldHeight(), EPS);
    }

    // 2. Wide aspect ratios (16:9, 18:9)
    @Test
    public void test02_WideAspectRatios() {
        OrthographicCamera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);

        // 16:9 (1920x1080) -> aspect ratio 1.7778 > 1.6667 (5:3)
        // Height stays at 12m, width extends to 12 * (16/9) = 21.333m
        viewport.update(1920, 1080, false);
        assertEquals(12.0f, viewport.getWorldHeight(), EPS);
        float expectedWidth16x9 = 12.0f * (16.0f / 9.0f);
        assertEquals(expectedWidth16x9, viewport.getWorldWidth(), 0.01f);
        assertTrue(viewport.getWorldWidth() >= GameConfig.MIN_WORLD_WIDTH,
                "Visible width must fully encompass the 20m minimum chamber");

        // 18:9 (2160x1080) -> aspect ratio 2.0
        // Height stays at 12m, width extends to 12 * 2.0 = 24.0m
        viewport.update(2160, 1080, false);
        assertEquals(12.0f, viewport.getWorldHeight(), EPS);
        assertEquals(24.0f, viewport.getWorldWidth(), 0.01f);
        assertTrue(viewport.getWorldWidth() >= GameConfig.MIN_WORLD_WIDTH);
    }

    // 3. Tall aspect ratios (4:3, 1:1, 9:16)
    @Test
    public void test03_TallAspectRatios() {
        OrthographicCamera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);

        // 4:3 (1024x768) -> aspect ratio 1.3333 < 1.6667 (5:3)
        // Width stays at 20m, height extends to 20 / (4/3) = 15.0m
        viewport.update(1024, 768, false);
        assertEquals(20.0f, viewport.getWorldWidth(), EPS);
        float expectedHeight4x3 = 20.0f / (4.0f / 3.0f);
        assertEquals(expectedHeight4x3, viewport.getWorldHeight(), 0.01f);
        assertTrue(viewport.getWorldHeight() >= GameConfig.MIN_WORLD_HEIGHT,
                "Visible height must fully encompass the 12m minimum chamber");

        // 1:1 square (1000x1000) -> aspect ratio 1.0
        // Width stays at 20m, height extends to 20m
        viewport.update(1000, 1000, false);
        assertEquals(20.0f, viewport.getWorldWidth(), EPS);
        assertEquals(20.0f, viewport.getWorldHeight(), EPS);

        // 9:16 vertical portrait (1080x1920)
        viewport.update(1080, 1920, false);
        assertEquals(20.0f, viewport.getWorldWidth(), EPS);
        float expectedHeightPortrait = 20.0f * (16.0f / 9.0f);
        assertEquals(expectedHeightPortrait, viewport.getWorldHeight(), 0.01f);
    }

    // 4. 19.53:9 (realme RMX5555) and 21:9 modern mobile/ultrawide layouts
    @Test
    public void test04_ModernMobileUltrawideLayouts() {
        OrthographicCamera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);

        // 19.53:9 realme RMX5555 physical screen (2344x1080, aspect ratio ~2.1704)
        viewport.update(2344, 1080, false);
        assertEquals(12.0f, viewport.getWorldHeight(), EPS);
        float expectedWidthRealme = 12.0f * (2344.0f / 1080.0f);
        assertEquals(expectedWidthRealme, viewport.getWorldWidth(), 0.01f);
        assertTrue(viewport.getWorldWidth() >= 26.0f, "2344x1080 extends world width past 26m");

        // 21:9 desktop ultrawide (2560x1080)
        viewport.update(2560, 1080, false);
        assertEquals(12.0f, viewport.getWorldHeight(), EPS);
        float expectedWidthUltrawide = 12.0f * (2560.0f / 1080.0f);
        assertEquals(expectedWidthUltrawide, viewport.getWorldWidth(), 0.01f);
        assertTrue(viewport.getWorldWidth() >= 28.4f);
    }

    // 5. Viewport resize behavior and scaling parity
    @Test
    public void test05_ViewportResizeBehaviorAndScalingParity() {
        OrthographicCamera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);

        // Initial 960x576
        viewport.update(960, 576, false);
        assertEquals(960, viewport.getScreenWidth());
        assertEquals(576, viewport.getScreenHeight());
        float pixelsPerMeterX1 = viewport.getScreenWidth() / viewport.getWorldWidth();
        float pixelsPerMeterY1 = viewport.getScreenHeight() / viewport.getWorldHeight();
        assertEquals(pixelsPerMeterX1, pixelsPerMeterY1, EPS, "Aspect ratio must be square (no stretch/distortion)");

        // Resize to 1920x1080
        viewport.update(1920, 1080, false);
        assertEquals(1920, viewport.getScreenWidth());
        assertEquals(1080, viewport.getScreenHeight());
        float pixelsPerMeterX2 = viewport.getScreenWidth() / viewport.getWorldWidth();
        float pixelsPerMeterY2 = viewport.getScreenHeight() / viewport.getWorldHeight();
        assertEquals(pixelsPerMeterX2, pixelsPerMeterY2, EPS, "Aspect ratio must remain 1:1 on resize");

        // Resize to 800x600
        viewport.update(800, 600, false);
        assertEquals(800, viewport.getScreenWidth());
        assertEquals(600, viewport.getScreenHeight());
        float pixelsPerMeterX3 = viewport.getScreenWidth() / viewport.getWorldWidth();
        float pixelsPerMeterY3 = viewport.getScreenHeight() / viewport.getWorldHeight();
        assertEquals(pixelsPerMeterX3, pixelsPerMeterY3, EPS);
    }

    // 6. Camera centering on the 20x12 chamber across all aspect ratios
    @Test
    public void test06_CameraCenteringOnChamber() {
        OrthographicCamera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);

        // Simulation of BaseScreen.resize() centering logic
        int[] widths = { 960, 1920, 2344, 1024, 800 };
        int[] heights = { 576, 1080, 1080, 768, 600 };

        for (int i = 0; i < widths.length; i++) {
            viewport.update(widths[i], heights[i], false);
            camera.position.set(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f, 0f);
            camera.update();

            // Camera center must strictly be at chamber center (10m, 6m)
            assertEquals(10.0f, camera.position.x, EPS);
            assertEquals(6.0f, camera.position.y, EPS);

            // Compute visible world bounds
            float halfVisibleWidth = viewport.getWorldWidth() / 2.0f;
            float halfVisibleHeight = viewport.getWorldHeight() / 2.0f;
            float leftBound = camera.position.x - halfVisibleWidth;
            float rightBound = camera.position.x + halfVisibleWidth;
            float bottomBound = camera.position.y - halfVisibleHeight;
            float topBound = camera.position.y + halfVisibleHeight;

            // Ensure the entire [0, 20] x [0, 12] chamber is inside the visible bounds
            assertTrue(leftBound <= 0.0f, "Left chamber border (x=0) must be visible");
            assertTrue(rightBound >= 20.0f, "Right chamber border (x=20) must be visible");
            assertTrue(bottomBound <= 0.0f, "Bottom chamber border (y=0) must be visible");
            assertTrue(topBound >= 12.0f, "Top chamber border (y=12) must be visible");

            // Verify symmetric padding around chamber
            float leftPadding = 0.0f - leftBound;
            float rightPadding = rightBound - 20.0f;
            assertEquals(leftPadding, rightPadding, 0.01f, "Extra horizontal space must be symmetrically padded");

            float bottomPadding = 0.0f - bottomBound;
            float topPadding = topBound - 12.0f;
            assertEquals(bottomPadding, topPadding, 0.01f, "Extra vertical space must be symmetrically padded");
        }
    }

    // 7. World-coordinate consistency in meters
    @Test
    public void test07_WorldCoordinateConsistency() {
        OrthographicCamera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, camera);

        viewport.update(2344, 1080, false);
        camera.position.set(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f, 0f);
        camera.update();

        // Project two points 5 meters apart using viewport projection
        Vector3 p1 = new Vector3(2.0f, 4.0f, 0f);
        Vector3 p2 = new Vector3(7.0f, 4.0f, 0f);
        Vector3 screenP1 = viewport.project(p1.cpy());
        Vector3 screenP2 = viewport.project(p2.cpy());

        // Check distance in pixels
        float pixelDistance = screenP2.x - screenP1.x;
        float pixelsPerMeter = (float) viewport.getScreenWidth() / viewport.getWorldWidth();
        assertEquals(5.0f * pixelsPerMeter, pixelDistance, 0.05f);

        // Unproject back to world coordinates
        Vector3 unprojectedP1 = viewport.unproject(screenP1.cpy());
        Vector3 unprojectedP2 = viewport.unproject(screenP2.cpy());
        assertEquals(2.0f, unprojectedP1.x, EPS);
        assertEquals(7.0f, unprojectedP2.x, EPS);
        assertEquals(5.0f, unprojectedP2.x - unprojectedP1.x, EPS);
    }

    // 8. Independent HUD coordinate system (1:1 screen pixels)
    @Test
    public void test08_IndependentHudCoordinateSystem() {
        OrthographicCamera hudCamera = new OrthographicCamera();
        ScreenViewport hudViewport = new ScreenViewport(hudCamera);

        // Update to 2344x1080
        hudViewport.update(2344, 1080, true);
        assertEquals(2344.0f, hudViewport.getWorldWidth(), EPS, "HUD world width must equal screen pixels");
        assertEquals(1080.0f, hudViewport.getWorldHeight(), EPS, "HUD world height must equal screen pixels");
        assertEquals(2344.0f / 2f, hudCamera.position.x, EPS);
        assertEquals(1080.0f / 2f, hudCamera.position.y, EPS);

        // Update to 1280x720
        hudViewport.update(1280, 720, true);
        assertEquals(1280.0f, hudViewport.getWorldWidth(), EPS);
        assertEquals(720.0f, hudViewport.getWorldHeight(), EPS);
        assertEquals(1280.0f / 2f, hudCamera.position.x, EPS);
        assertEquals(720.0f / 2f, hudCamera.position.y, EPS);
    }

    // 9. World and HUD viewports separation
    @Test
    public void test09_WorldAndHudViewportsSeparation() {
        OrthographicCamera worldCamera = new OrthographicCamera();
        ExtendViewport worldViewport = new ExtendViewport(GameConfig.MIN_WORLD_WIDTH, GameConfig.MIN_WORLD_HEIGHT, worldCamera);

        OrthographicCamera hudCamera = new OrthographicCamera();
        ScreenViewport hudViewport = new ScreenViewport(hudCamera);

        int screenW = 2344;
        int screenH = 1080;

        worldViewport.update(screenW, screenH, false);
        worldCamera.position.set(GameConfig.WORLD_WIDTH / 2f, GameConfig.WORLD_HEIGHT / 2f, 0f);
        worldCamera.update();

        hudViewport.update(screenW, screenH, true);
        hudCamera.update();

        // 1. Viewport world dimensions must be strictly distinct
        assertTrue(worldViewport.getWorldWidth() < 30.0f, "World viewport width must be in meters (~26m)");
        assertTrue(hudViewport.getWorldWidth() > 1000.0f, "HUD viewport width must be in pixels (2344px)");

        // 2. Camera viewport dimensions must be strictly distinct
        assertEquals(worldViewport.getWorldWidth(), worldCamera.viewportWidth, EPS);
        assertEquals(hudViewport.getWorldWidth(), hudCamera.viewportWidth, EPS);

        // 3. Projections are independent
        assertNotEquals(worldCamera.combined, hudCamera.combined, "Projection matrices must be independent");
    }
}
