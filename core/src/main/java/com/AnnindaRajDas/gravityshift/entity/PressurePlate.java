package com.AnnindaRajDas.gravityshift.entity;

import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerBus;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.utils.Array;

/**
 * Physics-driven pressure plate that tracks valid contacting fixtures
 * (Player torso or GravityCube) and publishes state transitions to {@link TriggerBus}.
 */
public class PressurePlate extends Entity {

    private final float width;
    private final float height;
    private final String channelId;
    private final TriggerBus triggerBus;
    private final Array<Fixture> activeFixtures = new Array<>(false, 8);
    private boolean pressed = false;

    private PressurePlate(Body body, float width, float height, String channelId, TriggerBus triggerBus) {
        super(body);
        this.width = width;
        this.height = height;
        this.channelId = channelId;
        this.triggerBus = triggerBus;
    }

    public static PressurePlate create(BodyFactory bodyFactory, float x, float y,
                                       float width, float height, String channelId, TriggerBus triggerBus) {
        if (bodyFactory == null) {
            throw new IllegalArgumentException("BodyFactory cannot be null.");
        }
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null or empty.");
        }
        if (triggerBus == null) {
            throw new IllegalArgumentException("TriggerBus cannot be null.");
        }

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x + width * 0.5f, y + height * 0.5f);

        Body body = bodyFactory.getWorld().createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width * 0.5f, height * 0.5f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true;

        body.createFixture(fixtureDef).setUserData(FixtureType.PRESSURE_PLATE_SENSOR);
        shape.dispose();

        return new PressurePlate(body, width, height, channelId, triggerBus);
    }

    public void onSensorContactBegin(Fixture otherFixture) {
        if (isDisposed() || otherFixture == null) {
            return;
        }
        if (!activeFixtures.contains(otherFixture, true)) {
            activeFixtures.add(otherFixture);
        }
    }

    public void onSensorContactEnd(Fixture otherFixture) {
        if (otherFixture == null) {
            return;
        }
        activeFixtures.removeValue(otherFixture, true);
    }

    @Override
    public void update(float delta) {
        // State evaluation occurs deterministically in onPrePhysicsStep / onPostPhysicsStep
    }

    @Override
    public void onPrePhysicsStep(float stepDelta) {
        if (isDisposed()) {
            return;
        }
        pruneInvalidFixtures();
        evaluateState();
    }

    @Override
    public void onPostPhysicsStep(float stepDelta) {
        if (isDisposed()) {
            return;
        }
        evaluateState();
    }

    private void pruneInvalidFixtures() {
        for (int i = activeFixtures.size - 1; i >= 0; i--) {
            Fixture f = activeFixtures.get(i);
            Body b = f.getBody();
            if (b == null || (b.getUserData() instanceof Entity e && e.isDisposed())) {
                activeFixtures.removeIndex(i);
            }
        }
    }

    private void evaluateState() {
        boolean shouldBePressed = activeFixtures.size > 0;
        if (shouldBePressed != pressed) {
            pressed = shouldBePressed;
            if (com.badlogic.gdx.Gdx.app != null) {
                com.badlogic.gdx.Gdx.app.log("Phase5Diag",
                        "PressurePlate[" + channelId + "] state -> " + pressed + " contacts=" + activeFixtures.size);
            }
            triggerBus.publish(channelId, pressed);
        }
    }

    public boolean isPressed() {
        return pressed;
    }

    public int getActiveContactCount() {
        return activeFixtures.size;
    }

    public String getChannelId() {
        return channelId;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    @Override
    public void dispose() {
        activeFixtures.clear();
        super.dispose();
    }
}
