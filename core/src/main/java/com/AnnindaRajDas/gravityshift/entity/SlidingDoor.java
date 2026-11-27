package com.AnnindaRajDas.gravityshift.entity;

import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerBus;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerListener;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;

/**
 * Logic-driven sliding door controlled via {@link TriggerBus}.
 * Collision state transitions (solid vs sensor) are applied deterministically
 * in {@link #onPrePhysicsStep(float)} outside any locked-world callback.
 */
public class SlidingDoor extends Entity implements TriggerListener {

    private final float width;
    private final float height;
    private final String channelId;
    private final TriggerBus triggerBus;
    private final Fixture doorFixture;

    private boolean targetOpen = false;
    private boolean open = false;

    private SlidingDoor(Body body, Fixture doorFixture, float width, float height,
                        String channelId, TriggerBus triggerBus) {
        super(body);
        this.doorFixture = doorFixture;
        this.width = width;
        this.height = height;
        this.channelId = channelId;
        this.triggerBus = triggerBus;
        this.triggerBus.subscribe(channelId, this);
    }

    public static SlidingDoor create(BodyFactory bodyFactory, float x, float y,
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
        fixtureDef.friction = 0.2f;
        fixtureDef.isSensor = false;

        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData(FixtureType.SLIDING_DOOR);
        shape.dispose();

        return new SlidingDoor(body, fixture, width, height, channelId, triggerBus);
    }

    @Override
    public void onTriggerChanged(String channelId, boolean active) {
        if (isDisposed() || !this.channelId.equals(channelId)) {
            return;
        }
        this.targetOpen = active;
    }

    @Override
    public void update(float delta) {
        // State transition applied in onPrePhysicsStep
    }

    @Override
    public void onPrePhysicsStep(float stepDelta) {
        if (isDisposed()) {
            return;
        }
        if (targetOpen != open) {
            open = targetOpen;
            doorFixture.setSensor(open);
        }
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isTargetOpen() {
        return targetOpen;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    @Override
    public void dispose() {
        triggerBus.unsubscribe(channelId, this);
        super.dispose();
    }
}
