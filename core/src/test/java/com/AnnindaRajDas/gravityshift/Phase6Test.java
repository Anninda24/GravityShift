package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.level.ChamberDefinition;
import com.AnnindaRajDas.gravityshift.level.TmxChamberLoader;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.replay.ChamberReplayRunner;
import com.AnnindaRajDas.gravityshift.replay.ChamberReplayRunner.ReplayResult;
import com.AnnindaRajDas.gravityshift.replay.ReplayInputProvider;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class Phase6Test {

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void testAllEightChambersLoadWithValidSpawnAndExit() {
        for (int i = 1; i <= 8; i++) {
            ChamberDefinition def = TmxChamberLoader.loadChamber(i);
            assertEquals(i, def.getChamberNumber());
            assertNotNull(def.getName());
            assertNotNull(def.getExit());
            assertTrue(def.getExit().width > 0);
            assertTrue(def.getExit().height > 0);
            assertFalse(def.getSolids().isEmpty(), "Chamber " + i + " should have solid geometry");
        }
    }

    @Test
    void testMalformedTmxFailsClearly() {
        String missingExit = "<map version=\"1.10\" width=\"20\" height=\"12\" tilewidth=\"32\" tileheight=\"32\">"
                + "<objectgroup name=\"objects\">"
                + "<object id=\"1\" type=\"spawn\" x=\"64\" y=\"288\" width=\"32\" height=\"32\"/>"
                + "</objectgroup></map>";
        assertThrows(IllegalArgumentException.class, () ->
                TmxChamberLoader.parse(new ByteArrayInputStream(missingExit.getBytes(StandardCharsets.UTF_8)), 1));

        String missingChannel = "<map version=\"1.10\" width=\"20\" height=\"12\" tilewidth=\"32\" tileheight=\"32\">"
                + "<objectgroup name=\"objects\">"
                + "<object id=\"1\" type=\"spawn\" x=\"64\" y=\"288\" width=\"32\" height=\"32\"/>"
                + "<object id=\"2\" type=\"exit\" x=\"544\" y=\"288\" width=\"32\" height=\"32\"/>"
                + "<object id=\"3\" type=\"plate\" x=\"320\" y=\"320\" width=\"64\" height=\"20\"/>"
                + "</objectgroup></map>";
        assertThrows(IllegalArgumentException.class, () ->
                TmxChamberLoader.parse(new ByteArrayInputStream(missingChannel.getBytes(StandardCharsets.UTF_8)), 1));
    }

    @Test
    void testChamber1RecordedSolutionReplay() {
        ChamberDefinition c1 = TmxChamberLoader.loadChamber(1);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addGravityShift(10, Direction.RIGHT)
                .addMove(20, 75, 0.0f, 1.0f);
        ReplayResult res = ChamberReplayRunner.run(c1, replay, 400);
        assertTrue(res.reachedExit, "Chamber 1 replay failed: " + res.failureReason);
    }

    @Test
    void testChamber2RecordedSolutionReplay() {
        ChamberDefinition c2 = TmxChamberLoader.loadChamber(2);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addGravityShift(10, Direction.UP)
                .addMove(30, 110, 1.0f, 0.0f)
                .addGravityShift(110, Direction.DOWN)
                .addMove(130, 400, 1.0f, 0.0f);
        ReplayResult res = ChamberReplayRunner.run(c2, replay, 450);
        assertTrue(res.reachedExit, "Chamber 2 replay failed: " + res.failureReason);
    }

    @Test
    void testChamber3RecordedSolutionReplay() {
        ChamberDefinition c3 = TmxChamberLoader.loadChamber(3);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addGravityShift(10, Direction.UP)
                .addMove(30, 110, 1.0f, 0.0f)
                .addGravityShift(110, Direction.DOWN)
                .addMove(130, 400, 1.0f, 0.0f);
        ReplayResult res = ChamberReplayRunner.run(c3, replay, 450);
        assertTrue(res.reachedExit, "Chamber 3 replay failed: " + res.failureReason);
    }

    @Test
    void testChamber4RecordedSolutionReplay() {
        ChamberDefinition c4 = TmxChamberLoader.loadChamber(4);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addGravityShift(20, Direction.RIGHT)
                .addGravityShift(45, Direction.DOWN)
                .addMove(75, 450, 1.0f, 0.0f)
                .addJump(130);
        ReplayResult res = ChamberReplayRunner.run(c4, replay, 500);
        assertTrue(res.reachedExit, "Chamber 4 replay failed: " + res.failureReason);
    }

    @Test
    void testChamber5RecordedSolutionReplay() {
        ChamberDefinition c5 = TmxChamberLoader.loadChamber(5);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addGravityShift(10, Direction.RIGHT)
                .addGravityShift(45, Direction.DOWN)
                .addGravityShift(80, Direction.UP)
                .addMove(100, 145, 1.0f, 0.0f)
                .addGravityShift(145, Direction.DOWN)
                .addMove(165, 500, 1.0f, 0.0f);
        ReplayResult res = ChamberReplayRunner.run(c5, replay, 550);
        assertTrue(res.reachedExit, "Chamber 5 replay failed: " + res.failureReason);
    }

    @Test
    void testChamber6RecordedSolutionReplay() {
        ChamberDefinition c6 = TmxChamberLoader.loadChamber(6);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addMove(0, 15, -1.0f, 0.0f)
                .addGravityShift(20, Direction.RIGHT)
                .addGravityShift(66, Direction.DOWN)
                .addMove(85, 500, 1.0f, 0.0f)
                .addJump(130)
                .addJump(180);
        ReplayResult res = ChamberReplayRunner.run(c6, replay, 550);
        assertTrue(res.reachedExit, "Chamber 6 replay failed: " + res.failureReason);
    }

    @Test
    void testChamber7RecordedSolutionReplay() {
        ChamberDefinition c7 = TmxChamberLoader.loadChamber(7);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addGravityShift(15, Direction.UP)
                .addMove(30, 115, 1.0f, 0.0f)
                .addGravityShift(115, Direction.DOWN)
                .addGravityShift(155, Direction.RIGHT)
                .addGravityShift(195, Direction.DOWN)
                .addMove(215, 550, 1.0f, 0.0f)
                .addJump(255);
        ReplayResult res = ChamberReplayRunner.run(c7, replay, 600);
        assertTrue(res.reachedExit, "Chamber 7 replay failed: " + res.failureReason);
    }

    @Test
    void testChamber8RecordedSolutionReplay() {
        ChamberDefinition c8 = TmxChamberLoader.loadChamber(8);
        ReplayInputProvider replay = new ReplayInputProvider()
                .addGravityShift(15, Direction.UP)
                .addMove(30, 105, 1.0f, 0.0f)
                .addGravityShift(105, Direction.DOWN)
                .addGravityShift(190, Direction.RIGHT)
                .addGravityShift(230, Direction.DOWN)
                .addMove(245, 550, 1.0f, 0.0f)
                .addJump(280);
        ReplayResult res = ChamberReplayRunner.run(c8, replay, 600);
        assertTrue(res.reachedExit, "Chamber 8 replay failed: " + res.failureReason);
    }

    @Test
    void testReplayIsDeterministic() {
        ChamberDefinition c1 = TmxChamberLoader.loadChamber(1);
        ReplayInputProvider r1 = new ReplayInputProvider()
                .addGravityShift(10, Direction.RIGHT)
                .addMove(20, 260, 0.0f, 1.0f);
        ReplayInputProvider r2 = new ReplayInputProvider()
                .addGravityShift(10, Direction.RIGHT)
                .addMove(20, 260, 0.0f, 1.0f);

        ReplayResult res1 = ChamberReplayRunner.run(c1, r1, 400);
        ReplayResult res2 = ChamberReplayRunner.run(c1, r2, 400);
        assertEquals(res1.reachedExit, res2.reachedExit);
        assertEquals(res1.stepsTaken, res2.stepsTaken);
    }
}
