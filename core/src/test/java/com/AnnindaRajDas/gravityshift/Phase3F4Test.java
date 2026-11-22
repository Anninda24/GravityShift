package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.physics.GameContactListener;
import com.AnnindaRajDas.gravityshift.physics.GravityController;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for Phase 3F.4:
 * Coyote Time & Jump Buffering.
 *
 * Verifies all 18 requirements:
 * 1. Grounded jump succeeds.
 * 2. Jump immediately after leaving support succeeds.
 * 3. Jump within 100ms coyote window succeeds.
 * 4. Jump after coyote window expires fails.
 * 5. Buffered jump executes when landing occurs within 100ms.
 * 6. Buffered jump expires when landing occurs after 100ms.
 * 7. Buffered jump executes on the next eligible physics frame.
 * 8. Repeated jump presses do not bypass the latch.
 * 9. A second jump while airborne fails.
 * 10. Gravity switching while airborne does not reset jump consumption.
 * 11. Gravity switching while grounded does not manufacture an extra jump.
 * 12. Corner landing correctly resets the latch after genuine support departure.
 * 13. Landing without genuine support departure does not reset the latch.
 * 14. Timers behave consistently under different render-frame delta patterns.
 * 15. Timers remain correct when the physics accumulator executes zero, one, or multiple steps.
 * 16. All four gravity directions support correct jump eligibility.
 * 17. Jump velocity remains directed opposite gravity.
 * 18. Existing player disposal and lifecycle behavior remain correct.
 */
public class Phase3F4Test {

    private static final float EPS = 0.001f;

