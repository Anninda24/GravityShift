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
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for Phase 3F.6:
 * World-Space Momentum Cap (15 m/s Limit on Gravity Transition).
 *
 * Covers:
 * 1-4. Velocities below 15 m/s (zero, horizontal, vertical, diagonal) remain unchanged.
 * 5. Exact 15 m/s boundary (axis-aligned & 9-12-15 diagonal) remains unchanged.
 * 6-9. Velocities above 15 m/s (horizontal, vertical, diagonal) clamp by vector magnitude
 *      and strictly preserve direction of travel.
 * 10-15. All cardinal gravity transitions (DOWN->LEFT->UP->RIGHT->DOWN), repeated transitions,
 *        and natural post-transition gravity acceleration.
 * 16-21. Regressions (impulse movement, jump velocity, coyote/buffer, physical departure,
 *        moving platforms, and Box2D world-lock guard).
 */
public class Phase3F6Test {

    private static final float EPS = 1e-4f;
    private static final float DT = GameConfig.TIME_STEP;
    private static final float MAX_SPEED = GameConfig.MAX_GRAVITY_SWITCH_SPEED; // 15.0f

    @BeforeAll
    public static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    private void switchGravity(GravityController gc, Player player, Direction nextDir) {
        gc.setGravityDirection(nextDir);
        player.onGravityDirectionChanged(nextDir);
    }

    // -------------------------------------------------------------------------
    // 1. VELOCITY BELOW THE 15 m/s CAP
    // -------------------------------------------------------------------------

    // 1. Zero velocity remains zero across gravity transitions
    @Test
    public void test01_ZeroVelocityRemainsZero() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        player.getBody().setLinearVelocity(0.0f, 0.0f);

        switchGravity(pc.getGravityController(), player, Direction.LEFT);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(0.0f, vel.x, EPS);
        assertEquals(0.0f, vel.y, EPS);

