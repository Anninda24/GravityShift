package com.AnnindaRajDas.gravityshift.entity;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;

/**
 * Movable gravity-responsive puzzle cube.
 * Responds to all four cardinal gravity directions and clamps linear speed
 * to {@link GameConfig#MAX_PLAYER_SPEED} for stability.
 */
public class GravityCube extends Entity {

    public static final float DEFAULT_SIZE = 1.0f;

    private final float width;
    private final float height;

    private GravityCube(Body body, float width, float height) {
        super(body);
        this.width = width;
        this.height = height;
    }

    public static GravityCube create(BodyFactory bodyFactory, float x, float y) {
        return create(bodyFactory, x, y, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    public static GravityCube create(BodyFactory bodyFactory, float x, float y, float width, float height) {
        if (bodyFactory == null) {
            throw new IllegalArgumentException("BodyFactory cannot be null.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Cube dimensions must be positive.");
        }

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x + width * 0.5f, y + height * 0.5f);
        bodyDef.fixedRotation = true;

        Body body = bodyFactory.getWorld().createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width * 0.5f, height * 0.5f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.5f;
        fixtureDef.friction = 0.6f;
        fixtureDef.restitution = 0.0f;

        body.createFixture(fixtureDef).setUserData(FixtureType.GRAVITY_CUBE);
        shape.dispose();

        return new GravityCube(body, width, height);
    }

    @Override
    public void update(float delta) {
        // Physics-driven
    }

    @Override
    public void onPrePhysicsStep(float stepDelta) {
        if (isDisposed()) {
            return;
        }
        Vector2 vel = body.getLinearVelocity();
        float speedSq = vel.len2();
        float maxSpeed = 15.0f;
        if (speedSq > maxSpeed * maxSpeed) {
            float scale = maxSpeed / (float) Math.sqrt(speedSq);
            body.setLinearVelocity(vel.x * scale, vel.y * scale);
        }
    }

    public void wakeUp() {
        if (!isDisposed()) {
            body.setAwake(true);
        }
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}
