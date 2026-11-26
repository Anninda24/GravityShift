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
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for Phase 3F.5:
 * Fixed-Step Impulse-Based Movement System.
 *
 * Covers:
 * 1. Grounded acceleration approaches desired tangential velocity.
 * 2. Grounded acceleration respects ~40 m/s^2 limit.
 * 3. Airborne acceleration respects ~20 m/s^2 limit.
 * 4. Opposite-direction input produces controlled deceleration and reversal.
 * 5. Zero input does not create unintended acceleration or oscillation.
 * 6. Ordinary movement does not alter gravity-parallel velocity under downward gravity.
 * 7. Gravity-parallel velocity preservation under upward gravity.
 * 8. Gravity-parallel velocity preservation under leftward gravity.
 * 9. Gravity-parallel velocity preservation under rightward gravity.
 * 10. Jump/fall velocity is not accidentally cancelled by tangential movement.
 * 11. Standing still relative to moving platform produces no unintended sliding.
 * 12. Tangential player movement is relative to moving platform.
 * 13. Losing support safely clears support reference and does not retain stale platform velocity.
 * 14. Airborne movement uses correct airborne acceleration limit (20 m/s^2).
 * 15. Gravity switching preserves world-space velocity.
 * 16. Four-way movement remains consistent after gravity switching.
 * 17. Jumping works cleanly with movement input held.
 * 18. Coyote time and jump buffering remain correct under impulse-based movement.
 * 19. Complete physical-departure invariant across all four sensor contact counts.
 * 20. Real Box2D lifecycle integration across PhysicsController and EntityManager.
 */
public class Phase3F5Test {

    private static final float EPS = 1e-3f;
    private static final float DT = GameConfig.TIME_STEP; // 1/60s

    @BeforeAll
    public static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    // Helper to create a kinematic moving platform
    private Body createKinematicPlatform(World world, float x, float y, float width, float height,
                                         float vx, float vy, FixtureType fixtureType) {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(x + width / 2.0f, y + height / 2.0f);
        Body body = world.createBody(bd);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2.0f, height / 2.0f);
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.friction = 0.4f;
        body.createFixture(fd).setUserData(fixtureType);
        shape.dispose();

