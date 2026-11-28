package com.AnnindaRajDas.gravityshift.entity;

import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;

/**
 * Chamber exit portal sensor.
 * Detects {@link FixtureType#PLAYER_BODY} contact and queues a single
 * completion callback processed outside any locked-world Box2D callback.
 */
public class ExitPortal extends Entity {

    private final float width;
    private final float height;
    private final Runnable onExitReached;

    private boolean playerInContact = false;
    private boolean exitDispatched = false;

    private ExitPortal(Body body, float width, float height, Runnable onExitReached) {
        super(body);
        this.width = width;
        this.height = height;
        this.onExitReached = onExitReached;
    }

    public static ExitPortal create(BodyFactory bodyFactory, float x, float y,
                                    float width, float height, Runnable onExitReached) {
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

        body.createFixture(fixtureDef).setUserData(FixtureType.EXIT_PORTAL);
        shape.dispose();

        return new ExitPortal(body, width, height, onExitReached);
    }

    public void onPlayerContactBegin() {
        if (!isDisposed()) {
            playerInContact = true;
        }
    }

    public void onPlayerContactEnd() {
        playerInContact = false;
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
        if (playerInContact && !exitDispatched) {
            exitDispatched = true;
            if (onExitReached != null) {
                onExitReached.run();
            }
        }
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}
