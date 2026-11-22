package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.Entity;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.physics.GameContactListener;
import com.AnnindaRajDas.gravityshift.physics.GravityController;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.AnnindaRajDas.gravityshift.physics.PhysicsStepListener;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive verification test suite for Phase 3F.1:
 * - 12 Dedicated Phase 3F.1 Tests (Timestep Clamping & Render Interpolation)
 * - 4 Four-Way Cardinal Gravity Tests (DOWN, UP, LEFT, RIGHT)
 * - 10 Integrated Scenario Tests (T1 - T10)
 * - 6 Sub-Phase Regression Tests (3C - 3E.4)
 *
 * Total: 32 individual automated unit tests.
 */
public class Phase3F1Test {

    private static final float EPS = 0.001f;

    @BeforeAll
    public static void setUp() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        Box2D.init();
    }

    // Concrete mock entity for testing transform snapshots
    private static class TestEntity extends Entity {
        public TestEntity(com.badlogic.gdx.physics.box2d.Body body) {
            super(body);
        }

        @Override
        public void update(float delta) {}
    }

    // =========================================================================
    // SECTION 1: PHASE 3F.1 DEDICATED TESTS (1 - 12)
    // =========================================================================

    @Test
    public void test01_FrameDeltaBelowMaximum() {
        PhysicsController pc = new PhysicsController();
        float subDelta = 0.008f; // ~120Hz frame delta (< MAX_FRAME_DELTA 0.1f)
        pc.update(subDelta);

        // Sub-timestep delta should accumulate without triggering a full 1/60s step
        assertEquals(subDelta, pc.getAccumulatorRemainder(), EPS);
        float expectedAlpha = subDelta / GameConfig.TIME_STEP;
        assertEquals(expectedAlpha, pc.getInterpolationAlpha(), EPS);
        pc.dispose();
    }

    @Test
    public void test02_FrameDeltaAboveMaximum() {
        PhysicsController pc = new PhysicsController();
        int[] stepsExecuted = new int[1];
        pc.setStepListener(new PhysicsStepListener() {
            @Override public void onPreStep() {}
            @Override public void onPostStep() { stepsExecuted[0]++; }
        });

        // Massive frame spike (0.5s) must be clamped to MAX_FRAME_DELTA (0.1s)
        pc.update(0.5f);
        assertTrue(stepsExecuted[0] <= GameConfig.MAX_PHYSICS_STEPS_PER_FRAME,
                "Executed steps (" + stepsExecuted[0] + ") must not exceed " + GameConfig.MAX_PHYSICS_STEPS_PER_FRAME);
        assertTrue(pc.getInterpolationAlpha() >= 0.0f && pc.getInterpolationAlpha() <= 1.0f);
        pc.dispose();
    }

    @Test
    public void test03_NegativeAndNonFiniteFrameDeltaHandling() {
        PhysicsController pc = new PhysicsController();
        int[] stepsExecuted = new int[1];
        pc.setStepListener(new PhysicsStepListener() {
            @Override public void onPreStep() {}
            @Override public void onPostStep() { stepsExecuted[0]++; }
        });

        pc.update(-0.016f);
        assertEquals(0.0f, pc.getAccumulatorRemainder(), EPS);
        assertEquals(0, stepsExecuted[0]);

        pc.update(Float.NaN);
        assertEquals(0.0f, pc.getAccumulatorRemainder(), EPS);
        assertEquals(0, stepsExecuted[0]);

        pc.update(Float.POSITIVE_INFINITY);
        assertEquals(0.0f, pc.getAccumulatorRemainder(), EPS);
        assertEquals(0, stepsExecuted[0]);

        pc.update(Float.NEGATIVE_INFINITY);
        assertEquals(0.0f, pc.getAccumulatorRemainder(), EPS);
        assertEquals(0, stepsExecuted[0]);
        pc.dispose();
    }

    @Test
    public void test04_MaximumOfSixPhysicsStepsPerFrame() {
        PhysicsController pc = new PhysicsController();
        int[] stepsExecuted = new int[1];
        pc.setStepListener(new PhysicsStepListener() {
            @Override public void onPreStep() {}
            @Override public void onPostStep() { stepsExecuted[0]++; }
        });

        // 2.0s spike
        pc.update(2.0f);
        assertEquals(6, stepsExecuted[0], "Hard cap of 6 steps per frame must be strictly enforced");
        assertTrue(pc.getAccumulatorRemainder() < GameConfig.TIME_STEP, "Excess accumulator must be discarded to prevent death spiral");
        pc.dispose();
    }

    @Test
    public void test05_AccumulatorRemainderAndAlphaCalculation() {
        PhysicsController pc = new PhysicsController();
        // 0.025s = 1 full step (0.0166667s) + remainder (0.0083333s, half a step)
        pc.update(0.025f);
        float expectedRemainder = 0.025f - GameConfig.TIME_STEP;
        assertEquals(expectedRemainder, pc.getAccumulatorRemainder(), EPS);
        float expectedAlpha = expectedRemainder / GameConfig.TIME_STEP;
        assertEquals(0.5f, expectedAlpha, 0.01f);
        assertEquals(expectedAlpha, pc.getInterpolationAlpha(), 0.01f);
        pc.dispose();
    }

    @Test
    public void test06_AlphaRemainsWithinZeroToOne() {
        PhysicsController pc = new PhysicsController();
        float[] testDeltas = { 0.0f, 0.001f, 0.010f, 0.0166667f, 0.020f, 0.0333333f, 0.099f, 0.1f, 0.25f, 1.0f };
        for (float delta : testDeltas) {
            pc.update(delta);
            float alpha = pc.getInterpolationAlpha();
            assertTrue(alpha >= 0.0f && alpha <= 1.0f, "Alpha (" + alpha + ") outside [0, 1] for delta " + delta);
        }
        pc.dispose();
    }

    @Test
    public void test07_CorrectMidpointPositionInterpolation() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        com.badlogic.gdx.physics.box2d.Body body = bf.createDynamicBox(0f, 0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f);
        TestEntity entity = new TestEntity(body);

        body.setTransform(2.0f, 4.0f, 0f);
        entity.capturePreStepTransform();
        body.setTransform(6.0f, 10.0f, 0f);
        entity.capturePostStepTransform();

        Vector2 midPos = entity.getInterpolatedPosition(0.5f);
        assertEquals(4.0f, midPos.x, EPS);
        assertEquals(7.0f, midPos.y, EPS);
        assertEquals(4.0f, entity.getInterpolatedX(0.5f), EPS);
        assertEquals(7.0f, entity.getInterpolatedY(0.5f), EPS);

        world.dispose();
    }

    @Test
    public void test08_CorrectInterpolationAtAlphaZeroAndOne() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        com.badlogic.gdx.physics.box2d.Body body = bf.createDynamicBox(0f, 0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f);
        TestEntity entity = new TestEntity(body);

        body.setTransform(2.0f, 4.0f, 0f);
        entity.capturePreStepTransform();
        body.setTransform(6.0f, 10.0f, 0f);
        entity.capturePostStepTransform();

        Vector2 atZero = entity.getInterpolatedPosition(0.0f);
        assertEquals(2.0f, atZero.x, EPS);
        assertEquals(4.0f, atZero.y, EPS);

        Vector2 atOne = entity.getInterpolatedPosition(1.0f);
        assertEquals(6.0f, atOne.x, EPS);
        assertEquals(10.0f, atOne.y, EPS);

        world.dispose();
    }

    @Test
    public void test09_AngleInterpolationAcrossWrapBoundary() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        com.badlogic.gdx.physics.box2d.Body body = bf.createDynamicBox(0f, 0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f);
        TestEntity entity = new TestEntity(body);

        float prevAngle = (float) (Math.PI - 0.1);
        float currAngle = (float) (-Math.PI + 0.1);

        body.setTransform(0f, 0f, prevAngle);
        entity.capturePreStepTransform();
        body.setTransform(0f, 0f, currAngle);
        entity.capturePostStepTransform();

        float midAngle = entity.getInterpolatedAngle(0.5f);
        assertTrue(Math.abs(Math.abs(midAngle) - Math.PI) < 0.01f,
                "Expected angle near PI, got " + midAngle);

        world.dispose();
    }

    @Test
    public void test10_NewlyCreatedEntitySnapshotInitialization() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        com.badlogic.gdx.physics.box2d.Body body = bf.createDynamicBox(3.0f, 5.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f);
        TestEntity entity = new TestEntity(body);

        Vector2 prev = entity.getPreviousPosition();
        Vector2 curr = entity.getCurrentPosition();
        Vector2 interp = entity.getInterpolatedPosition(0.5f);

        assertEquals(body.getPosition().x, prev.x, EPS);
        assertEquals(body.getPosition().y, prev.y, EPS);
        assertEquals(body.getPosition().x, curr.x, EPS);
        assertEquals(body.getPosition().y, curr.y, EPS);
        assertEquals(body.getPosition().x, interp.x, EPS);
        assertEquals(body.getPosition().y, interp.y, EPS);

        world.dispose();
    }

    @Test
    public void test11_TeleportSynchronizationWithoutInterpolationStreaks() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        com.badlogic.gdx.physics.box2d.Body body = bf.createDynamicBox(3.0f, 5.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f);
        TestEntity entity = new TestEntity(body);

        entity.capturePreStepTransform();
        body.setTransform(15.0f, 8.0f, 0f);
        entity.syncTransform();

        Vector2 interpMid = entity.getInterpolatedPosition(0.5f);
        assertEquals(15.0f, interpMid.x, EPS);
        assertEquals(8.0f, interpMid.y, EPS);

        world.dispose();
    }

    @Test
    public void test12_InterpolationDoesNotMutateBox2DBodyTransform() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        com.badlogic.gdx.physics.box2d.Body body = bf.createDynamicBox(5.0f, 2.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f);
        TestEntity entity = new TestEntity(body);

        entity.capturePreStepTransform();
        body.setTransform(7.0f, 4.0f, 0.5f);
        entity.capturePostStepTransform();

        Vector2 interp = entity.getInterpolatedPosition(0.3f);
        float interpAngle = entity.getInterpolatedAngle(0.7f);

        // Box2D body must remain untouched at its latest physics transform
        assertEquals(7.0f, body.getPosition().x, EPS);
        assertEquals(4.0f, body.getPosition().y, EPS);
        assertEquals(0.5f, body.getAngle(), EPS);

        world.dispose();
    }

    // =========================================================================
    // SECTION 2: FOUR-WAY CARDINAL GRAVITY VERIFICATION (13 - 16)
    // =========================================================================

    @Test
    public void test13_CardinalDownGravity() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);

        bf.createStaticBox(0f, 0f, 20f, 1f, FixtureType.GROUND);
        gc.setGravityDirection(Direction.DOWN);
        Player player = Player.create(bf, 5.0f, 1.0f);
        world.step(1f / 60f, 8, 3);
        player.onGravityDirectionChanged(Direction.DOWN);

        player.getBody().setLinearVelocity(2f, -3f);
        player.setMoveDirection(1.0f);
        player.update(1f / 60f);

        assertEquals(5.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(-3.0f, player.getBody().getLinearVelocity().y, EPS);
        assertTrue(player.jump());
        assertEquals(6.5f, player.getBody().getLinearVelocity().y, EPS);
        assertEquals(Direction.DOWN, gc.getGravityDirection());
        assertEquals(new Vector2(0f, -9.81f), world.getGravity());
        assertEquals(Direction.DOWN, player.getSupportDirection());
        assertTrue(player.isJumpConsumed());

        world.dispose();
    }

    @Test
    public void test14_CardinalUpGravity() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);

        bf.createStaticBox(0f, 11f, 20f, 1f, FixtureType.CEILING);
        gc.setGravityDirection(Direction.UP);
        Player player = Player.create(bf, 5.0f, 9.6f);
        world.step(1f / 60f, 8, 3);
        player.onGravityDirectionChanged(Direction.UP);

        player.getBody().setLinearVelocity(2f, 3f);
        player.setMoveDirection(-1.0f);
        player.update(1f / 60f);

        assertEquals(-5.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(3.0f, player.getBody().getLinearVelocity().y, EPS);
        assertTrue(player.jump());
        assertEquals(-6.5f, player.getBody().getLinearVelocity().y, EPS);
        assertEquals(Direction.UP, gc.getGravityDirection());
        assertEquals(new Vector2(0f, 9.81f), world.getGravity());
        assertEquals(Direction.UP, player.getSupportDirection());
        assertTrue(player.isJumpConsumed());

        world.dispose();
    }

    @Test
    public void test15_CardinalLeftGravity() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);

        bf.createStaticBox(0f, 1f, 1f, 10f, FixtureType.WALL);
        gc.setGravityDirection(Direction.LEFT);
        Player player = Player.create(bf, 1.0f, 5.0f);
        world.step(1f / 60f, 8, 3);
        player.onGravityDirectionChanged(Direction.LEFT);

        player.getBody().setLinearVelocity(-4f, 2f);
        player.setMoveDirection(1.0f);
        player.update(1f / 60f);

        assertEquals(5.0f, player.getBody().getLinearVelocity().y, EPS);
        assertEquals(-4.0f, player.getBody().getLinearVelocity().x, EPS);
        assertTrue(player.jump());
        assertEquals(6.5f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(Direction.LEFT, gc.getGravityDirection());
        assertEquals(new Vector2(-9.81f, 0f), world.getGravity());
        assertEquals(Direction.LEFT, player.getSupportDirection());
        assertTrue(player.isJumpConsumed());

        world.dispose();
    }

    @Test
    public void test16_CardinalRightGravity() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);

        bf.createStaticBox(19f, 1f, 1f, 10f, FixtureType.WALL);
        gc.setGravityDirection(Direction.RIGHT);
        Player player = Player.create(bf, 18.2f, 5.0f);
        world.step(1f / 60f, 8, 3);
        player.onGravityDirectionChanged(Direction.RIGHT);

        player.getBody().setLinearVelocity(4f, 2f);
        player.setMoveDirection(-1.0f);
        player.update(1f / 60f);

        assertEquals(-5.0f, player.getBody().getLinearVelocity().y, EPS);
        assertEquals(4.0f, player.getBody().getLinearVelocity().x, EPS);
        assertTrue(player.jump());
        assertEquals(-6.5f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(Direction.RIGHT, gc.getGravityDirection());
        assertEquals(new Vector2(9.81f, 0f), world.getGravity());
        assertEquals(Direction.RIGHT, player.getSupportDirection());
        assertTrue(player.isJumpConsumed());

        world.dispose();
    }

    // =========================================================================
    // SECTION 3: INTEGRATED SCENARIOS (17 - 26, T1 - T10)
    // =========================================================================

    @Test
    public void test17_ScenarioT1_CompleteDownGameplayCycle() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.DOWN);

        // Platform: x=1, y=0.5, w=18, h=0.8 -> surface at y = 1.3m
        bf.createStaticBox(1.0f, 0.5f, 18.0f, 0.8f, FixtureType.GROUND);
        bf.createStaticBox(0.0f, 0.0f, 0.5f, GameConfig.WORLD_HEIGHT, FixtureType.WALL);
        bf.createStaticBox(GameConfig.WORLD_WIDTH - 0.5f, 0.0f, 0.5f, GameConfig.WORLD_HEIGHT, FixtureType.WALL);

        Player player = Player.create(bf, 3.0f, 5.0f);
        player.onGravityDirectionChanged(Direction.DOWN);

        // Simulate falling onto platform
        for (int i = 0; i < 120; i++) {
            world.step(GameConfig.TIME_STEP, 8, 3);
            if (player.isGrounded()) break;
        }

        assertTrue(player.isGrounded(), "Player must be grounded after falling onto platform");
        // Verify resting center of mass coordinate (surface 1.3m + halfHeight 0.7m + skin ~0.015m ≈ 2.015m)
        assertEquals(2.015f, player.getBody().getPosition().y, 0.05f);

        // Horizontal movement
        player.setMoveDirection(1.0f);
        player.update(GameConfig.TIME_STEP);
        assertEquals(5.0f, player.getBody().getLinearVelocity().x, EPS);

        player.setMoveDirection(-1.0f);
        player.update(GameConfig.TIME_STEP);
        assertEquals(-5.0f, player.getBody().getLinearVelocity().x, EPS);

        // Jump
        assertTrue(player.jump(), "Jump should succeed when grounded");
        assertTrue(player.isJumpConsumed(), "Jump latch must be consumed");
        assertEquals(6.5f, player.getBody().getLinearVelocity().y, EPS);

        // Fall and land again
        for (int i = 0; i < 120; i++) {
            world.step(GameConfig.TIME_STEP, 8, 3);
            if (player.isGrounded() && !player.isJumpConsumed()) break;
        }
        assertTrue(player.isGrounded(), "Player must be grounded upon landing");
        assertFalse(player.isJumpConsumed(), "Jump latch must reset upon fresh landing");

        world.dispose();
    }

    @Test
    public void test18_ScenarioT2_DownToUpTransition() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.DOWN);

        bf.createStaticBox(1.0f, 0.5f, 18.0f, 0.8f, FixtureType.GROUND);
        bf.createStaticBox(0.0f, GameConfig.WORLD_HEIGHT - 0.5f, GameConfig.WORLD_WIDTH, 0.5f, FixtureType.CEILING);

        Player player = Player.create(bf, 3.0f, 1.3f);
        player.onGravityDirectionChanged(Direction.DOWN);
        world.step(GameConfig.TIME_STEP, 8, 3);

        // Transition to UP
        gc.setGravityDirection(Direction.UP);
        player.onGravityDirectionChanged(Direction.UP);
        assertEquals(Direction.UP, gc.getGravityDirection());
        assertEquals(Direction.UP, player.getSupportDirection());

        // Rise to ceiling
        for (int i = 0; i < 120; i++) {
            world.step(GameConfig.TIME_STEP, 8, 3);
            if (player.isGrounded()) break;
        }
        assertTrue(player.isGrounded(), "Player must reach ceiling and become grounded under UP gravity");

        // Horizontal movement
        player.setMoveDirection(1.0f);
        player.update(GameConfig.TIME_STEP);
        assertEquals(5.0f, player.getBody().getLinearVelocity().x, EPS);

        // Jump downward
        assertTrue(player.jump(), "Jump should succeed on ceiling");
        assertTrue(player.getBody().getLinearVelocity().y < -6.0f, "Jump away from ceiling must be negative Y");

        world.dispose();
    }

    @Test
    public void test19_ScenarioT3_DownToLeftCornerContact() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.DOWN);

        bf.createStaticBox(0.0f, 0.0f, 0.5f, GameConfig.WORLD_HEIGHT, FixtureType.WALL);

        Player player = Player.create(bf, 0.1f, 5.0f);
        world.step(GameConfig.TIME_STEP, 8, 3);
        assertTrue(player.getContactCount(Direction.LEFT) >= 1, "Left sensor must contact left wall");

        // Transition to LEFT
        gc.setGravityDirection(Direction.LEFT);
        player.onGravityDirectionChanged(Direction.LEFT);
        assertTrue(player.isGrounded(), "Immediate support must be recognized upon switching to contacting wall");

        // Vertical tangential movement
        player.setMoveDirection(1.0f);
        player.update(GameConfig.TIME_STEP);
        assertEquals(5.0f, player.getBody().getLinearVelocity().y, EPS);

        // Jump away from left wall (rightward, positive X)
        assertTrue(player.jump());
        assertTrue(player.getBody().getLinearVelocity().x > 6.0f);

        world.dispose();
    }

    @Test
    public void test20_ScenarioT4_DownToRightCornerContact() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.DOWN);

        bf.createStaticBox(19.5f, 0.0f, 0.5f, GameConfig.WORLD_HEIGHT, FixtureType.WALL);

        Player player = Player.create(bf, 19.1f, 5.0f);
        world.step(GameConfig.TIME_STEP, 8, 3);
        assertTrue(player.getContactCount(Direction.RIGHT) >= 1, "Right sensor must contact right wall");

        // Transition to RIGHT
        gc.setGravityDirection(Direction.RIGHT);
        player.onGravityDirectionChanged(Direction.RIGHT);
        assertTrue(player.isGrounded(), "Immediate support must be recognized upon switching to contacting wall");

        // Vertical tangential movement
        player.setMoveDirection(-1.0f);
        player.update(GameConfig.TIME_STEP);
        assertEquals(-5.0f, player.getBody().getLinearVelocity().y, EPS);

        // Jump away from right wall (leftward, negative X)
        assertTrue(player.jump());
        assertTrue(player.getBody().getLinearVelocity().x < -6.0f);

        world.dispose();
    }

    @Test
    public void test21_ScenarioT5_FourDirectionCycle() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        Direction[] cycle = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT, Direction.DOWN };
        for (Direction dir : cycle) {
            gc.setGravityDirection(dir);
            player.onGravityDirectionChanged(dir);
            assertEquals(dir, gc.getGravityDirection());
            assertEquals(dir, player.getSupportDirection());
        }

        world.dispose();
    }

    @Test
    public void test22_ScenarioT6_JumpAntiExploit() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.DOWN);

        bf.createStaticBox(0.0f, 0.0f, 10.0f, 1.0f, FixtureType.GROUND);
        bf.createStaticBox(0.0f, 1.0f, 1.0f, 10.0f, FixtureType.WALL);

        Player player = Player.create(bf, 0.6f, 1.0f);
        world.step(GameConfig.TIME_STEP, 8, 3);

        assertTrue(player.getContactCount(Direction.DOWN) >= 1);
        assertTrue(player.getContactCount(Direction.LEFT) >= 1);

        // Jump consumes the latch
        assertTrue(player.jump());
        assertTrue(player.isJumpConsumed());

        // Repeated switching between existing contacts must not grant free jump
        for (int i = 0; i < 8; i++) {
            Direction dir = (i % 2 == 0) ? Direction.LEFT : Direction.DOWN;
            gc.setGravityDirection(dir);
            player.onGravityDirectionChanged(dir);
            assertTrue(player.isJumpConsumed(), "Jump latch must remain consumed");
            assertFalse(player.jump(), "Jump must be blocked when latch is consumed");
        }

        world.dispose();
    }

    @Test
    public void test23_ScenarioT7_AirborneGravitySwitching() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);
        gc.setGravityDirection(Direction.DOWN);

        bf.createStaticBox(0.0f, 0.0f, 10.0f, 1.0f, FixtureType.GROUND);
        Player player = Player.create(bf, 5.0f, 1.0f);
        world.step(GameConfig.TIME_STEP, 8, 3);
        assertTrue(player.jump());

        // Place in open air
        player.getBody().setTransform(10.0f, 6.0f, 0f);
        world.step(GameConfig.TIME_STEP, 8, 3);

        assertFalse(player.isGrounded(), "Player should be airborne");
        assertTrue(player.isJumpConsumed());

        // Switch gravity in air
        Direction[] dirs = { Direction.UP, Direction.LEFT, Direction.RIGHT };
        for (Direction dir : dirs) {
            gc.setGravityDirection(dir);
            player.onGravityDirectionChanged(dir);
            assertFalse(player.isGrounded(), "Airborne player must not become grounded");
            assertFalse(player.jump(), "Airborne player cannot jump");
        }

        // Land on surface
        player.onSensorContactBegin(FixtureType.PLAYER_RIGHT_SENSOR);
        assertTrue(player.isGrounded());
        assertFalse(player.isJumpConsumed());
        assertTrue(player.jump());

        world.dispose();
    }

    @Test
    public void test24_ScenarioT8_RapidGravitySwitching() {
        World world = new World(new Vector2(0f, 0f), true);
        GravityController gc = new GravityController(world);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        gc.setGravityDirection(Direction.DOWN);
        player.onGravityDirectionChanged(Direction.DOWN);

        for (int i = 0; i < 20; i++) {
            gc.setGravityDirection(Direction.UP);
            player.onGravityDirectionChanged(Direction.UP);
            gc.setGravityDirection(Direction.LEFT);
            player.onGravityDirectionChanged(Direction.LEFT);
            gc.setGravityDirection(Direction.RIGHT);
            player.onGravityDirectionChanged(Direction.RIGHT);
            gc.setGravityDirection(Direction.DOWN);
            player.onGravityDirectionChanged(Direction.DOWN);
        }

        assertEquals(Direction.DOWN, gc.getGravityDirection());
        assertEquals(Direction.DOWN, player.getSupportDirection());
        assertEquals(new Vector2(0f, -9.81f), world.getGravity());

        world.dispose();
    }

    @Test
    public void test25_ScenarioT9_LockedWorldProtection() {
        World world = new World(new Vector2(0f, -9.81f), true);
        GravityController gc = new GravityController(world);

        boolean[] caughtLocked = new boolean[1];
        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                try {
                    gc.setGravityDirection(Direction.UP);
                } catch (IllegalStateException e) {
                    caughtLocked[0] = true;
                }
            }
            @Override public void endContact(Contact contact) {}
            @Override public void preSolve(Contact contact, Manifold oldManifold) {}
            @Override public void postSolve(Contact contact, ContactImpulse impulse) {}
        });

        com.badlogic.gdx.physics.box2d.BodyDef bdDynamic = new com.badlogic.gdx.physics.box2d.BodyDef();
        bdDynamic.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody;
        com.badlogic.gdx.physics.box2d.Body bDynamic = world.createBody(bdDynamic);
        com.badlogic.gdx.physics.box2d.PolygonShape shape1 = new com.badlogic.gdx.physics.box2d.PolygonShape();
        shape1.setAsBox(1f, 1f);
        bDynamic.createFixture(shape1, 1f);
        shape1.dispose();

        com.badlogic.gdx.physics.box2d.BodyDef bdStatic = new com.badlogic.gdx.physics.box2d.BodyDef();
        bdStatic.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody;
        com.badlogic.gdx.physics.box2d.Body bStatic = world.createBody(bdStatic);
        com.badlogic.gdx.physics.box2d.PolygonShape shape2 = new com.badlogic.gdx.physics.box2d.PolygonShape();
        shape2.setAsBox(1f, 1f);
        bStatic.createFixture(shape2, 1f);
        shape2.dispose();

        world.step(GameConfig.TIME_STEP, 8, 3);
        assertTrue(caughtLocked[0], "GravityController must reject changes during locked world step");
        assertEquals(Direction.DOWN, gc.getGravityDirection());

        world.dispose();
    }

    @Test
    public void test26_ScenarioT10_DisposalLifecycle() {
        PhysicsController pc = new PhysicsController();
        pc.dispose();

        assertTrue(pc.isDisposed());
        assertDoesNotThrow(pc::dispose);
        assertThrows(IllegalStateException.class, () -> pc.update(0.016f));

        World dispWorld = new World(new Vector2(0f, 0f), true);
        BodyFactory bfDisp = new BodyFactory(dispWorld);
        Player pDisp = Player.create(bfDisp, 5f, 5f);
        pDisp.dispose();
        assertTrue(pDisp.isDisposed());
        assertFalse(pDisp.jump(), "Disposed player must reject jump");
        pDisp.update(0.016f);
        pDisp.setMoveDirection(1.0f);

        dispWorld.dispose();
    }

    // =========================================================================
    // SECTION 4: SUB-PHASE REGRESSIONS (27 - 32, Phases 3C - 3E.4)
    // =========================================================================

    @Test
    public void test27_RegressionPhase3C_SensorsAndCounters() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5f, 5f);

        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertEquals(0, player.getContactCount(Direction.DOWN), "Contact count must not underflow below 0");

        player.setMoveDirection(2.5f);
        assertEquals(1.0f, player.getMoveDirection(), EPS, "Move direction must clamp to +1.0");

        player.setMoveDirection(-3.0f);
        assertEquals(-1.0f, player.getMoveDirection(), EPS, "Move direction must clamp to -1.0");

        world.dispose();
    }

    @Test
    public void test28_RegressionPhase3D_GravityVectors() {
        World world = new World(new Vector2(0f, 0f), true);
        GravityController gc = new GravityController(world);

        assertEquals(new Vector2(0f, -9.81f), gc.getGravityVector());
        gc.setGravityDirection(Direction.UP);
        assertEquals(new Vector2(0f, 9.81f), gc.getGravityVector());
        gc.setGravityDirection(Direction.LEFT);
        assertEquals(new Vector2(-9.81f, 0f), gc.getGravityVector());
        gc.setGravityDirection(Direction.RIGHT);
        assertEquals(new Vector2(9.81f, 0f), gc.getGravityVector());
        assertEquals(new Vector2(1f, 0f), gc.getNormalizedGravityDirection());

        world.dispose();
    }

    @Test
    public void test29_RegressionPhase3E1_SupportDirectionSync() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5f, 5f);

        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);
        int leftBefore = player.getContactCount(Direction.LEFT);
        player.onGravityDirectionChanged(Direction.LEFT);

        assertEquals(Direction.LEFT, player.getSupportDirection());
        assertTrue(player.isGrounded());
        assertEquals(leftBefore, player.getContactCount(Direction.LEFT));

        world.dispose();
    }

    @Test
    public void test30_RegressionPhase3E2_TangentialMovementAndNormalPreservation() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5f, 5f);
        player.onGravityDirectionChanged(Direction.LEFT);

        player.getBody().setLinearVelocity(-7.0f, 0.0f);
        player.setMoveDirection(1.0f);
        player.update(GameConfig.TIME_STEP);

        assertEquals(-7.0f, player.getBody().getLinearVelocity().x, EPS, "Normal velocity must be preserved");
        assertEquals(5.0f, player.getBody().getLinearVelocity().y, EPS, "Tangential velocity must be updated");

        world.dispose();
    }

    @Test
    public void test31_RegressionPhase3E3_DirectionIndependentJump() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5f, 5f);
        player.onGravityDirectionChanged(Direction.LEFT);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);

        player.getBody().setLinearVelocity(0f, 5.0f);
        assertTrue(player.jump());
        assertEquals(6.5f, player.getBody().getLinearVelocity().x, EPS, "Jump away from left surface launches rightward (+X)");
        assertEquals(5.0f, player.getBody().getLinearVelocity().y, EPS, "Tangential velocity must be preserved during jump");
        assertFalse(player.jump(), "Double jump must be rejected");

        world.dispose();
    }

    @Test
    public void test32_RegressionPhase3E4_TransitionOrderingAndAntiExploit() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5f, 5f);
        player.onGravityDirectionChanged(Direction.LEFT);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);

        assertTrue(player.jump());
        assertTrue(player.isJumpConsumed());

        // Switch to DOWN (airborne on DOWN)
        player.onGravityDirectionChanged(Direction.DOWN);
        assertTrue(player.isJumpConsumed());
        assertFalse(player.jump());

        // Switch back to LEFT (still touching left surface, but latch was consumed)
        player.onGravityDirectionChanged(Direction.LEFT);
        assertTrue(player.isGrounded());
        assertTrue(player.isJumpConsumed());
        assertFalse(player.jump(), "Switching to an already-contacting surface must not reset consumed jump");

        world.dispose();
    }
}