        body.setLinearVelocity(vx, vy);
        return body;
    }

    // -------------------------------------------------------------------------
    // 1. TANGENTIAL MOVEMENT & ACCELERATION CLAMPING
    // -------------------------------------------------------------------------

    // 1. Grounded acceleration approaches desired tangential velocity (5.0 m/s)
    @Test
    public void test01_GroundedAccelerationApproachesDesiredVelocity() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Grounded on bottom sensor
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.isGrounded());
        assertEquals(0.0f, player.getBody().getLinearVelocity().x, EPS);

        player.setMoveDirection(1.0f); // Desired: +5.0 m/s

        // Accelerate over multiple fixed steps
        for (int i = 0; i < 15; i++) {
            player.onPrePhysicsStep(DT);
        }

        // Must smoothly reach exactly MOVE_SPEED (5.0 m/s)
        assertEquals(Player.MOVE_SPEED, player.getBody().getLinearVelocity().x, EPS,
            "Grounded player must reach 5.0 m/s desired tangential speed");

        world.dispose();
    }

    // 2. Grounded acceleration respects ~40 m/s^2 limit
    @Test
    public void test02_GroundedAccelerationRespectsMaxLimit() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.isGrounded());

        player.setMoveDirection(1.0f); // Desired: 5.0 m/s (delta = 5.0 m/s)

        // Single step of DT
        player.onPrePhysicsStep(DT);

        // Max delta V = 40.0 * (1/60) = 2/3 ≈ 0.66667 m/s
        float expectedDeltaV = Player.MAX_GROUND_ACCEL * DT;
        float actualVx = player.getBody().getLinearVelocity().x;

        assertEquals(expectedDeltaV, actualVx, EPS,
            "Single-step velocity change must be clamped to max ground acceleration * DT");

        float effectiveAccel = actualVx / DT;
        assertEquals(Player.MAX_GROUND_ACCEL, effectiveAccel, 0.1f,
            "Effective grounded acceleration must be ~40 m/s^2");

        world.dispose();
    }

    // 3. Airborne acceleration respects ~20 m/s^2 limit
    @Test
    public void test03_AirborneAccelerationRespectsMaxLimit() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Not grounded
        assertFalse(player.isGrounded());
        assertEquals(0.0f, player.getBody().getLinearVelocity().x, EPS);

        player.setMoveDirection(1.0f); // Desired: 5.0 m/s

        // Single step of DT
        player.onPrePhysicsStep(DT);

        // Max delta V airborne = 20.0 * (1/60) = 1/3 ≈ 0.33333 m/s
        float expectedDeltaV = Player.MAX_AIR_ACCEL * DT;
        float actualVx = player.getBody().getLinearVelocity().x;

        assertEquals(expectedDeltaV, actualVx, EPS,
            "Single-step airborne velocity change must be clamped to max air acceleration * DT");

        float effectiveAccel = actualVx / DT;
        assertEquals(Player.MAX_AIR_ACCEL, effectiveAccel, 0.1f,
            "Effective airborne acceleration must be ~20 m/s^2");

        world.dispose();
    }

    // 4. Opposite-direction input produces controlled deceleration and reversal
    @Test
    public void test04_OppositeDirectionInputProducesControlledDecelerationAndReversal() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Initial full speed rightward (+5.0 m/s)
        player.getBody().setLinearVelocity(5.0f, 0.0f);

        // Reverse input: full leftward (-1.0)
        player.setMoveDirection(-1.0f);

        // Step 1: should decelerate by 40 * DT ≈ 0.667 m/s
        player.onPrePhysicsStep(DT);
        assertEquals(5.0f - Player.MAX_GROUND_ACCEL * DT, player.getBody().getLinearVelocity().x, EPS);

        // Advance until reversed: total delta needed = 10.0 m/s (from +5 to -5)
        // 10.0 / 40.0 = 0.25s = 15 steps
        for (int i = 0; i < 20; i++) {
            player.onPrePhysicsStep(DT);
        }

        // Must smoothly reach -5.0 m/s without overshoot
        assertEquals(-Player.MOVE_SPEED, player.getBody().getLinearVelocity().x, EPS,
            "Player must reverse cleanly to -5.0 m/s");

        world.dispose();
    }

    // 5. Zero input does not create unintended acceleration or oscillation
    @Test
    public void test05_ZeroInputDoesNotCreateUnintendedAccelerationOrOscillation() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Stationary player with zero input
        player.getBody().setLinearVelocity(0.0f, 0.0f);
        player.setMoveDirection(0.0f);

        for (int i = 0; i < 60; i++) {
            player.onPrePhysicsStep(DT);
            assertEquals(0.0f, player.getBody().getLinearVelocity().x, EPS,
                "Stationary player must remain at exactly 0.0 velocity under zero input");
        }

        // Moving player coming to a stop
        player.getBody().setLinearVelocity(1.5f, 0.0f);
        player.setMoveDirection(0.0f);

        // Advance to full stop (1.5 / 40 ≈ 0.0375s ≈ 3 steps)
        for (int i = 0; i < 10; i++) {
            player.onPrePhysicsStep(DT);
        }

        // Must settle cleanly at 0.0f without oscillating around zero
        assertEquals(0.0f, player.getBody().getLinearVelocity().x, EPS,
            "Player coming to a stop must settle at exactly 0.0 m/s without jitter");

        world.dispose();
    }

    // -------------------------------------------------------------------------
    // 2. GRAVITY-PARALLEL VELOCITY PRESERVATION
    // -------------------------------------------------------------------------

    // 6. Ordinary movement does not alter gravity-parallel velocity under downward gravity
    @Test
    public void test06_DownwardGravity_GravityParallelVelocityStrictlyPreserved() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onGravityDirectionChanged(Direction.DOWN);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Set vertical (gravity-parallel) velocity to falling speed -3.7 m/s
        player.getBody().setLinearVelocity(0.0f, -3.7f);
        player.setMoveDirection(1.0f);

        for (int i = 0; i < 15; i++) {
            player.onPrePhysicsStep(DT);
            assertEquals(-3.7f, player.getBody().getLinearVelocity().y, EPS,
                "Y velocity must remain strictly untouched under DOWN gravity movement");
        }

        assertEquals(Player.MOVE_SPEED, player.getBody().getLinearVelocity().x, EPS);

        world.dispose();
    }

    // 7. Gravity-parallel velocity preserved under upward gravity
    @Test
    public void test07_UpwardGravity_GravityParallelVelocityStrictlyPreserved() {
        World world = new World(new Vector2(0f, 9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onGravityDirectionChanged(Direction.UP);
        player.onSensorContactBegin(FixtureType.PLAYER_TOP_SENSOR);

        player.getBody().setLinearVelocity(0.0f, 4.2f);
        player.setMoveDirection(-1.0f);

        for (int i = 0; i < 15; i++) {
            player.onPrePhysicsStep(DT);
            assertEquals(4.2f, player.getBody().getLinearVelocity().y, EPS,
                "Y velocity must remain strictly untouched under UP gravity movement");
        }

        assertEquals(-Player.MOVE_SPEED, player.getBody().getLinearVelocity().x, EPS);

        world.dispose();
    }

    // 8. Gravity-parallel velocity preserved under leftward gravity
    @Test
    public void test08_LeftwardGravity_GravityParallelVelocityStrictlyPreserved() {
        World world = new World(new Vector2(-9.81f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onGravityDirectionChanged(Direction.LEFT);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);

        // Parallel to gravity: X axis! Set vx = -6.1 m/s
        player.getBody().setLinearVelocity(-6.1f, 0.0f);
        player.setMoveDirection(1.0f); // Tangential is Y (+5.0 m/s)

        for (int i = 0; i < 15; i++) {
            player.onPrePhysicsStep(DT);
            assertEquals(-6.1f, player.getBody().getLinearVelocity().x, EPS,
                "X velocity must remain strictly untouched under LEFT gravity movement");
        }

        assertEquals(Player.MOVE_SPEED, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 9. Gravity-parallel velocity preserved under rightward gravity
    @Test
    public void test09_RightwardGravity_GravityParallelVelocityStrictlyPreserved() {
        World world = new World(new Vector2(9.81f, 0f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onGravityDirectionChanged(Direction.RIGHT);
        player.onSensorContactBegin(FixtureType.PLAYER_RIGHT_SENSOR);

        // Parallel to gravity: X axis! Set vx = +5.3 m/s
        player.getBody().setLinearVelocity(5.3f, 0.0f);
        player.setMoveDirection(-1.0f); // Tangential is Y (-5.0 m/s)

        for (int i = 0; i < 15; i++) {
            player.onPrePhysicsStep(DT);
            assertEquals(5.3f, player.getBody().getLinearVelocity().x, EPS,
                "X velocity must remain strictly untouched under RIGHT gravity movement");
        }

        assertEquals(-Player.MOVE_SPEED, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 10. Jump/fall velocity is not accidentally cancelled by tangential movement
    @Test
    public void test10_JumpAndFallVelocityNotCancelledByTangentialMovement() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Execute jump
        assertTrue(player.jump());
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        // Immediately hold full horizontal movement in the air
        player.setMoveDirection(1.0f);
        player.onPrePhysicsStep(DT);

        // Jump velocity must NOT be cancelled or altered by movement impulse
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS,
            "Jump launch velocity must not be affected by horizontal movement impulse");
        assertTrue(player.getBody().getLinearVelocity().x > 0.0f,
            "Horizontal velocity must start accelerating");

        world.dispose();
    }

    // -------------------------------------------------------------------------
    // 3. MOVING PLATFORMS & SUPPORT VELOCITY
    // -------------------------------------------------------------------------

    // 11. Standing still relative to moving platform does not produce unintended sliding
    @Test
    public void test11_StandingStillOnMovingPlatformDoesNotSlide() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Create kinematic platform moving at vx = 3.0 m/s
        Body platform = createKinematicPlatform(world, 0f, 0f, 10f, 1f, 3.0f, 0.0f, FixtureType.GROUND);

        // Player lands on moving platform
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR, platform);
        assertSame(platform, player.getActiveSupportingBody());
        assertTrue(player.isGrounded());

        // Player inputs 0 (standing still relative to platform)
        player.setMoveDirection(0.0f);

        // Accelerate player to match platform speed
        for (int i = 0; i < 15; i++) {
            player.onPrePhysicsStep(DT);
        }

        // Player's world velocity must match platform's velocity (3.0 m/s)
        assertEquals(3.0f, player.getBody().getLinearVelocity().x, EPS,
            "Player standing still on moving platform must match platform velocity in world space");

        // Relative speed = 0 (no sliding)
        float relativeSpeed = player.getBody().getLinearVelocity().x - platform.getLinearVelocity().x;
        assertEquals(0.0f, relativeSpeed, EPS, "Relative speed to platform must be exactly zero");

        world.dispose();
    }

    // 12. Tangential player movement is relative to the platform
    @Test
    public void test12_TangentialPlayerMovementIsRelativeToPlatform() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Platform moving right at 2.5 m/s
        Body platform = createKinematicPlatform(world, 0f, 0f, 10f, 1f, 2.5f, 0.0f, FixtureType.GROUND);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR, platform);

        // Move right (+1.0): target in world space = 2.5 + 5.0 = 7.5 m/s
        player.setMoveDirection(1.0f);
        for (int i = 0; i < 20; i++) {
            player.onPrePhysicsStep(DT);
        }
        assertEquals(7.5f, player.getBody().getLinearVelocity().x, EPS,
            "Moving right on platform must equal platform speed + MOVE_SPEED");

        // Move left (-1.0): target in world space = 2.5 - 5.0 = -2.5 m/s
        player.setMoveDirection(-1.0f);
        for (int i = 0; i < 30; i++) {
            player.onPrePhysicsStep(DT);
        }
        assertEquals(-2.5f, player.getBody().getLinearVelocity().x, EPS,
            "Moving left on platform must equal platform speed - MOVE_SPEED");

        world.dispose();
    }

    // 13. Losing support safely clears support reference and does not retain stale platform velocity
    @Test
    public void test13_LosingSupportClearsSupportReferenceAndStalePlatformVelocity() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        Body platform = createKinematicPlatform(world, 0f, 0f, 10f, 1f, 4.0f, 0.0f, FixtureType.GROUND);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR, platform);
        assertSame(platform, player.getActiveSupportingBody());

        // Player leaves platform (falls into air)
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR, platform);
        assertFalse(player.isGrounded());
        assertNull(player.getActiveSupportingBody(), "Support body reference must be cleared when airborne");
        assertNull(player.getSupportingBody(Direction.DOWN));

        // When airborne, movement must target input relative to stationary air (not platform)
        player.setMoveDirection(1.0f); // Desired airborne: 0 + 1.0 * 5.0 = 5.0 m/s (NOT 4.0 + 5.0)

        // Reset velocity to 0 to measure airborne acceleration
        player.getBody().setLinearVelocity(0.0f, 0.0f);
        for (int i = 0; i < 25; i++) {
            player.onPrePhysicsStep(DT);
        }

        assertEquals(Player.MOVE_SPEED, player.getBody().getLinearVelocity().x, EPS,
            "Airborne player must target 5.0 m/s, not retaining stale platform velocity");

        world.dispose();
    }

    // 14. Airborne movement uses correct airborne acceleration limit (20 m/s^2)
    @Test
    public void test14_AirborneMovementUsesAirborneAccelerationLimit() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Completely airborne
        assertFalse(player.isGrounded());
        player.setMoveDirection(1.0f);

        // Step 1: delta V must be 20 * DT, NOT 40 * DT
        player.onPrePhysicsStep(DT);
        float actualDeltaV = player.getBody().getLinearVelocity().x;

        assertEquals(Player.MAX_AIR_ACCEL * DT, actualDeltaV, EPS,
            "Airborne acceleration must be clamped to MAX_AIR_ACCEL");
        assertNotEquals(Player.MAX_GROUND_ACCEL * DT, actualDeltaV, EPS);

        world.dispose();
    }

    // -------------------------------------------------------------------------
    // 4. INTEGRATION & REGRESSIONS
    // -------------------------------------------------------------------------

    // 15. Gravity switching preserves world-space velocity
    @Test
    public void test15_GravitySwitchingPreservesWorldSpaceVelocity() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Moving horizontally at (4.0, -2.0)
        player.getBody().setLinearVelocity(4.0f, -2.0f);

        // Switch gravity to LEFT
        player.onGravityDirectionChanged(Direction.LEFT);

        assertEquals(4.0f, player.getBody().getLinearVelocity().x, EPS,
            "World-space Vx must be preserved across gravity switch");
        assertEquals(-2.0f, player.getBody().getLinearVelocity().y, EPS,
            "World-space Vy must be preserved across gravity switch");

        world.dispose();
    }

    // 16. Four-way movement remains consistent after gravity switching
    @Test
    public void test16_FourWayMovementRemainsConsistentAfterGravitySwitching() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // 1. DOWN gravity: moves X
        player.onGravityDirectionChanged(Direction.DOWN);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.setMoveDirection(1.0f);
        for (int i = 0; i < 15; i++) player.onPrePhysicsStep(DT);
        assertEquals(5.0f, player.getBody().getLinearVelocity().x, EPS);
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);

        // 2. Switch to LEFT gravity: moves Y
        player.onGravityDirectionChanged(Direction.LEFT);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);
        player.setMoveDirection(1.0f); // Screen-up (+Y)
        for (int i = 0; i < 15; i++) player.onPrePhysicsStep(DT);
        assertEquals(5.0f, player.getBody().getLinearVelocity().y, EPS);
        player.onSensorContactEnd(FixtureType.PLAYER_LEFT_SENSOR);

        // 3. Switch to UP gravity: moves X
        player.onGravityDirectionChanged(Direction.UP);
        player.onSensorContactBegin(FixtureType.PLAYER_TOP_SENSOR);
        player.setMoveDirection(-1.0f); // Screen-left (-X)
        for (int i = 0; i < 25; i++) player.onPrePhysicsStep(DT);
        assertEquals(-5.0f, player.getBody().getLinearVelocity().x, EPS);
        player.onSensorContactEnd(FixtureType.PLAYER_TOP_SENSOR);

        // 4. Switch to RIGHT gravity: moves Y
        player.onGravityDirectionChanged(Direction.RIGHT);
        player.onSensorContactBegin(FixtureType.PLAYER_RIGHT_SENSOR);
        player.setMoveDirection(-1.0f); // Screen-down (-Y)
        for (int i = 0; i < 25; i++) player.onPrePhysicsStep(DT);
        assertEquals(-5.0f, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 17. Jumping works cleanly with movement input held
    @Test
    public void test17_JumpingWorksCleanlyWithMovementInputHeld() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Full speed running right
        player.setMoveDirection(1.0f);
        for (int i = 0; i < 15; i++) player.onPrePhysicsStep(DT);
        assertEquals(5.0f, player.getBody().getLinearVelocity().x, EPS);

        // Jump while holding input
        assertTrue(player.jump(), "Jump must succeed while running");
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS,
            "Jump velocity must be JUMP_VELOCITY (+Y)");
        assertEquals(5.0f, player.getBody().getLinearVelocity().x, EPS,
            "Running speed must be preserved during jump launch");

        world.dispose();
    }

    // 18. Coyote time and jump buffering remain correct under impulse-based movement
    @Test
    public void test18_CoyoteTimeAndJumpBufferingRemainCorrectWithMovement() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Grounded
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.stepPhysics(DT);

        // Walk off ledge (contact ends without jumping)
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertTrue(player.isCoyoteActive(), "Coyote window must open after walking off ledge");

        // Hold movement input and advance 3 steps (50ms < 100ms)
        player.setMoveDirection(1.0f);
        for (int i = 0; i < 3; i++) {
            player.onPrePhysicsStep(DT);
            player.onPostPhysicsStep(DT);
        }

        // Coyote jump should still succeed while moving in air
        assertTrue(player.isCoyoteActive());
        assertTrue(player.jump(), "Coyote jump must succeed within window while steering in air");
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        world.dispose();
    }

    // 19. Complete physical-departure invariant across all four sensor contact counts
    @Test
    public void test19_CompletePhysicalDepartureAcrossAllSensorCounts() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Corner contact: floor (BOTTOM) + wall (LEFT)
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);
        assertEquals(2, player.getAllContactCount());

        // Jump
        assertTrue(player.jump());
        assertTrue(player.isJumpConsumed());
        assertFalse(player.hasLeftSupportSinceLaunch());

        // Steer away: floor contact ends, but wall contact remains
        player.setMoveDirection(1.0f);
        player.onPrePhysicsStep(DT);
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);

        // Departure must NOT be signaled because wall contact is still active!
        assertEquals(1, player.getAllContactCount());
        assertFalse(player.hasLeftSupportSinceLaunch(),
            "Departure must NOT be signaled while any surface contact remains");

        // Wall contact ends -> now free in air
        player.onSensorContactEnd(FixtureType.PLAYER_LEFT_SENSOR);
        assertEquals(0, player.getAllContactCount());
        assertTrue(player.hasLeftSupportSinceLaunch(),
            "Departure must be signaled only when ALL surface contacts reach zero");

        world.dispose();
    }

    // 20. Real Box2D lifecycle integration across PhysicsController and EntityManager
    @Test
    public void test20_RealPhysicsLifecycle_ImpulseMovementAndSimulationIntegration() {
        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);

        // Static ground at y = 1.30m
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 1.565f);
        em.addEntity(player);

        // Settle player onto ground via simulation
        for (int i = 0; i < 30; i++) {
            pc.update(DT);
        }
        assertTrue(player.isGrounded(), "Player must be grounded on ground platform");

        // Start horizontal movement
        player.setMoveDirection(1.0f);

        // Advance simulation for 20 physics steps (player accelerates via onPrePhysicsStep impulses)
        for (int i = 0; i < 20; i++) {
            pc.update(DT);
        }

        // Verify player is moving horizontally at ~5.0 m/s
        assertTrue(player.getBody().getLinearVelocity().x > 4.5f,
            "Player must have accelerated to near 5.0 m/s under real Box2D lifecycle");
        assertTrue(player.getPosition().x > 5.0f, "Player position must have advanced rightward");

        pc.dispose();
    }

    // 21. Real Box2D lifecycle: two bodies on same sensor; B enters then leaves while A remains
    @Test
    public void test21_RealBox2D_TwoBodiesSameSensor_RevertsToRemainingBodyWithoutStaleVelocity() {
        World world = new World(new Vector2(0f, -9.81f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);

        // Platform A: wide kinematic floor moving at vx = 2.0 m/s (x=0..25, y=0..1.0, top=1.0)
        Body platformA = createKinematicPlatform(world, 0f, 0f, 25f, 1.0f, 2.0f, 0.0f, FixtureType.GROUND);

        // Platform B: kinematic platform initially overlapping bottom sensor (top=0.98m, inside sensor [0.95..1.05])
        // with vx = 8.0 m/s, vy = -1.0 m/s so it initially registers contact, then descends cleanly out of sensor range
        Body platformB = createKinematicPlatform(world, 4.0f, 0.0f, 4.0f, 0.98f, 8.0f, -1.0f, FixtureType.GROUND);

        // Player spawned at x=5.0, y=1.0 (bottom sensor touches both Platform A and Platform B)
        Player player = Player.create(bf, 5.0f, 1.0f);
        player.onGravityDirectionChanged(Direction.DOWN);

        // Step 1: Both Platform A and Platform B are in contact (A registered first, then B)
        player.onPrePhysicsStep(DT);
        world.step(DT, 8, 3);
        player.onPostPhysicsStep(DT);

        assertEquals(2, player.getContactCount(Direction.DOWN),
            "Both Platform A and Platform B must contact bottom sensor");
        assertEquals(2, player.getTrackedBodyCount(Direction.DOWN));
        // Platform B was created second so Box2D reports it after A (or if either order, both are tracked)
        Body secondBody = player.getActiveSupportingBody();
        assertNotNull(secondBody);

        // Advance simulation until Platform B descends below bottom sensor (y < 0.95) and breaks contact
        boolean leftB = false;
        for (int i = 0; i < 30; i++) {
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
            if (player.getContactCount(Direction.DOWN) == 1) {
                leftB = true;
                break;
            }
        }
        assertTrue(leftB, "Platform B must exit contact while Platform A remains");
        assertEquals(1, player.getTrackedBodyCount(Direction.DOWN));
        assertNotSame(platformB, player.getActiveSupportingBody(),
            "Active support must NOT remain stale reference to detached Platform B");
        assertSame(platformA, player.getActiveSupportingBody(),
            "Active support must revert to remaining valid Platform A");

        // Stand still (moveDirection = 0): player must converge to Platform A's velocity (2.0 m/s), NOT B's (12.0 m/s)
        player.setMoveDirection(0.0f);
        for (int i = 0; i < 30; i++) {
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
        }
        assertEquals(2.0f, player.getBody().getLinearVelocity().x, 0.25f,
            "Player standing on remaining Platform A must track A's velocity (2.0 m/s), not stale B velocity");

        // Jump off Platform A into air and verify support clears completely
        assertTrue(player.jump());
        for (int i = 0; i < 10; i++) {
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
        }
        assertFalse(player.isGrounded());
        assertEquals(0, player.getTrackedBodyCount(Direction.DOWN));
        assertNull(player.getActiveSupportingBody(), "Airborne player must have null active supporting body");

        world.dispose();
    }

    // 22. Real Box2D lifecycle: multiple fixtures on the same body do not cause premature invalidation
    @Test
    public void test22_RealBox2D_MultipleFixturesSameBody_NoPrematureInvalidation() {
        World world = new World(new Vector2(0f, -9.81f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);

        // Create a single kinematic body with TWO adjacent GROUND fixtures (seam at x = 5.0)
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(5.0f, 0.5f);
        Body dualFixturePlatform = world.createBody(bd);

        PolygonShape leftShape = new PolygonShape();
        leftShape.setAsBox(1.5f, 0.5f, new Vector2(-1.5f, 0.0f), 0.0f); // x in [2.0, 5.0], y in [0.0, 1.0]
        FixtureDef fd1 = new FixtureDef();
        fd1.shape = leftShape;
        fd1.friction = 0.0f;
        dualFixturePlatform.createFixture(fd1).setUserData(FixtureType.GROUND);
        leftShape.dispose();

        PolygonShape rightShape = new PolygonShape();
        rightShape.setAsBox(1.5f, 0.5f, new Vector2(1.5f, 0.0f), 0.0f); // x in [5.0, 8.0], y in [0.0, 1.0]
        FixtureDef fd2 = new FixtureDef();
        fd2.shape = rightShape;
        fd2.friction = 0.0f;
        dualFixturePlatform.createFixture(fd2).setUserData(FixtureType.GROUND);
        rightShape.dispose();

        dualFixturePlatform.setLinearVelocity(3.0f, 0.0f);

        // Spawn player centered right over the seam at x = 5.0 (bottom-left x = 4.6)
        Player player = Player.create(bf, 4.6f, 1.0f);
        player.onGravityDirectionChanged(Direction.DOWN);

        // Step 1: bottom sensor overlaps BOTH fixtures of dualFixturePlatform
        player.onPrePhysicsStep(DT);
        world.step(DT, 8, 3);
        player.onPostPhysicsStep(DT);

        assertEquals(2, player.getContactCount(Direction.DOWN),
            "Both fixtures of the same body must register contacts on bottom sensor");
        assertEquals(1, player.getTrackedBodyCount(Direction.DOWN),
            "Both fixtures belong to 1 distinct Body");
        assertSame(dualFixturePlatform, player.getActiveSupportingBody());

        // Move rightward relative to platform so player leaves the left fixture while remaining on the right fixture
        player.setMoveDirection(1.0f);
        boolean leftOneFixture = false;
        for (int i = 0; i < 40; i++) {
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
            if (player.getContactCount(Direction.DOWN) == 1) {
                leftOneFixture = true;
                break;
            }
        }
        assertTrue(leftOneFixture, "Player must leave left fixture while staying on right fixture");
        assertTrue(player.isGrounded());
        assertEquals(1, player.getTrackedBodyCount(Direction.DOWN));
        assertSame(dualFixturePlatform, player.getActiveSupportingBody(),
            "Ending 1 of 2 fixtures on the same body must NOT prematurely invalidate the body");

        // Jump off the remaining fixture and verify complete detachment
        assertTrue(player.jump());
        for (int i = 0; i < 10; i++) {
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
        }
        assertFalse(player.isGrounded());
        assertEquals(0, player.getContactCount(Direction.DOWN));
        assertEquals(0, player.getTrackedBodyCount(Direction.DOWN));
        assertNull(player.getActiveSupportingBody());

        world.dispose();
    }

    // 23. Real Box2D lifecycle: ninth distinct supporting body triggers safe LRU overflow and never gets ignored
    @Test
    public void test23_RealBox2D_NinthDistinctSupportingBody_TriggersOverflowAndSelectsNinthBody() {
        World world = new World(new Vector2(-9.81f, 0f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);

        // Left wall sensor height = 1.2m (y in [2.1 .. 3.3] when player spawned at y = 2.0)
        // Create 8 small stationary kinematic wall tiles (0.08m tall each) inside sensor vertical range
        Body[] segments = new Body[9];
        for (int i = 0; i < 8; i++) {
            float segY = 2.15f + i * 0.13f; // spans 2.15 .. 3.14 inside [2.1 .. 3.3]
            segments[i] = createKinematicPlatform(world, 0.5f, segY, 0.5f, 0.08f, 0.0f, 0.0f, FixtureType.WALL);
        }

        // Player pressed against left wall at x = 1.0 (left sensor [0.95..1.05] contacts segments 0..7)
        Player player = Player.create(bf, 1.0f, 2.0f);
        player.onGravityDirectionChanged(Direction.LEFT);

        player.onPrePhysicsStep(DT);
        world.step(DT, 8, 3);
        player.onPostPhysicsStep(DT);

        assertEquals(8, player.getContactCount(Direction.LEFT), "First 8 distinct wall bodies must contact left sensor");
        assertEquals(8, player.getTrackedBodyCount(Direction.LEFT));
        assertFalse(player.hasSupportOverflow(Direction.LEFT));
        assertEquals(0, player.getUntrackedSupportContactCount(Direction.LEFT));

        // Introduce 9th distinct kinematic body (moving upward at vy = 4.0 m/s) sliding into left sensor at x=0.45..0.98
        // Initially spawned just outside sensor at x = 0.40..0.90 moving right at vx = 3.0 m/s into sensor, then stop X
        segments[8] = createKinematicPlatform(world, 0.42f, 3.19f, 0.50f, 0.08f, 3.0f, 4.0f, FixtureType.WALL);

        boolean reachedNine = false;
        for (int i = 0; i < 15; i++) {
            // Keep player vertically aligned with segments during contact check
            player.getBody().setTransform(player.getBody().getPosition().x, 2.7f, 0.0f);
            player.getBody().setLinearVelocity(0.0f, 0.0f);
            if (i == 2) {
                // After stepping into sensor [0.95..1.05], stop horizontal motion of 9th segment so it stays cleanly
                segments[8].setLinearVelocity(0.0f, 0.0f);
            }
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
            if (player.getContactCount(Direction.LEFT) == 9) {
                reachedNine = true;
                break;
            }
        }
        assertTrue(reachedNine, "9th distinct wall body must contact left sensor simultaneously with first 8");
        assertEquals(Player.MAX_SUPPORT_BODIES_PER_DIR, player.getTrackedBodyCount(Direction.LEFT));
        assertTrue(player.hasSupportOverflow(Direction.LEFT), "Overflow flag must indicate evicted contact");
        assertEquals(1, player.getUntrackedSupportContactCount(Direction.LEFT));
        assertSame(segments[8], player.getActiveSupportingBody(),
            "9th body must NOT be silently ignored; LRU policy must evict oldest and select 9th body");

        // Assign tangential velocity vy = 4.0 m/s to 9th body (keeping it inside y [2.1..3.3] for a step)
        segments[8].setLinearVelocity(0.0f, 4.0f);
        player.setMoveDirection(0.0f);
        player.getBody().setLinearVelocity(0.0f, 0.0f);
        player.onPrePhysicsStep(DT);
        assertEquals(Player.MAX_GROUND_ACCEL * DT, player.getBody().getLinearVelocity().y, EPS,
            "Movement impulse must track the newly selected 9th body's upward velocity");

        // Move 9th body leftward out of sensor contact while 1..8 remain
        segments[8].setLinearVelocity(-6.0f, 0.0f);
        boolean detachedNinth = false;
        for (int i = 0; i < 15; i++) {
            player.getBody().setLinearVelocity(0.0f, 0.0f);
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
            if (player.getContactCount(Direction.LEFT) == 8) {
                detachedNinth = true;
                break;
            }
        }
        assertTrue(detachedNinth, "9th body must detach cleanly");
        assertNotSame(segments[8], player.getActiveSupportingBody(),
            "Detached 9th body must NOT remain active support");
        assertNotNull(player.getActiveSupportingBody(),
            "Remaining tracked wall body must become active support");

        // Detach all bodies (move player rightward away from wall)
        player.getBody().setTransform(5.0f, 2.7f, 0.0f);
        player.onPrePhysicsStep(DT);
        world.step(DT, 8, 3);
        player.onPostPhysicsStep(DT);

        assertEquals(0, player.getContactCount(Direction.LEFT));
        assertEquals(0, player.getTrackedBodyCount(Direction.LEFT));
        assertFalse(player.hasSupportOverflow(Direction.LEFT));
        assertEquals(0, player.getUntrackedSupportContactCount(Direction.LEFT));
        assertNull(player.getActiveSupportingBody());

        world.dispose();
    }

    // 24. Support overflow fallback: if all tracked bodies detach leaving only an evicted contact, never use stale velocity
    @Test
    public void test24_SupportOverflow_EvictedOldestRemainsLast_SafeNullFallbackZeroStale() {
        World world = new World(new Vector2(0f, -9.81f), true);
        BodyFactory bf = new BodyFactory(world);
        Player player = Player.create(bf, 5.0f, 2.0f);

        // Create 9 kinematic platforms (B0..B8), B0 at vx=1.0, B1..B8 at vx=5.0
        Body[] platforms = new Body[9];
        for (int i = 0; i < 9; i++) {
            platforms[i] = createKinematicPlatform(world, 0f, 0f, 10f, 1f, (i == 0) ? 1.0f : 5.0f, 0.0f, FixtureType.GROUND);
        }

        // Contact B0..B7 (fills capacity 8)
        for (int i = 0; i < 8; i++) {
            player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR, platforms[i]);
        }
        assertFalse(player.hasSupportOverflow(Direction.DOWN));
        assertSame(platforms[7], player.getActiveSupportingBody());

        // Contact B8 -> B0 is evicted into untracked overflow; B8 becomes active
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR, platforms[8]);
        assertTrue(player.hasSupportOverflow(Direction.DOWN));
        assertEquals(1, player.getUntrackedSupportContactCount(Direction.DOWN));
        assertEquals(8, player.getTrackedBodyCount(Direction.DOWN));
        assertSame(platforms[8], player.getActiveSupportingBody());

        // Detach B1..B8 (leaving ONLY evicted B0 still in contact)
        for (int i = 1; i < 9; i++) {
            player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR, platforms[i]);
        }
        assertEquals(1, player.getContactCount(Direction.DOWN));
        assertTrue(player.isGrounded());
        assertEquals(0, player.getTrackedBodyCount(Direction.DOWN));
        assertEquals(1, player.getUntrackedSupportContactCount(Direction.DOWN));
        assertNull(player.getActiveSupportingBody(),
            "Safe overflow fallback must return null rather than any detached B1..B8 body");

        // Verify standing still uses 0.0f fallback, NOT stale 5.0 m/s from detached platforms
        player.getBody().setLinearVelocity(0.0f, 0.0f);
        player.setMoveDirection(0.0f);
        player.onPrePhysicsStep(DT);
        assertEquals(0.0f, player.getBody().getLinearVelocity().x, EPS,
            "Must NOT inherit stale velocity from detached B1..B8");

        // Finally detach B0 -> overflow and contacts cleanly drop to zero
        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR, platforms[0]);
        assertFalse(player.isGrounded());
        assertFalse(player.hasSupportOverflow(Direction.DOWN));
        assertEquals(0, player.getUntrackedSupportContactCount(Direction.DOWN));

        world.dispose();
    }
}
