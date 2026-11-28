package com.AnnindaRajDas.gravityshift.physics;

import com.AnnindaRajDas.gravityshift.entity.ExitPortal;
import com.AnnindaRajDas.gravityshift.entity.LaserHazard;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.entity.PressurePlate;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;

/**
 * Handles Box2D collision callbacks to manage directional support contact states for entities.
 *
 * Enforces strict directional compatibility between player sensors (BOTTOM, TOP, LEFT, RIGHT)
 * and supporting surface orientations (GROUND, CEILING, WALL).
 */
public class GameContactListener implements ContactListener {

    @Override
    public void beginContact(Contact contact) {
        handleContact(contact, true);
    }

    @Override
    public void endContact(Contact contact) {
        handleContact(contact, false);
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
        // No pre-solve logic required for sensors
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
        // No post-solve impulse handling required for sensors
    }

    /**
     * Inspects fixture pair and dispatches support callbacks only when sensor and surface
     * are strictly compatible.
     *
     * @param contact the Box2D contact event
     * @param isBegin true for beginContact, false for endContact
     */
    private void handleContact(Contact contact, boolean isBegin) {
        Fixture fixtureA = contact.getFixtureA();
        Fixture fixtureB = contact.getFixtureB();

        if (fixtureA == null || fixtureB == null) {
            return;
        }

        Object dataA = fixtureA.getUserData();
        Object dataB = fixtureB.getUserData();

        if (dataA instanceof FixtureType typeA && dataB instanceof FixtureType typeB) {
            if (typeA.isPlayerSensor() && typeA.isCompatibleWith(typeB)) {
                notifyPlayer(fixtureA, typeA, fixtureB, isBegin);
            } else if (typeB.isPlayerSensor() && typeB.isCompatibleWith(typeA)) {
                notifyPlayer(fixtureB, typeB, fixtureA, isBegin);
            }

            if (typeA == FixtureType.PRESSURE_PLATE_SENSOR && isPlateActivator(typeB)) {
                notifyPressurePlate(fixtureA, fixtureB, isBegin);
            } else if (typeB == FixtureType.PRESSURE_PLATE_SENSOR && isPlateActivator(typeA)) {
                notifyPressurePlate(fixtureB, fixtureA, isBegin);
            }

            if (typeA == FixtureType.HAZARD_LASER && typeB == FixtureType.PLAYER_BODY) {
                notifyLaserHazard(fixtureA, isBegin);
            } else if (typeB == FixtureType.HAZARD_LASER && typeA == FixtureType.PLAYER_BODY) {
                notifyLaserHazard(fixtureB, isBegin);
            }

            if (typeA == FixtureType.EXIT_PORTAL && typeB == FixtureType.PLAYER_BODY) {
                notifyExitPortal(fixtureA, isBegin);
            } else if (typeB == FixtureType.EXIT_PORTAL && typeA == FixtureType.PLAYER_BODY) {
                notifyExitPortal(fixtureB, isBegin);
            }
        }
    }

    private static boolean isPlateActivator(FixtureType type) {
        return type == FixtureType.PLAYER_BODY || type == FixtureType.GRAVITY_CUBE;
    }

    private void notifyPressurePlate(Fixture plateFixture, Fixture activatorFixture, boolean isBegin) {
        if (plateFixture.getBody() == null) {
            return;
        }
        Object bodyUserData = plateFixture.getBody().getUserData();
        if (bodyUserData instanceof PressurePlate plate && !plate.isDisposed()) {
            if (com.badlogic.gdx.Gdx.app != null) {
                com.badlogic.gdx.Gdx.app.log("Phase5Diag",
                        "Contact " + (isBegin ? "BEGIN" : "END") + " plate=" + plate.getChannelId()
                        + " activator=" + activatorFixture.getUserData());
            }
            if (isBegin) {
                plate.onSensorContactBegin(activatorFixture);
            } else {
                plate.onSensorContactEnd(activatorFixture);
            }
        }
    }

    private void notifyLaserHazard(Fixture hazardFixture, boolean isBegin) {
        if (hazardFixture.getBody() == null) {
            return;
        }
        Object bodyUserData = hazardFixture.getBody().getUserData();
        if (bodyUserData instanceof LaserHazard hazard && !hazard.isDisposed()) {
            if (isBegin) {
                hazard.onPlayerContactBegin();
            } else {
                hazard.onPlayerContactEnd();
            }
        }
    }

    private void notifyExitPortal(Fixture exitFixture, boolean isBegin) {
        if (exitFixture.getBody() == null) {
            return;
        }
        Object bodyUserData = exitFixture.getBody().getUserData();
        if (bodyUserData instanceof ExitPortal portal && !portal.isDisposed()) {
            if (isBegin) {
                portal.onPlayerContactBegin();
            } else {
                portal.onPlayerContactEnd();
            }
        }
    }

    private void notifyPlayer(Fixture sensorFixture, FixtureType sensorType, Fixture supportFixture, boolean isBegin) {
        if (sensorFixture.getBody() == null) {
            return;
        }

        Object bodyUserData = sensorFixture.getBody().getUserData();
        if (bodyUserData instanceof Player player && !player.isDisposed()) {
            Body supportBody = (supportFixture != null) ? supportFixture.getBody() : null;
            if (isBegin) {
                player.onSensorContactBegin(sensorType, supportBody);
            } else {
                player.onSensorContactEnd(sensorType, supportBody);
            }
        }
    }
}
