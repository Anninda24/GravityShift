package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.physics.GameContactListener;
import com.AnnindaRajDas.gravityshift.physics.GravityController;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for Phase 3F.3:
 * Chamfered Player Shape & Friction Tuning.
 *
 * Verifies:
 * 1. Player physical fixture is an 8-vertex chamfered polygon, not a simple rectangle.
 * 2. Bounding width remains 0.8m.
 * 3. Bounding height remains 1.4m.
 * 4. Corner chamfer geometry is approximately 0.1m.
 * 5. Polygon vertex count and convexity are strictly valid for Box2D.
 * 6. Fixed rotation remains enabled.
 * 7. Mass density remains 1.2 kg/m^2.
 * 8. Friction matches the selected tuned value (0.2).
 * 9. Support sensors still function reliably in all four cardinal directions.
 * 10. Player settles stably on horizontal platform at y ≈ 2.015m (surface y = 1.3m).
 * 11. Player establishes support against vertical surfaces under horizontal gravity.
 * 12. Existing spawn, movement, and lifecycle invariants remain preserved.
 */
public class Phase3F3Test {

    private static final float EPS = 0.001f;

    @BeforeAll
    public static void setUp() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        Box2D.init();
    }

    private Fixture getPlayerBodyFixture(Player player) {
        for (Fixture f : player.getBody().getFixtureList()) {
            if (f.getUserData() == FixtureType.PLAYER_BODY) {
                return f;
            }
        }
        return null;
    }

    // 1. Player physical fixture is a polygon, not a rectangle
    @Test
    public void test01_PlayerPhysicalFixtureIsPolygonNotRectangle() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        Fixture bodyFixture = getPlayerBodyFixture(player);
        assertNotNull(bodyFixture, "Player must possess a solid PLAYER_BODY fixture");
        assertTrue(bodyFixture.getShape() instanceof PolygonShape, "Fixture shape must be a PolygonShape");
        PolygonShape polygon = (PolygonShape) bodyFixture.getShape();

        // A rectangle has 4 vertices; a chamfered polygon has 8 vertices
        assertEquals(8, polygon.getVertexCount(), "Torso fixture must be an 8-vertex chamfered polygon, not a 4-vertex rectangle");

        world.dispose();
    }

    // 2. Bounding width remains 0.8m
    @Test
    public void test02_BoundingWidthRemainsPointEightMeters() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        Fixture bodyFixture = getPlayerBodyFixture(player);
        assertNotNull(bodyFixture);
        PolygonShape polygon = (PolygonShape) bodyFixture.getShape();

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        Vector2 vertex = new Vector2();
        for (int i = 0; i < polygon.getVertexCount(); i++) {
            polygon.getVertex(i, vertex);
            if (vertex.x < minX) minX = vertex.x;
            if (vertex.x > maxX) maxX = vertex.x;
        }

        float boundingWidth = maxX - minX;
        assertEquals(Player.DEFAULT_WIDTH, boundingWidth, EPS, "Bounding width must strictly be 0.8m");
        assertEquals(0.8f, player.getWidth(), EPS);

        world.dispose();
    }

    // 3. Bounding height remains 1.4m
    @Test
    public void test03_BoundingHeightRemainsOnePointFourMeters() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        Fixture bodyFixture = getPlayerBodyFixture(player);
        assertNotNull(bodyFixture);
        PolygonShape polygon = (PolygonShape) bodyFixture.getShape();

        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        Vector2 vertex = new Vector2();
        for (int i = 0; i < polygon.getVertexCount(); i++) {
            polygon.getVertex(i, vertex);
            if (vertex.y < minY) minY = vertex.y;
            if (vertex.y > maxY) maxY = vertex.y;
        }

        float boundingHeight = maxY - minY;
        assertEquals(Player.DEFAULT_HEIGHT, boundingHeight, EPS, "Bounding height must strictly be 1.4m");
        assertEquals(1.4f, player.getHeight(), EPS);

        world.dispose();
    }

    // 4. Chamfer geometry is approximately 0.1m
    @Test
    public void test04_ChamferGeometryIsApproximatelyPointOneMeters() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        Fixture bodyFixture = getPlayerBodyFixture(player);
        assertNotNull(bodyFixture);
        PolygonShape polygon = (PolygonShape) bodyFixture.getShape();

        // Expected half dimensions
        float halfW = Player.DEFAULT_WIDTH / 2.0f;  // 0.4m
        float halfH = Player.DEFAULT_HEIGHT / 2.0f; // 0.7m
        float expectedChamfer = Player.CHAMFER_RADIUS; // 0.1m

        // Find top-right horizontal vertex (x = halfW - chamfer, y = halfH)
        // and top-right vertical vertex (x = halfW, y = halfH - chamfer)
        Vector2 vertex = new Vector2();
        boolean foundTopHorizontal = false;
        boolean foundTopVertical = false;

        for (int i = 0; i < polygon.getVertexCount(); i++) {
            polygon.getVertex(i, vertex);
            if (Math.abs(vertex.x - (halfW - expectedChamfer)) < EPS && Math.abs(vertex.y - halfH) < EPS) {
                foundTopHorizontal = true;
            }
            if (Math.abs(vertex.x - halfW) < EPS && Math.abs(vertex.y - (halfH - expectedChamfer)) < EPS) {
                foundTopVertical = true;
            }
        }

        assertTrue(foundTopHorizontal, "Polygon must contain chamfer vertex at (halfW - 0.1, halfH)");
        assertTrue(foundTopVertical, "Polygon must contain chamfer vertex at (halfW, halfH - 0.1)");

        world.dispose();
    }

    // 5. Polygon vertex count and geometry are valid (convex, CCW order)
    @Test
    public void test05_PolygonVertexCountAndGeometryValidity() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        Fixture bodyFixture = getPlayerBodyFixture(player);
        assertNotNull(bodyFixture);
        PolygonShape polygon = (PolygonShape) bodyFixture.getShape();

        int n = polygon.getVertexCount();
        assertEquals(8, n, "Box2D maximum polygon vertex limit is 8");

        Vector2 v0 = new Vector2();
        Vector2 v1 = new Vector2();
        Vector2 v2 = new Vector2();

        // Verify strictly convex counter-clockwise winding
        for (int i = 0; i < n; i++) {
            polygon.getVertex(i, v0);
            polygon.getVertex((i + 1) % n, v1);
            polygon.getVertex((i + 2) % n, v2);

            float edge1X = v1.x - v0.x;
            float edge1Y = v1.y - v0.y;
            float edge2X = v2.x - v1.x;
            float edge2Y = v2.y - v1.y;

            // 2D cross product: e1 x e2 > 0 for strictly counter-clockwise turns
            float crossProduct = (edge1X * edge2Y) - (edge1Y * edge2X);
            assertTrue(crossProduct > 0.0f, "Polygon must be strictly convex and counter-clockwise at vertex " + i);
        }

        world.dispose();
    }

    // 6. Fixed rotation remains enabled
    @Test
    public void test06_FixedRotationRemainsEnabled() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        assertTrue(player.getBody().isFixedRotation(), "Player body must strictly have fixed rotation enabled");

        world.dispose();
    }

    // 7. Density remains 1.2
    @Test
    public void test07_DensityRemainsOnePointTwo() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        Fixture bodyFixture = getPlayerBodyFixture(player);
        assertNotNull(bodyFixture);
        assertEquals(Player.DENSITY, bodyFixture.getDensity(), EPS, "Player body density must be 1.2 kg/m^2");
        assertEquals(1.2f, bodyFixture.getDensity(), EPS);

        world.dispose();
    }

    // 8. Friction matches the selected tuned value (0.2)
    @Test
    public void test08_FrictionMatchesTunedValue() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        Fixture bodyFixture = getPlayerBodyFixture(player);
        assertNotNull(bodyFixture);
        assertEquals(Player.FRICTION, bodyFixture.getFriction(), EPS, "Player body friction must match tuned constant");
        assertEquals(0.2f, bodyFixture.getFriction(), EPS, "Player friction calibrated to 0.2 to prevent wall-sticking");

        world.dispose();
    }

    // 9. Support sensors still work in all four directions
    @Test
    public void test09_SupportSensorsWorkInAllFourDirections() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 3.0f, 5.0f);

        // Verify sensor fixture existence
        int sensorCount = 0;
        for (Fixture f : player.getBody().getFixtureList()) {
            if (f.isSensor()) {
                sensorCount++;
            }
        }
        assertEquals(4, sensorCount, "Player must possess exactly four directional sensors");

        // Verify contact callbacks for all four directions
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertEquals(1, player.getContactCount(Direction.DOWN));
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertEquals(0, player.getContactCount(Direction.DOWN));

        player.onSensorContactBegin(FixtureType.PLAYER_TOP_SENSOR);
        assertEquals(1, player.getContactCount(Direction.UP));
        player.onSensorContactEnd(FixtureType.PLAYER_TOP_SENSOR);
        assertEquals(0, player.getContactCount(Direction.UP));

        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);
        assertEquals(1, player.getContactCount(Direction.LEFT));
        player.onSensorContactEnd(FixtureType.PLAYER_LEFT_SENSOR);
        assertEquals(0, player.getContactCount(Direction.LEFT));

        player.onSensorContactBegin(FixtureType.PLAYER_RIGHT_SENSOR);
        assertEquals(1, player.getContactCount(Direction.RIGHT));
        player.onSensorContactEnd(FixtureType.PLAYER_RIGHT_SENSOR);
        assertEquals(0, player.getContactCount(Direction.RIGHT));

        world.dispose();
    }

    // 10. Player can settle on a horizontal platform at y ≈ 2.015m
    @Test
    public void test10_PlayerSettlesOnHorizontalPlatform() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.DOWN);

        // Ground platform: y = 0.5m, h = 0.8m -> surface y = 1.3m
        bf.createStaticBox(1.0f, 0.5f, 18.0f, 0.8f, FixtureType.GROUND);

        Player player = Player.create(bf, 3.0f, 5.0f);
        player.onGravityDirectionChanged(Direction.DOWN);

        // Step world until player lands on platform
        for (int i = 0; i < 120; i++) {
            world.step(GameConfig.TIME_STEP, 8, 3);
            if (player.isGrounded()) break;
        }

        assertTrue(player.isGrounded(), "Player must be grounded on horizontal platform");
        assertEquals(Direction.DOWN, player.getSupportDirection());
        assertTrue(player.getContactCount(Direction.DOWN) >= 1);

        // Invariant: surface at 1.3m + half-height 0.7m + Box2D skin collision separation (~0.015m) ≈ 2.015m
        assertEquals(2.015f, player.getBody().getPosition().y, 0.05f, "Player center must settle near 2.015m");

        world.dispose();
    }

    // 11. Player can establish support against vertical surfaces
    @Test
    public void test11_PlayerEstablishesSupportAgainstVerticalSurfaces() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.LEFT);

        // Left vertical wall: x = 0, y = 0, w = 0.5m, h = 12m (surface at x = 0.5m)
        bf.createStaticBox(0.0f, 0.0f, 0.5f, GameConfig.WORLD_HEIGHT, FixtureType.WALL);

        // Spawn player against left wall
        Player player = Player.create(bf, 0.1f, 5.0f);
        player.onGravityDirectionChanged(Direction.LEFT);

        world.step(GameConfig.TIME_STEP, 8, 3);

        assertTrue(player.getContactCount(Direction.LEFT) >= 1, "Player left sensor must contact left wall");
        assertTrue(player.isGrounded(), "Player must be grounded against vertical wall under LEFT gravity");
        assertEquals(Direction.LEFT, player.getSupportDirection());

        // Tangential movement along vertical surface
        player.setMoveDirection(1.0f);
        for (int i = 0; i < 10; i++) player.onPrePhysicsStep(GameConfig.TIME_STEP);
        assertEquals(5.0f, player.getBody().getLinearVelocity().y, EPS);

        // Direction-independent jump away from left wall (launches in +X direction)
        assertTrue(player.jump());
        assertTrue(player.getBody().getLinearVelocity().x > 6.0f, "Jump must launch away from left wall");

        world.dispose();
    }

    // 12. Existing player spawn and lifecycle behavior remain correct
    @Test
    public void test12_ExistingPlayerSpawnAndLifecycleInvariants() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 3.0f);

        assertFalse(player.isDisposed());
        assertEquals(Direction.DOWN, player.getSupportDirection());
        assertEquals(0.0f, player.getMoveDirection(), EPS);
        assertFalse(player.isJumpConsumed());

        // Movement clamping
        player.setMoveDirection(3.5f);
        assertEquals(1.0f, player.getMoveDirection(), EPS);
        player.setMoveDirection(-2.0f);
        assertEquals(-1.0f, player.getMoveDirection(), EPS);

        // Lifecycle disposal
        player.dispose();
        assertTrue(player.isDisposed());
        assertFalse(player.jump(), "Disposed player must reject jump");
        assertEquals(0, player.getContactCount(Direction.DOWN));

        world.dispose();
    }
}
