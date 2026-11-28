package com.AnnindaRajDas.gravityshift.replay;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
import com.AnnindaRajDas.gravityshift.entity.ExitPortal;
import com.AnnindaRajDas.gravityshift.entity.GravityCube;
import com.AnnindaRajDas.gravityshift.entity.LaserHazard;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.entity.PressurePlate;
import com.AnnindaRajDas.gravityshift.entity.SlidingDoor;
import com.AnnindaRajDas.gravityshift.input.PlayerInputController;
import com.AnnindaRajDas.gravityshift.level.ChamberDefinition;
import com.AnnindaRajDas.gravityshift.level.ChamberDefinition.*;
import com.AnnindaRajDas.gravityshift.level.TmxChamberLoader;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.physics.GameContactListener;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerBus;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;

/**
 * Runs a deterministic fixed-timestep recorded solution replay for a {@link ChamberDefinition}.
 */
public final class ChamberReplayRunner {

    public static final class ReplayResult {
        public final int chamberNumber;
        public final boolean reachedExit;
        public final boolean died;
        public final int stepsTaken;
        public final String failureReason;

        public ReplayResult(int chamberNumber, boolean reachedExit, boolean died,
                            int stepsTaken, String failureReason) {
            this.chamberNumber = chamberNumber;
            this.reachedExit = reachedExit;
            this.died = died;
            this.stepsTaken = stepsTaken;
            this.failureReason = failureReason;
        }
    }

    private ChamberReplayRunner() {}

    public static ReplayResult run(ChamberDefinition chamber, ReplayInputProvider replay, int maxSteps) {
        PhysicsController physics = new PhysicsController(false);
        EntityManager entities = new EntityManager();
        physics.setStepListener(entities);
        TriggerBus bus = new TriggerBus();

        final boolean[] exitReached = { false };
        final boolean[] playerDied = { false };

        // Build chamber solids from TMX using shared builder
        TmxChamberLoader.buildCollisionGeometry(physics.getBodyFactory(), chamber);

        Player player = Player.create(physics.getBodyFactory(), chamber.getSpawnX(), chamber.getSpawnY());
        entities.addEntity(player);

        RectSpec ex = chamber.getExit();
        ExitPortal exit = ExitPortal.create(
                physics.getBodyFactory(), ex.x, ex.y, ex.width, ex.height, () -> exitReached[0] = true);
        entities.addEntity(exit);

        for (CubeSpec c : chamber.getCubes()) {
            entities.addEntity(GravityCube.create(physics.getBodyFactory(), c.x, c.y));
        }
        for (PlateSpec p : chamber.getPlates()) {
            entities.addEntity(PressurePlate.create(
                    physics.getBodyFactory(), p.x, p.y, p.width, p.height, p.channelId, bus));
        }
        for (DoorSpec d : chamber.getDoors()) {
            entities.addEntity(SlidingDoor.create(
                    physics.getBodyFactory(), d.x, d.y, d.width, d.height, d.channelId, bus));
        }
        for (HazardSpec h : chamber.getHazards()) {
            entities.addEntity(LaserHazard.create(
                    physics.getBodyFactory(), h.x, h.y, h.width, h.height,
                    h.channelId, bus, h.initiallyActive, () -> playerDied[0] = true));
        }

        PlayerInputController inputController = new PlayerInputController(player, replay);

        try {
            for (int step = 0; step < maxSteps; step++) {
                replay.setStep(step);
                inputController.update();

                Direction reqGrav = inputController.consumeGravityRequest(
                        physics.getGravityController().getGravityDirection());
                if (reqGrav != null) {
                    physics.getGravityController().setGravityDirection(reqGrav);
                    player.onGravityDirectionChanged(reqGrav);
                }

                entities.update(GameConfig.TIME_STEP);
                physics.update(GameConfig.TIME_STEP);

                if (playerDied[0]) {
                    return new ReplayResult(chamber.getChamberNumber(), false, true, step + 1,
                            "Player died at step " + (step + 1));
                }
                if (exitReached[0]) {
                    return new ReplayResult(chamber.getChamberNumber(), true, false, step + 1, null);
                }
            }
            return new ReplayResult(chamber.getChamberNumber(), false, false, maxSteps,
                    "Did not reach exit within " + maxSteps + " steps (player at x="
                            + player.getX() + ", y=" + player.getY() + ")");
        } finally {
            bus.clear();
            entities.dispose();
            physics.dispose();
        }
    }
}
