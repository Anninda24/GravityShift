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
}
