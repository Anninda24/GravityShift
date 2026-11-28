package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.level.ChamberDefinition;
import com.AnnindaRajDas.gravityshift.level.ChamberDefinition.RectSpec;
import com.AnnindaRajDas.gravityshift.level.TmxChamberLoader;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.AnnindaRajDas.gravityshift.replay.ChamberReplayRunner;
import com.AnnindaRajDas.gravityshift.replay.ChamberReplayRunner.ReplayResult;
import com.AnnindaRajDas.gravityshift.replay.ReplayInputProvider;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Phase61Test {

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void testNoHiddenCentralPlatformInChamber1Replay() {
        ChamberDefinition c1 = TmxChamberLoader.loadChamber(1);
        // Shift UP from spawn (x=3, y=2) -> if central platform existed at y=5.5..6.1,
        // moving right at y=5.0 would be fine, but moving to x=10 and shifting UP would hit y=5.5.
        // Here we move to x=10, then shift UP: without central platform, player reaches ceiling (y~10.3)
        // and we verify failureReason reports y > 8.0 instead of being blocked at y < 5.5.
        ReplayInputProvider replay = new ReplayInputProvider()
                .addMove(0, 60, 1.0f, 0.0f)
                .addGravityShift(65, Direction.UP);
        ReplayResult res = ChamberReplayRunner.run(c1, replay, 150);
        assertFalse(res.reachedExit);
        assertNotNull(res.failureReason);
        assertTrue(res.failureReason.contains("y=10.") || res.failureReason.contains("y=9."),
                "Player should reach ceiling without hidden central platform, got: " + res.failureReason);
    }

    @Test
    void testAllEightChambersInitializeAndIsolateConsecutiveRuns() {
        for (int i = 1; i <= 8; i++) {
            ChamberDefinition def = TmxChamberLoader.loadChamber(i);
            ReplayInputProvider r1 = new ReplayInputProvider();
            ReplayInputProvider r2 = new ReplayInputProvider();

            ReplayResult res1 = ChamberReplayRunner.run(def, r1, 10);
            ReplayResult res2 = ChamberReplayRunner.run(def, r2, 10);

            assertEquals(i, res1.chamberNumber);
            assertEquals(res1.reachedExit, res2.reachedExit);
            assertEquals(res1.stepsTaken, res2.stepsTaken);
            assertEquals(res1.failureReason, res2.failureReason);
        }
    }

    @Test
    void testAllEightChambersHavePerimeterBoundaryGeometryAndNoHiddenPlatform() {
        for (int i = 1; i <= 8; i++) {
            ChamberDefinition def = TmxChamberLoader.loadChamber(i);
            boolean hasFloor = false;
            boolean hasCeiling = false;
            boolean hasLeftWall = false;
            boolean hasRightWall = false;
            for (RectSpec s : def.getSolids()) {
                if (s.y == 0f && s.x == 0f && s.width == 20f) hasFloor = true;
                if (s.y == 11f && s.x == 0f && s.width == 20f) hasCeiling = true;
                if (s.x == 0f && s.y == 1f) hasLeftWall = true;
                if (s.x == 19f && s.y == 1f) hasRightWall = true;
                assertFalse(s.x == 6f && s.y == 5.5f && s.width == 8f,
                        "Chamber " + i + " should not contain hardcoded central platform");
            }
            assertTrue(hasFloor, "Chamber " + i + " missing floor");
            assertTrue(hasCeiling, "Chamber " + i + " missing ceiling");
            assertTrue(hasLeftWall, "Chamber " + i + " missing left wall");
            assertTrue(hasRightWall, "Chamber " + i + " missing right wall");
        }
    }

    @Test
    void testChamber4AndChamber6PuzzleConnectionsAndInitialState() {
        ChamberDefinition c4 = TmxChamberLoader.loadChamber(4);
        assertEquals(1, c4.getCubes().size());
        assertEquals(1, c4.getPlates().size());
        assertEquals(1, c4.getDoors().size());
        assertEquals("door_1", c4.getPlates().get(0).channelId);
        assertEquals("door_1", c4.getDoors().get(0).channelId);
        assertEquals(3.0f, c4.getSpawnX(), 0.001f);
        assertEquals(2.0f, c4.getSpawnY(), 0.001f);

        ChamberDefinition c6 = TmxChamberLoader.loadChamber(6);
        assertEquals(1, c6.getCubes().size());
        assertEquals(2, c6.getPlates().size());
        assertEquals(2, c6.getDoors().size());
        assertEquals("door_1", c6.getPlates().get(0).channelId);
        assertEquals("door_2", c6.getPlates().get(1).channelId);
        assertEquals("door_1", c6.getDoors().get(0).channelId);
        assertEquals("door_2", c6.getDoors().get(1).channelId);
    }

    @Test
    void testSharedGeometryBuilderMatchesTmxSolidsInBox2DWorldWithoutLegacyPlatform() {
        for (int i = 1; i <= 8; i++) {
            ChamberDefinition def = TmxChamberLoader.loadChamber(i);
            PhysicsController physics = new PhysicsController(false);
            try {
                List<Body> bodies = TmxChamberLoader.buildCollisionGeometry(physics.getBodyFactory(), def);
                assertEquals(def.getSolids().size(), bodies.size(),
                        "Chamber " + i + " Box2D body count must match TMX solids");
                assertEquals(def.getSolids().size(), physics.getWorld().getBodyCount(),
                        "Chamber " + i + " World must contain no legacy default bodies");
                for (Body b : bodies) {
                    // Legacy central platform center was (10.0, 5.8)
                    boolean isLegacyPlatform = Math.abs(b.getPosition().x - 10.0f) < 0.01f
                            && Math.abs(b.getPosition().y - 5.8f) < 0.01f;
                    assertFalse(isLegacyPlatform, "Chamber " + i + " must not contain legacy central platform");
                }
            } finally {
                physics.dispose();
            }
        }
    }
}
