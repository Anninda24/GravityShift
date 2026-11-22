package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.Entity;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
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
import com.badlogic.gdx.physics.box2d.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive verification test suite for Phase 3F.1:
 * Timestep Clamping, Render Interpolation, and Regression Verification.
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

    // 1. Frame delta below the maximum
    @Test
    public void testFrameDeltaBelowMaximum() {
        PhysicsController pc = new PhysicsController();
        float subDelta = 0.008f; // ~120Hz frame delta (< MAX_FRAME_DELTA 0.1f)
        pc.update(subDelta);

        // Sub-timestep delta should accumulate without triggering a full 1/60s step
        assertEquals(subDelta, pc.getAccumulatorRemainder(), EPS);
        float expectedAlpha = subDelta / GameConfig.TIME_STEP;
        assertEquals(expectedAlpha, pc.getInterpolationAlpha(), EPS);
        pc.dispose();
    }

    // 2. Frame delta above the maximum
    @Test
    public void testFrameDeltaAboveMaximum() {
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

    // 3. Negative and non-finite frame delta handling
    @Test
    public void testNegativeAndNonFiniteFrameDeltaHandling() {
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

    // 4. Maximum of six physics steps per frame
    @Test
    public void testMaximumOfSixPhysicsStepsPerFrame() {
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

    // 5. Accumulator remainder and alpha calculation
    @Test
    public void testAccumulatorRemainderAndAlphaCalculation() {
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

    // 6. Alpha remains within [0, 1]
    @Test
    public void testAlphaRemainsWithinZeroToOne() {
        PhysicsController pc = new PhysicsController();
        float[] testDeltas = { 0.0f, 0.001f, 0.010f, 0.0166667f, 0.020f, 0.0333333f, 0.099f, 0.1f, 0.25f, 1.0f };
        for (float delta : testDeltas) {
            pc.update(delta);
            float alpha = pc.getInterpolationAlpha();
            assertTrue(alpha >= 0.0f && alpha <= 1.0f, "Alpha (" + alpha + ") outside [0, 1] for delta " + delta);
        }
        pc.dispose();
    }

    // 7. Correct midpoint position interpolation
    @Test
    public void testCorrectMidpointPositionInterpolation() {
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

    // 8. Correct interpolation at alpha 0 and alpha 1
    @Test
    public void testCorrectInterpolationAtAlphaZeroAndOne() {
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

    // 9. Angle interpolation across the angular wrap boundary
    @Test
    public void testAngleInterpolationAcrossWrapBoundary() {
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
        // Shortest arc midpoint between (PI - 0.1) and (-PI + 0.1) is PI (or -PI)
        assertTrue(Math.abs(Math.abs(midAngle) - Math.PI) < 0.01f,
                "Expected angle near PI, got " + midAngle);

        world.dispose();
    }

    // 10. Newly created entity snapshot initialization
    @Test
    public void testNewlyCreatedEntitySnapshotInitialization() {
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

    // 11. Teleport synchronization without interpolation streaks
    @Test
    public void testTeleportSynchronizationWithoutInterpolationStreaks() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        com.badlogic.gdx.physics.box2d.Body body = bf.createDynamicBox(3.0f, 5.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f);
        TestEntity entity = new TestEntity(body);

        // Pre-step at (3.0, 5.0)
        entity.capturePreStepTransform();
        // Teleport to (15.0, 8.0)
        body.setTransform(15.0f, 8.0f, 0f);
        // Explicitly synchronize transform to clear teleport streak
        entity.syncTransform();

        Vector2 interpMid = entity.getInterpolatedPosition(0.5f);
        assertEquals(15.0f, interpMid.x, EPS);
        assertEquals(8.0f, interpMid.y, EPS);

        world.dispose();
    }

    // 12. Interpolation does not mutate the Box2D body transform
    @Test
    public void testInterpolationDoesNotMutateBox2DBodyTransform() {
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

    // 13. Existing gravity, player movement, jumping, and contact regression tests (Phase 3E.5 full suite)
    @Test
    public void testAllPhase3E5Regressions() {
        World world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);
        GravityController gc = new GravityController(world);

        // Chamber fixtures: Floor (0..20, 0..1), Ceiling (0..20, 11..12), Left Wall (0..1, 1..11), Right Wall (19..20, 1..11)
        bf.createStaticBox(0f, 0f, 20f, 1f, FixtureType.GROUND);
        bf.createStaticBox(0f, 11f, 20f, 1f, FixtureType.CEILING);
        bf.createStaticBox(0f, 1f, 1f, 10f, FixtureType.WALL);
        bf.createStaticBox(19f, 1f, 1f, 10f, FixtureType.WALL);

        // 1. DOWN Verification
        gc.setGravityDirection(Direction.DOWN);
        Player pDown = Player.create(bf, 5.0f, 1.0f);
        world.step(1f / 60f, 8, 3);
        pDown.onGravityDirectionChanged(Direction.DOWN);
        pDown.getBody().setLinearVelocity(2f, -3f);
        pDown.setMoveDirection(1.0f);
        pDown.update(1f / 60f);
        assertTrue(Math.abs(pDown.getBody().getLinearVelocity().x - 5.0f) < EPS);
        assertTrue(Math.abs(pDown.getBody().getLinearVelocity().y - (-3.0f)) < EPS);
        assertTrue(pDown.jump());
        assertEquals(Direction.DOWN, gc.getGravityDirection());
        assertEquals(new Vector2(0f, -9.81f), world.getGravity());
        assertEquals(Direction.DOWN, pDown.getSupportDirection());
        assertTrue(pDown.isJumpConsumed());

        // 2. UP Verification
        gc.setGravityDirection(Direction.UP);
        Player pUp = Player.create(bf, 5.0f, 9.6f);
        world.step(1f / 60f, 8, 3);
        pUp.onGravityDirectionChanged(Direction.UP);
        pUp.getBody().setLinearVelocity(2f, 3f);
        pUp.setMoveDirection(-1.0f);
        pUp.update(1f / 60f);
        assertTrue(Math.abs(pUp.getBody().getLinearVelocity().x - (-5.0f)) < EPS);
        assertTrue(Math.abs(pUp.getBody().getLinearVelocity().y - 3.0f) < EPS);
        assertTrue(pUp.jump());
        assertEquals(Direction.UP, gc.getGravityDirection());
        assertEquals(new Vector2(0f, 9.81f), world.getGravity());

        // 3. LEFT Verification
        gc.setGravityDirection(Direction.LEFT);
        Player pLeft = Player.create(bf, 1.0f, 5.0f);
        world.step(1f / 60f, 8, 3);
        pLeft.onGravityDirectionChanged(Direction.LEFT);
        pLeft.getBody().setLinearVelocity(-4f, 2f);
        pLeft.setMoveDirection(1.0f);
        pLeft.update(1f / 60f);
        assertTrue(Math.abs(pLeft.getBody().getLinearVelocity().y - 5.0f) < EPS);
        assertTrue(pLeft.jump());
        assertEquals(Direction.LEFT, gc.getGravityDirection());
        assertEquals(new Vector2(-9.81f, 0f), world.getGravity());

        // 4. RIGHT Verification
        gc.setGravityDirection(Direction.RIGHT);
        Player pRight = Player.create(bf, 18.2f, 5.0f);
        world.step(1f / 60f, 8, 3);
        pRight.onGravityDirectionChanged(Direction.RIGHT);
        pRight.getBody().setLinearVelocity(4f, 2f);
        pRight.setMoveDirection(-1.0f);
        pRight.update(1f / 60f);
        assertTrue(Math.abs(pRight.getBody().getLinearVelocity().y - (-5.0f)) < EPS);
        assertTrue(pRight.jump());
        assertEquals(Direction.RIGHT, gc.getGravityDirection());
        assertEquals(new Vector2(9.81f, 0f), world.getGravity());

        world.dispose();
    }
}