        pc.dispose();
    }

    // 2. A 5 m/s horizontal velocity remains unchanged
    @Test
    public void test02_HorizontalFiveMetersPerSecondUnchanged() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        player.getBody().setLinearVelocity(5.0f, 0.0f);

        switchGravity(pc.getGravityController(), player, Direction.LEFT);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(5.0f, vel.x, EPS);
        assertEquals(0.0f, vel.y, EPS);

        pc.dispose();
    }

    // 3. A 10 m/s vertical velocity remains unchanged
    @Test
    public void test03_VerticalTenMetersPerSecondUnchanged() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        player.getBody().setLinearVelocity(0.0f, -10.0f);

        switchGravity(pc.getGravityController(), player, Direction.RIGHT);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(0.0f, vel.x, EPS);
        assertEquals(-10.0f, vel.y, EPS);

        pc.dispose();
    }

    // 4. Diagonal velocity with magnitude below 15 m/s remains unchanged (6, -8 -> speed 10)
    @Test
    public void test04_DiagonalBelowCapUnchanged() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        player.getBody().setLinearVelocity(6.0f, -8.0f); // magnitude = 10.0 < 15.0

        switchGravity(pc.getGravityController(), player, Direction.UP);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(6.0f, vel.x, EPS);
        assertEquals(-8.0f, vel.y, EPS);
        assertEquals(10.0f, vel.len(), EPS);

        pc.dispose();
    }

    // -------------------------------------------------------------------------
    // 2. EXACT 15 m/s BOUNDARY
    // -------------------------------------------------------------------------

    // 5. Velocity vector with magnitude exactly 15 m/s remains unchanged
    @Test
    public void test05_ExactBoundaryFifteenMetersPerSecondUnchanged() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);

        // Axis-aligned exact 15 m/s
        player.getBody().setLinearVelocity(15.0f, 0.0f);
        switchGravity(pc.getGravityController(), player, Direction.LEFT);
        assertEquals(15.0f, player.getBody().getLinearVelocity().x, 0.0f);
        assertEquals(0.0f, player.getBody().getLinearVelocity().y, 0.0f);

        // Diagonal exact 15 m/s: (9, -12) -> sqrt(81 + 144) = 15.0
        player.getBody().setLinearVelocity(9.0f, -12.0f);
        switchGravity(pc.getGravityController(), player, Direction.UP);
        assertEquals(9.0f, player.getBody().getLinearVelocity().x, 0.0f);
        assertEquals(-12.0f, player.getBody().getLinearVelocity().y, 0.0f);

        pc.dispose();
    }

    // -------------------------------------------------------------------------
    // 3. VELOCITY ABOVE THE 15 m/s CAP
    // -------------------------------------------------------------------------

    // 6. A 20 m/s horizontal velocity is clamped to 15 m/s
    @Test
    public void test06_HorizontalTwentyMetersPerSecondClampedToFifteen() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        player.getBody().setLinearVelocity(-20.0f, 0.0f);

        switchGravity(pc.getGravityController(), player, Direction.UP);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(-15.0f, vel.x, EPS);
        assertEquals(0.0f, vel.y, EPS);
        assertTrue(vel.len() <= MAX_SPEED);

        pc.dispose();
    }

    // 7. A 20 m/s vertical velocity is clamped to 15 m/s
    @Test
    public void test07_VerticalTwentyMetersPerSecondClampedToFifteen() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        player.getBody().setLinearVelocity(0.0f, -20.0f);

        switchGravity(pc.getGravityController(), player, Direction.LEFT);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(0.0f, vel.x, EPS);
        assertEquals(-15.0f, vel.y, EPS);
        assertTrue(vel.len() <= MAX_SPEED);

        pc.dispose();
    }

    // 8. Diagonal velocity above cap clamps by vector magnitude, NOT per-axis clamping
    @Test
    public void test08_DiagonalAboveCapClampedByMagnitudeNotPerAxis() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);

        // (20, 20) -> speed = sqrt(800) ≈ 28.2843 m/s.
        // Clamped by magnitude: each component = 20 * (15 / 28.2843) = 15 / sqrt(2) ≈ 10.6066 m/s.
        // Per-axis clamping would wrongly yield (15, 15) with speed ≈ 21.213 m/s.
        player.getBody().setLinearVelocity(20.0f, 20.0f);

        switchGravity(pc.getGravityController(), player, Direction.RIGHT);

        Vector2 vel = player.getBody().getLinearVelocity();
        float expectedComp = 15.0f / (float) Math.sqrt(2.0);
        assertEquals(expectedComp, vel.x, EPS);
        assertEquals(expectedComp, vel.y, EPS);
        assertEquals(MAX_SPEED, vel.len(), EPS);
        assertTrue(vel.len() <= MAX_SPEED, "Magnitude after clamping must not exceed 15 m/s");

        pc.dispose();
    }

    // 9. Exact direction of travel is preserved after clamping
    @Test
    public void test09_DirectionOfTravelPreservedAfterClamping() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);

        // (18, -24) -> magnitude = 30.0 (2x a 9-12-15 triangle) -> must scale by 0.5 to (9, -12)
        float vxBefore = 18.0f;
        float vyBefore = -24.0f;
        double angleBefore = Math.atan2(vyBefore, vxBefore);
        player.getBody().setLinearVelocity(vxBefore, vyBefore);

        switchGravity(pc.getGravityController(), player, Direction.LEFT);

        Vector2 vel = player.getBody().getLinearVelocity();
        assertEquals(9.0f, vel.x, EPS);
        assertEquals(-12.0f, vel.y, EPS);
        assertEquals(MAX_SPEED, vel.len(), EPS);
        assertEquals(angleBefore, Math.atan2(vel.y, vel.x), 1e-6, "Travel angle must remain identical");

        pc.dispose();
    }

    // -------------------------------------------------------------------------
    // 4. GRAVITY TRANSITIONS & NATURAL ACCELERATION
    // -------------------------------------------------------------------------

    // 10. DOWN -> LEFT preserves (<15) and clamps (>15) world-space velocity
    @Test
    public void test10_TransitionDownToLeft_PreservesAndClamps() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);

        player.getBody().setLinearVelocity(4.0f, -9.0f);
        switchGravity(pc.getGravityController(), player, Direction.LEFT);
        assertEquals(4.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(-9.0f, player.getBody().getLinearVelocity().y, EPS);

        // Reset to DOWN, exceed cap, switch to LEFT
        switchGravity(pc.getGravityController(), player, Direction.DOWN);
        player.getBody().setLinearVelocity(0.0f, -25.0f);
        switchGravity(pc.getGravityController(), player, Direction.LEFT);
        assertEquals(0.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(-15.0f, player.getBody().getLinearVelocity().y, EPS);

        pc.dispose();
    }

    // 11. LEFT -> UP preserves (<15) and clamps (>15) world-space velocity
    @Test
    public void test11_TransitionLeftToUp_PreservesAndClamps() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        switchGravity(pc.getGravityController(), player, Direction.LEFT);

        player.getBody().setLinearVelocity(-8.0f, 5.0f);
        switchGravity(pc.getGravityController(), player, Direction.UP);
        assertEquals(-8.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(5.0f, player.getBody().getLinearVelocity().y, EPS);

        // Switch back to LEFT, set overspeed, switch to UP
        switchGravity(pc.getGravityController(), player, Direction.LEFT);
        player.getBody().setLinearVelocity(-30.0f, 0.0f);
        switchGravity(pc.getGravityController(), player, Direction.UP);
        assertEquals(-15.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(0.0f, player.getBody().getLinearVelocity().y, EPS);

        pc.dispose();
    }

    // 12. UP -> RIGHT preserves (<15) and clamps (>15) world-space velocity
    @Test
    public void test12_TransitionUpToRight_PreservesAndClamps() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        switchGravity(pc.getGravityController(), player, Direction.UP);

        player.getBody().setLinearVelocity(3.0f, 11.0f);
        switchGravity(pc.getGravityController(), player, Direction.RIGHT);
        assertEquals(3.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(11.0f, player.getBody().getLinearVelocity().y, EPS);

        switchGravity(pc.getGravityController(), player, Direction.UP);
        player.getBody().setLinearVelocity(12.0f, 16.0f); // magnitude 20.0 -> scale 0.75 -> (9, 12)
        switchGravity(pc.getGravityController(), player, Direction.RIGHT);
        assertEquals(9.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(12.0f, player.getBody().getLinearVelocity().y, EPS);

        pc.dispose();
    }

    // 13. RIGHT -> DOWN preserves (<15) and clamps (>15) world-space velocity
    @Test
    public void test13_TransitionRightToDown_PreservesAndClamps() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 5.0f);
        switchGravity(pc.getGravityController(), player, Direction.RIGHT);

        player.getBody().setLinearVelocity(7.0f, -4.0f);
        switchGravity(pc.getGravityController(), player, Direction.DOWN);
        assertEquals(7.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(-4.0f, player.getBody().getLinearVelocity().y, EPS);

        switchGravity(pc.getGravityController(), player, Direction.RIGHT);
        player.getBody().setLinearVelocity(24.0f, 0.0f);
        switchGravity(pc.getGravityController(), player, Direction.DOWN);
        assertEquals(15.0f, player.getBody().getLinearVelocity().x, EPS);
        assertEquals(0.0f, player.getBody().getLinearVelocity().y, EPS);

        pc.dispose();
    }

    // 14. Repeated gravity transitions cannot increase velocity beyond 15 m/s by themselves
    @Test
    public void test14_RepeatedGravityTransitionsNeverExceedCap() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 10.0f, 6.0f);

        // High diagonal velocity (> 15 m/s)
        player.getBody().setLinearVelocity(21.0f, 17.0f);

        Direction[] cycle = { Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN };
        float clampedMag = -1.0f;
        for (int round = 0; round < 5; round++) {
            for (Direction dir : cycle) {
                switchGravity(pc.getGravityController(), player, dir);
                float speed = player.getBody().getLinearVelocity().len();
                assertTrue(speed <= MAX_SPEED, "Speed after transition must remain <= 15 m/s, was " + speed);
                if (clampedMag < 0.0f) {
                    clampedMag = speed;
                    assertEquals(MAX_SPEED, clampedMag, EPS);
                } else {
                    assertEquals(clampedMag, speed, 1e-3f, "Repeated transitions must not drift speed");
                }
            }
        }

        pc.dispose();
    }

    // 15. Normal gravity acceleration after a transition still works (including accelerating freely after switch)
    @Test
    public void test15_NormalGravityAccelerationWorksAfterTransition() {
        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);

        Player player = Player.create(pc.getBodyFactory(), 3.0f, 8.0f);
        em.addEntity(player);

        // Pre-transition overspeed leftward, switch gravity LEFT -> RIGHT
        switchGravity(pc.getGravityController(), player, Direction.LEFT);
        player.getBody().setLinearVelocity(-20.0f, 0.0f);
        switchGravity(pc.getGravityController(), player, Direction.RIGHT);
        assertEquals(-15.0f, player.getBody().getLinearVelocity().x, EPS);

        // Advance simulation: +9.81 m/s^2 rightward gravity must naturally accelerate vx toward +X
        for (int i = 0; i < 30; i++) { // 0.5s under 9.81 m/s^2 -> +4.9 m/s
            pc.update(DT);
        }
        assertTrue(player.getBody().getLinearVelocity().x > -11.0f,
            "New gravity must naturally accelerate player along +X after transition clamp");

        pc.dispose();
    }

    // -------------------------------------------------------------------------
    // 5. REGRESSIONS & ARCHITECTURE INTEGRITY
    // -------------------------------------------------------------------------

    // 16. Impulse-based movement remains functional (not hijacked into an always-on 15 m/s clamp)
    @Test
    public void test16_ImpulseBasedMovementRemainsFunctional() {
        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);

        Player player = Player.create(pc.getBodyFactory(), 5.0f, 1.565f);
        em.addEntity(player);

        for (int i = 0; i < 20; i++) pc.update(DT);
        assertTrue(player.isGrounded());

        player.setMoveDirection(1.0f);
        for (int i = 0; i < 15; i++) pc.update(DT);

        assertTrue(player.getBody().getLinearVelocity().x > 4.5f,
            "Impulse-based grounded acceleration must smoothly approach 5.0 m/s");

        pc.dispose();
    }

    // 17. Jump launch velocity is not incorrectly clamped by ordinary movement
    @Test
    public void test17_JumpLaunchVelocityNotAlteredByMovement() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 2.0f);
        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.setMoveDirection(1.0f);

        assertTrue(player.jump());
        player.onPrePhysicsStep(DT);

        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);
        assertTrue(player.getBody().getLinearVelocity().x > 0.0f);

        pc.dispose();
    }

    // 18. Coyote time and jump buffering remain correct across gravity-switch cap
    @Test
    public void test18_CoyoteTimeAndJumpBufferingRemainCorrect() {
        PhysicsController pc = new PhysicsController();
        EntityManager em = new EntityManager();
        pc.setStepListener(em);

        Player player = Player.create(pc.getBodyFactory(), 5.0f, 1.565f);
        em.addEntity(player);
        player.getBody().setLinearVelocity(0f, -2.0f);

        assertFalse(player.jump()); // buffered
        assertTrue(player.isJumpBuffered());

        for (int i = 0; i < 6; i++) {
            pc.update(DT);
        }
        assertTrue(player.isGrounded());
        assertTrue(player.isJumpConsumed(), "Step 6 buffered jump must execute");
        assertEquals(Player.JUMP_VELOCITY, player.getBody().getLinearVelocity().y, EPS);

        pc.dispose();
    }

    // 19. Complete physical-departure protection remains intact
    @Test
    public void test19_CompletePhysicalDepartureProtectionRemainsIntact() {
        PhysicsController pc = new PhysicsController();
        Player player = Player.create(pc.getBodyFactory(), 5.0f, 2.0f);

        player.onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
        player.onSensorContactBegin(FixtureType.PLAYER_LEFT_SENSOR);
        assertTrue(player.jump());
        assertFalse(player.hasLeftSupportSinceLaunch());

        player.onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
        assertFalse(player.hasLeftSupportSinceLaunch(),
            "Losing bottom sensor while left sensor remains must not reset departure");

        player.onSensorContactEnd(FixtureType.PLAYER_LEFT_SENSOR);
        assertTrue(player.hasLeftSupportSinceLaunch());

        pc.dispose();
    }

    // 20. Existing moving-platform support behavior remains correct
    @Test
    public void test20_MovingPlatformSupportBehaviorRemainsCorrect() {
        World world = new World(new Vector2(0f, -9.81f), true);
        world.setContactListener(new GameContactListener());
        BodyFactory bf = new BodyFactory(world);

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(10.0f, 0.5f);
        Body platform = world.createBody(bd);
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(8.0f, 0.5f);
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.friction = 0.4f;
        platform.createFixture(fd).setUserData(FixtureType.GROUND);
        shape.dispose();
        platform.setLinearVelocity(3.0f, 0.0f);

        Player player = Player.create(bf, 5.0f, 1.0f);
        player.setMoveDirection(0.0f);

        for (int i = 0; i < 20; i++) {
            player.onPrePhysicsStep(DT);
            world.step(DT, 8, 3);
            player.onPostPhysicsStep(DT);
        }

        assertSame(platform, player.getActiveSupportingBody());
        assertEquals(3.0f, player.getBody().getLinearVelocity().x, 0.1f,
            "Player must ride moving platform cleanly at 3.0 m/s without sliding");

        world.dispose();
    }

    // 21. Box2D world-lock safety & non-player dynamic body clamping
    @Test
    public void test21_WorldLockSafetyAndDynamicCubeClamping() {
        World world = new World(new Vector2(0f, -9.81f), true);
        GravityController gc = new GravityController(world);
        BodyFactory bf = new BodyFactory(world);

        // Create a sleeping dynamic crate and a high-speed moving dynamic crate
        bf.createStaticBox(0f, 0f, 20f, 1f, FixtureType.GROUND);
        Body sleepingCrate = bf.createDynamicBox(2f, 1.0f, 1f, 1f, 1.0f, 0.2f, 0.0f, true);
        sleepingCrate.setAwake(false);

        Body crate = bf.createDynamicBox(5f, 1.0f, 1f, 1f, 1.0f, 0.2f, 0.0f, true);
        crate.setLinearVelocity(18.0f, -24.0f); // speed 30 m/s

        // GravityController clamps all dynamic bodies & wakes sleeping bodies safely
        gc.setGravityDirection(Direction.LEFT);
        assertTrue(sleepingCrate.isAwake(), "Sleeping dynamic body must wake after gravity switch");
        assertEquals(9.0f, crate.getLinearVelocity().x, EPS);
        assertEquals(-12.0f, crate.getLinearVelocity().y, EPS);

        // Verify IllegalStateException when attempting to switch gravity during locked World.step()
        final boolean[] threwOnLockedWorld = { false };
        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                try {
                    gc.setGravityDirection(Direction.UP);
                } catch (IllegalStateException expected) {
                    threwOnLockedWorld[0] = true;
                }
            }
            @Override public void endContact(Contact contact) {}
            @Override public void preSolve(Contact contact, Manifold oldManifold) {}
            @Override public void postSolve(Contact contact, ContactImpulse impulse) {}
        });

        crate.setTransform(5.5f, 1.45f, 0f);
        world.step(DT, 8, 3);
        assertTrue(threwOnLockedWorld[0], "World-lock guard must reject gravity switch inside World.step()");

        world.dispose();
    }
}
