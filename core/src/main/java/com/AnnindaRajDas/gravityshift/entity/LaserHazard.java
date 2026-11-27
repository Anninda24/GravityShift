package com.AnnindaRajDas.gravityshift.entity;

import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerBus;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerListener;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;

/**
 * Lethal laser hazard sensor.
 * Contact with an active laser queues a single death notification processed
 * outside any locked-world Box2D callback.
 */
public class LaserHazard extends Entity implements TriggerListener {

    private final float width;
    private final float height;
    private final String channelId;
    private final TriggerBus triggerBus;
    private final Runnable onPlayerKilled;

    private boolean active;
    private boolean playerInContact = false;
    private boolean killDispatchedForContact = false;

    private LaserHazard(Body body, float width, float height,
                        String channelId, TriggerBus triggerBus,
                        boolean initiallyActive, Runnable onPlayerKilled) {
        super(body);
        this.width = width;
        this.height = height;
        this.channelId = channelId;
        this.triggerBus = triggerBus;
        this.active = initiallyActive;
        this.onPlayerKilled = onPlayerKilled;
        if (triggerBus != null && channelId != null && !channelId.isEmpty()) {
            triggerBus.subscribe(channelId, this);
        }
    }

    public static LaserHazard create(BodyFactory bodyFactory, float x, float y,
                                     float width, float height, Runnable onPlayerKilled) {
        return create(bodyFactory, x, y, width, height, null, null, true, onPlayerKilled);
    }

    public static LaserHazard create(BodyFactory bodyFactory, float x, float y,
                                     float width, float height,
                                     String channelId, TriggerBus triggerBus,
                                     boolean initiallyActive, Runnable onPlayerKilled) {
        if (bodyFactory == null) {
            throw new IllegalArgumentException("BodyFactory cannot be null.");
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

        body.createFixture(fixtureDef).setUserData(FixtureType.HAZARD_LASER);
        shape.dispose();

        return new LaserHazard(body, width, height, channelId, triggerBus, initiallyActive, onPlayerKilled);
    }

    public void onPlayerContactBegin() {
        if (isDisposed()) {
            return;
        }
        playerInContact = true;
    }

    public void onPlayerContactEnd() {
        playerInContact = false;
        killDispatchedForContact = false;
    }

    @Override
    public void onTriggerChanged(String channelId, boolean active) {
        if (isDisposed() || this.channelId == null || !this.channelId.equals(channelId)) {
            return;
        }
        this.active = active;
    }

    @Override
    public void update(float delta) {
        // Processed in onPrePhysicsStep
    }

    @Override
    public void onPrePhysicsStep(float stepDelta) {
        if (isDisposed()) {
            return;
        }
        if (active && playerInContact && !killDispatchedForContact) {
            killDispatchedForContact = true;
            if (onPlayerKilled != null) {
                onPlayerKilled.run();
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    @Override
    public void dispose() {
        if (triggerBus != null && channelId != null && !channelId.isEmpty()) {
            triggerBus.unsubscribe(channelId, this);
        }
        super.dispose();
    }
}