    @BeforeAll
    public static void setUp() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        Box2D.init();
    }

    // 1. Grounded jump succeeds
    @Test
    public void test01_GroundedJumpSucceeds() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        assertTrue(player.isGrounded(), "Player must be grounded");
        assertTrue(player.isJumpEligible(), "Player must be eligible to jump when grounded");
        assertTrue(player.jump(), "Grounded jump must return true");
        assertTrue(player.isJumpConsumed(), "Jump latch must be consumed");
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS,
            "Jump velocity must be JUMP_VELOCITY opposite gravity");

        world.dispose();
    }

    // 2. Jump immediately after leaving support succeeds
    @Test
    public void test02_JumpImmediatelyAfterLeavingSupportSucceeds() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.isGrounded());

        // Leave support
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertFalse(player.isGrounded(), "Player is no longer grounded");
        assertTrue(player.isCoyoteActive(), "Coyote time must be active immediately upon leaving support");
        assertEquals(Player.COYOTE_DURATION, player.getCoyoteTimer(), EPS);

        assertTrue(player.jump(), "Jump immediately after leaving support must succeed");
        assertTrue(player.isJumpConsumed());
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 3. Jump within 100ms coyote window succeeds
    @Test
    public void test03_JumpWithin100msCoyoteWindowSucceeds() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Leave support
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Advance 4 physics steps (4 * 16.67ms = ~66.7ms < 100ms)
        for (int i = 0; i < 4; i++) {
            player.stepPhysics(GameConfig.TIME_STEP);
        }

        assertTrue(player.isCoyoteActive(), "Coyote timer must still be open after 4 steps (~66.7ms)");
        assertTrue(player.getCoyoteTimer() > 0.0f);
        assertTrue(player.jump(), "Jump within 100ms coyote window must succeed");
        assertTrue(player.isJumpConsumed());
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 4. Jump after coyote window expires fails
    @Test
    public void test04_JumpAfterCoyoteWindowExpiresFails() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Leave support
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Advance 7 physics steps (7 * 16.6667ms = 116.7ms > 100ms)
        for (int i = 0; i < 7; i++) {
            player.stepPhysics(GameConfig.TIME_STEP);
        }

        assertFalse(player.isCoyoteActive(), "Coyote timer must expire after 100ms");
        assertEquals(0.0f, player.getCoyoteTimer(), EPS);
        assertFalse(player.isJumpEligible(), "Player cannot be eligible after coyote expiry");
        assertFalse(player.jump(), "Jump after coyote window expiration must fail");
        assertFalse(player.isJumpConsumed(), "Jump was not launched so latch is not consumed");
        assertNotEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 5. Buffered jump executes when landing occurs within 100ms
    @Test
    public void test05_BufferedJumpExecutesWhenLandingOccursWithin100ms() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        // Airborne player (no contacts)
        assertFalse(player.isGrounded());

        // Press jump 3 steps (~50ms) before landing
        assertFalse(player.jump(), "Airborne jump cannot launch immediately");
        assertTrue(player.isJumpBuffered(), "Jump must be buffered");

        // Advance 3 physics steps in the air
        for (int i = 0; i < 3; i++) {
            player.stepPhysics(GameConfig.TIME_STEP);
        }
        assertTrue(player.isJumpBuffered(), "Buffer must still be active after 50ms");

        // Touch ground (landing)
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.isGrounded());

        // Next physics frame executes the buffered jump
        player.stepPhysics(GameConfig.TIME_STEP);
        assertTrue(player.isJumpConsumed(), "Buffered jump must have executed upon landing frame");
        assertFalse(player.isJumpBuffered(), "Jump buffer must be consumed");
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS,
            "Vertical velocity must match JUMP_VELOCITY from buffered jump execution");

        world.dispose();
    }

    // 6. Buffered jump expires when landing occurs after 100ms
    @Test
    public void test06_BufferedJumpExpiresWhenLandingOccursAfter100ms() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        // Airborne player buffers a jump
        assertFalse(player.jump());
        assertTrue(player.isJumpBuffered());

        // 8 steps elapse in the air (8 * 16.67ms = ~133ms > 100ms)
        for (int i = 0; i < 8; i++) {
            player.stepPhysics(GameConfig.TIME_STEP);
        }

        assertFalse(player.isJumpBuffered(), "Jump buffer must have expired after 100ms");
        assertEquals(0.0f, player.getJumpBufferTimer(), EPS);

        // Player lands on ground
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.stepPhysics(GameConfig.TIME_STEP);

        // Buffered jump should NOT execute
        assertFalse(player.isJumpConsumed(), "Expired buffer must not execute jump on landing");
        assertNotEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 7. Buffered jump executes on the next eligible physics frame
    @Test
    public void test07_BufferedJumpExecutesOnNextEligiblePhysicsFrame() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        // Buffer jump while airborne
        player.jump();
        assertTrue(player.isJumpBuffered());

        // Land on surface
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        // Before stepPhysics, player is grounded but buffered jump executes on next physics step
        assertTrue(player.isGrounded());

        // Step physics frame
        player.stepPhysics(GameConfig.TIME_STEP);
        assertTrue(player.isJumpConsumed(), "Jump executed on physics frame");
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 8. Repeated jump presses do not bypass the latch
    @Test
    public void test08_RepeatedJumpPressesDoNotBypassLatch() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Grounded jump
        assertTrue(player.jump());
        assertTrue(player.isJumpConsumed());

        // Leave support
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Repeated jump presses while airborne
        for (int i = 0; i < 10; i++) {
            assertFalse(player.jump(), "Repeated airborne jump press #" + i + " must fail");
            assertTrue(player.isJumpConsumed(), "Jump latch must remain consumed");
        }

        world.dispose();
    }

    // 9. A second jump while airborne fails
    @Test
    public void test09_SecondJumpWhileAirborneFails() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // First jump
        assertTrue(player.jump());

        // Airborne
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.getBody().setLinearVelocity(0f, 2.0f); // ascending

        // Attempt second jump
        assertFalse(player.jump(), "Second jump while airborne must fail");
        assertEquals(2.0f, player.getBody().getLinearVelocity().y, EPS, "Velocity must not be overwritten");

        world.dispose();
    }

    // 10. Gravity switching while airborne does not reset jump consumption
    @Test
    public void test10_GravitySwitchingWhileAirborneDoesNotResetJumpConsumption() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Grounded jump
        assertTrue(player.jump());
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.isJumpConsumed());

        // Switch gravity in air to UP, LEFT, RIGHT, DOWN
        Direction[] dirs = { Direction.UP, Direction.LEFT, Direction.RIGHT, Direction.DOWN };
        for (Direction dir : dirs) {
            player.onGravityDirectionChanged(dir);
            assertTrue(player.isJumpConsumed(), "Switching to " + dir + " must not reset consumed latch");
            assertFalse(player.isCoyoteActive(), "Switching to " + dir + " must not create coyote window");
            assertFalse(player.jump(), "Jump must fail after airborne gravity switch to " + dir);
        }

        world.dispose();
    }

    // 11. Gravity switching while grounded does not manufacture an extra jump
    @Test
    public void test11_GravitySwitchingWhileGroundedDoesNotManufactureExtraJump() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Grounded on DOWN and touching LEFT wall in a corner
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);

        // Jump off floor
        assertTrue(player.jump());
        assertTrue(player.isJumpConsumed());
        assertFalse(player.hasLeftSupportSinceLaunch(), "Player has not yet left support");

        // Gravity shifts to LEFT while still touching left wall
        player.onGravityDirectionChanged(Direction.LEFT);
        assertTrue(player.isGrounded(), "Player contacts left wall");
        assertTrue(player.isJumpConsumed(), "Jump latch must remain consumed; corner shift cannot reset latch without departure");
        assertFalse(player.jump(), "Jump must be rejected because player never genuinely left support");

        world.dispose();
    }

    // 12. Corner landing correctly resets the latch after genuine support departure
    @Test
    public void test12_CornerLandingCorrectlyResetsLatchAfterGenuineSupportDeparture() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Grounded on DOWN
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.jump());

        // Genuinely leave support (fly airborne)
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.hasLeftSupportSinceLaunch(), "Player genuinely left support");

        // Shift gravity to LEFT while flying
        player.onGravityDirectionChanged(Direction.LEFT);

        // Land on LEFT wall
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);
        assertTrue(player.isGrounded(), "Grounded against left wall");
        assertFalse(player.isJumpConsumed(), "Latch must reset upon genuine landing against new surface");
        assertTrue(player.jump(), "Jump away from left wall must succeed");
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().x, EPS,
            "Jump away from left wall must be directed right (+X)");

        world.dispose();
    }

    // 13. Landing without genuine support departure does not reset the latch
    @Test
    public void test13_LandingWithoutGenuineSupportDepartureDoesNotResetLatch() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.jump());
        assertFalse(player.hasLeftSupportSinceLaunch());

        // A second contact begins on the same direction before departure
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.isJumpConsumed(), "Latch must NOT reset without departure");
        assertFalse(player.jump(), "Jump must be rejected");

        world.dispose();
    }

    // 14. Timers behave consistently under different render-frame delta patterns
    @Test
    public void test14_TimersBehaveConsistentlyUnderDifferentRenderFrameDeltaPatterns() {
        // Test setup A: 60Hz delta (0.0166667s) for 6 frames = 6 physics steps
        PhysicsController pcA = new PhysicsController();
        EntityManager emA = new EntityManager();
        pcA.setStepListener(emA);
        Player pA = Player.create(pcA.getBodyFactory(), 5f, 5f);
        emA.addEntity(pA);
        pA.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        pA.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR); // coyote timer = 0.100s
        for (int i = 0; i < 4; i++) {
            pcA.update(GameConfig.TIME_STEP);
        }

        // Test setup B: 120Hz delta (0.0083333s) for 8 frames = exactly 4 physics steps
        PhysicsController pcB = new PhysicsController();
        EntityManager emB = new EntityManager();
        pcB.setStepListener(emB);
        Player pB = Player.create(pcB.getBodyFactory(), 5f, 5f);
        emB.addEntity(pB);
        pB.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        pB.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR); // coyote timer = 0.100s
        for (int i = 0; i < 8; i++) {
            pcB.update(GameConfig.TIME_STEP / 2.0f);
        }

        // Test setup C: 30Hz delta (0.0333333s) for 2 frames = exactly 4 physics steps
        PhysicsController pcC = new PhysicsController();
        EntityManager emC = new EntityManager();
        pcC.setStepListener(emC);
        Player pC = Player.create(pcC.getBodyFactory(), 5f, 5f);
        emC.addEntity(pC);
        pC.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        pC.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR); // coyote timer = 0.100s
        for (int i = 0; i < 2; i++) {
            pcC.update(GameConfig.TIME_STEP * 2.0f);
        }

        // All three patterns must have identical remaining coyote time
        assertEquals(pA.getCoyoteTimer(), pB.getCoyoteTimer(), EPS, "60Hz and 120Hz must produce identical timer values");
        assertEquals(pA.getCoyoteTimer(), pC.getCoyoteTimer(), EPS, "60Hz and 30Hz must produce identical timer values");

        pcA.dispose();
        pcB.dispose();
        pcC.dispose();
    }

    // 15. Timers remain correct when physics accumulator executes zero, one, or multiple steps
    @Test
    public void test15_TimersRemainCorrectWhenPhysicsAccumulatorExecutesZeroOneOrMultipleSteps() {
        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);
        Player player = Player.create(pc.getBodyFactory(), 5f, 5f);
        em.addEntity(player);

        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        float initialTimer = player.getCoyoteTimer();
        assertEquals(Player.COYOTE_DURATION, initialTimer, EPS);

        // 1. Delta < TIME_STEP -> 0 physics steps executed
        pc.update(0.005f);
        assertEquals(initialTimer, player.getCoyoteTimer(), EPS, "Timer must not advance when 0 physics steps execute");

        // 2. Delta = TIME_STEP -> 1 physics step executed
        pc.update(GameConfig.TIME_STEP);
        float afterOneStep = initialTimer - GameConfig.TIME_STEP;
        assertEquals(afterOneStep, player.getCoyoteTimer(), EPS, "Timer must advance by exactly 1 step");

        // 3. Delta = 3 * TIME_STEP -> 3 physics steps executed
        pc.update(GameConfig.TIME_STEP * 3.0f);
        float afterThreeSteps = afterOneStep - (GameConfig.TIME_STEP * 3.0f);
        assertEquals(afterThreeSteps, player.getCoyoteTimer(), EPS, "Timer must advance by exactly 3 steps");

        pc.dispose();
    }

    // 16. All four gravity directions support correct jump eligibility
    @Test
    public void test16_AllFourGravityDirectionsSupportCorrectJumpEligibility() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        FixtureType[] sensors = {
            FixtureType.PLAYER_BOTTOM_SENSOR,
            FixtureType.PLAYER_TOP_SENSOR,
            FixtureType.PLAYER_LEFT_SENSOR,
            FixtureType.PLAYER_RIGHT_SENSOR
        };
        Direction[] dirs = { Direction.DOWN, Direction.UP, Direction.LEFT, Direction.RIGHT };

        for (int i = 0; i < 4; i++) {
            Direction dir = dirs[i];
            FixtureType sensor = sensors[i];

            player.onGravityDirectionChanged(dir);
            player.onSensorContactBegin(sensor);
            assertTrue(player.isGrounded(), "Must be grounded along " + dir);
            assertTrue(player.isJumpEligible(), "Must be eligible along " + dir);

            // Leave support
            player.onSensorContactEnd(sensor);
            assertFalse(player.isGrounded());
            assertTrue(player.isCoyoteActive(), "Coyote time must activate along " + dir);
            assertTrue(player.jump(), "Coyote jump must succeed along " + dir);
            assertTrue(player.isJumpConsumed());

            // Land again
            player.onSensorContactBegin(sensor);
            assertFalse(player.isJumpConsumed(), "Latch must reset on landing along " + dir);
            player.onSensorContactEnd(sensor);
        }

        world.dispose();
    }

    // 17. Jump velocity remains directed opposite gravity
    @Test
    public void test17_JumpVelocityRemainsDirectedOppositeGravity() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        // DOWN: gravity -Y -> jump +Y
        player.onGravityDirectionChanged(Direction.DOWN);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.jump());
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);

        // UP: gravity +Y -> jump -Y
        player.onGravityDirectionChanged(Direction.UP);
        player.onSensorContactBegin(FixtureType.PLAYER_TOP_SENSOR);
        assertTrue(player.jump());
        assertEquals(-Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);
        player.onSensorContactEnd(FixtureType.PLAYER_TOP_SENSOR);

        // LEFT: gravity -X -> jump +X
        player.onGravityDirectionChanged(Direction.LEFT);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);
        assertTrue(player.jump());
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().x, EPS);
        player.onSensorContactEnd(FixtureType.PLAYER_LEFT_SENSOR);

        // RIGHT: gravity +X -> jump -X
        player.onGravityDirectionChanged(Direction.RIGHT);
        player.onSensorContactBegin(FixtureType.PLAYER_RIGHT_SENSOR);
        assertTrue(player.jump());
        assertEquals(-Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().x, EPS);
        player.onSensorContactEnd(FixtureType.PLAYER_RIGHT_SENSOR);

        world.dispose();
    }

    // 18. Existing player disposal and lifecycle behavior remain correct
    @Test
    public void test18_ExistingPlayerDisposalAndLifecycleBehaviorRemainCorrect() {
        World world = new World(new Vector2(0f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 5.0f);

        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.jump();
        player.dispose();

        assertTrue(player.isDisposed());
        assertFalse(player.jump(), "Disposed player must reject jump");
        assertFalse(player.isJumpEligible(), "Disposed player cannot be eligible");
        assertEquals(0.0f, player.getCoyoteTimer(), EPS);
        assertEquals(0.0f, player.getJumpBufferTimer(), EPS);
        assertFalse(player.isCoyoteActive());
        assertFalse(player.isJumpBuffered());

        // Safe no-ops
        assertDoesNotThrow(() -> player.stepPhysics(GameConfig.TIME_STEP));
        assertDoesNotThrow(() -> player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR));
        assertDoesNotThrow(() -> player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR));
        assertDoesNotThrow(() -> player.onGravityDirectionChanged(Direction.UP));

        world.dispose();
    }
}
