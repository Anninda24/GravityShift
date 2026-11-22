package com.AnnindaRajDas.gravityshift.physics;

import com.AnnindaRajDas.gravityshift.entity.Player;
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
                notifyPlayer(fixtureA, typeA, isBegin);
            } else if (typeB.isPlayerSensor() && typeB.isCompatibleWith(typeA)) {
                notifyPlayer(fixtureB, typeB, isBegin);
            }
        }
    }

    private void notifyPlayer(Fixture sensorFixture, FixtureType sensorType, boolean isBegin) {
        if (sensorFixture.getBody() == null) {
            return;
        }

        Object bodyUserData = sensorFixture.getBody().getUserData();
        if (bodyUserData instanceof Player player && !player.isDisposed()) {
            if (isBegin) {
                player.onSensorContactBegin(sensorType);
            } else {
                player.onSensorContactEnd(sensorType);
            }
        }
    }
}
