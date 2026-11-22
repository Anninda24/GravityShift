package com.AnnindaRajDas.gravityshift.physics;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;

/**
 * Factory class responsible for creating reusable Box2D bodies and fixtures.
 * Encapsulates the verbose definition and shape allocation logic, keeping Box2D
 * boilerplate out of gameplay, level, and rendering classes.
 */
public class BodyFactory {

    private final World world;

    public BodyFactory(World world) {
        if (world == null) {
            throw new IllegalArgumentException("World cannot be null.");
        }
        this.world = world;
    }

    /**
     * Creates a static rectangular physics body with default friction and restitution,
     * tagged as GROUND by default.
     *
     * @param x      bottom-left X coordinate in world meters
     * @param y      bottom-left Y coordinate in world meters
     * @param width  width in world meters
     * @param height height in world meters
     * @return the created static Box2D body
     */
    public Body createStaticBox(float x, float y, float width, float height) {
        return createStaticBox(x, y, width, height, GameConfig.DEFAULT_FRICTION, 0.0f, FixtureType.GROUND);
    }

    /**
     * Creates a static rectangular physics body with a specific fixture type.
     *
     * @param x           bottom-left X coordinate in world meters
     * @param y           bottom-left Y coordinate in world meters
     * @param width       width in world meters
     * @param height      height in world meters
     * @param fixtureType collision classification for the fixture
     * @return the created static Box2D body
     */
    public Body createStaticBox(float x, float y, float width, float height, FixtureType fixtureType) {
        return createStaticBox(x, y, width, height, GameConfig.DEFAULT_FRICTION, 0.0f, fixtureType);
    }

    /**
     * Creates a static rectangular physics body with custom friction and restitution,
     * tagged as GROUND by default.
     *
     * @param x           bottom-left X coordinate in world meters
     * @param y           bottom-left Y coordinate in world meters
     * @param width       width in world meters
     * @param height      height in world meters
     * @param friction    surface friction coefficient [0.0 - 1.0]
     * @param restitution bounciness coefficient [0.0 - 1.0]
     * @return the created static Box2D body
     */
    public Body createStaticBox(float x, float y, float width, float height, float friction, float restitution) {
        return createStaticBox(x, y, width, height, friction, restitution, FixtureType.GROUND);
    }

    /**
     * Creates a static rectangular physics body with custom friction, restitution, and fixture classification.
     *
     * @param x           bottom-left X coordinate in world meters
     * @param y           bottom-left Y coordinate in world meters
     * @param width       width in world meters
     * @param height      height in world meters
     * @param friction    surface friction coefficient [0.0 - 1.0]
     * @param restitution bounciness coefficient [0.0 - 1.0]
     * @param fixtureType collision classification for the fixture
     * @return the created static Box2D body
     */
    public Body createStaticBox(float x, float y, float width, float height,
                                float friction, float restitution, FixtureType fixtureType) {
        assertWorldNotLocked();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive. Given: " + width + "x" + height);
        }
        if (fixtureType == null) {
            throw new IllegalArgumentException("FixtureType cannot be null.");
        }

        // Box2D centers bodies at their origin; compute center from bottom-left
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.StaticBody;
        bodyDef.position.set(centerX, centerY);

        Body body = world.createBody(bodyDef);
        PolygonShape shape = new PolygonShape();
        try {
            // setAsBox takes half-width and half-height
            shape.setAsBox(width / 2.0f, height / 2.0f);

            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.shape = shape;
            fixtureDef.friction = friction;
            fixtureDef.restitution = restitution;

            Fixture fixture = body.createFixture(fixtureDef);
            fixture.setUserData(fixtureType);
            return body;
        } catch (RuntimeException e) {
            world.destroyBody(body);
            throw e;
        } finally {
            // Guarantee native shape disposal even if fixture creation fails
            shape.dispose();
        }
    }

    /**
     * Creates a dynamic rectangular physics body with default physical properties.
     *
     * @param x      bottom-left X coordinate in world meters
     * @param y      bottom-left Y coordinate in world meters
     * @param width  width in world meters
     * @param height height in world meters
     * @return the created dynamic Box2D body
     */
    public Body createDynamicBox(float x, float y, float width, float height) {
        return createDynamicBox(x, y, width, height,
                GameConfig.DEFAULT_DENSITY,
                GameConfig.DEFAULT_FRICTION,
                GameConfig.DEFAULT_RESTITUTION);
    }

    /**
     * Creates a dynamic rectangular physics body with custom physical properties.
     *
     * @param x           bottom-left X coordinate in world meters
     * @param y           bottom-left Y coordinate in world meters
     * @param width       width in world meters
     * @param height      height in world meters
     * @param density     mass density in kg/m^2
     * @param friction    surface friction coefficient
     * @param restitution bounciness coefficient
     * @return the created dynamic Box2D body
     */
    public Body createDynamicBox(float x, float y, float width, float height,
                                 float density, float friction, float restitution) {
        return createDynamicBox(x, y, width, height, density, friction, restitution, false);
    }

    /**
     * Creates a dynamic rectangular physics body with custom physical properties and fixed rotation.
     *
     * @param x             bottom-left X coordinate in world meters
     * @param y             bottom-left Y coordinate in world meters
     * @param width         width in world meters
     * @param height        height in world meters
     * @param density       mass density in kg/m^2
     * @param friction      surface friction coefficient
     * @param restitution   bounciness coefficient
     * @param fixedRotation whether to prevent body rotation (useful for player characters)
     * @return the created dynamic Box2D body
     */
    public Body createDynamicBox(float x, float y, float width, float height,
                                 float density, float friction, float restitution, boolean fixedRotation) {
        assertWorldNotLocked();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive. Given: " + width + "x" + height);
        }
        if (density <= 0) {
            throw new IllegalArgumentException("Density must be positive for dynamic bodies. Given: " + density);
        }

        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DynamicBody;
        bodyDef.position.set(centerX, centerY);
        bodyDef.fixedRotation = fixedRotation;

        Body body = world.createBody(bodyDef);
        PolygonShape shape = new PolygonShape();
        try {
            shape.setAsBox(width / 2.0f, height / 2.0f);

            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.shape = shape;
            fixtureDef.density = density;
            fixtureDef.friction = friction;
            fixtureDef.restitution = restitution;

            body.createFixture(fixtureDef);
            return body;
        } catch (RuntimeException e) {
            world.destroyBody(body);
            throw e;
        } finally {
            // Guarantee native shape disposal even if fixture creation fails
            shape.dispose();
        }
    }

    /**
     * Creates a dynamic Box2D body for the Player with a chamfered polygon torso fixture (PLAYER_BODY)
     * and four directional support sensors (PLAYER_BOTTOM_SENSOR, PLAYER_TOP_SENSOR,
     * PLAYER_LEFT_SENSOR, PLAYER_RIGHT_SENSOR).
     *
     * @param spawnX      bottom-left X coordinate in world meters
     * @param spawnY      bottom-left Y coordinate in world meters
     * @param width       player width in world meters
     * @param height      player height in world meters
     * @param density     mass density in kg/m^2
     * @param friction    surface friction coefficient
     * @param restitution bounciness coefficient
     * @return the created dynamic Box2D body containing torso and four directional sensors
     */
    public Body createPlayerBody(float spawnX, float spawnY, float width, float height,
                                 float density, float friction, float restitution) {
        return createPlayerBody(spawnX, spawnY, width, height, 0.1f, density, friction, restitution);
    }

    /**
     * Creates a dynamic Box2D body for the Player with a custom corner chamfer radius.
     *
     * @param spawnX      bottom-left X coordinate in world meters
     * @param spawnY      bottom-left Y coordinate in world meters
     * @param width       player width in world meters
     * @param height      player height in world meters
     * @param chamfer     corner chamfer offset in world meters
     * @param density     mass density in kg/m^2
     * @param friction    surface friction coefficient
     * @param restitution bounciness coefficient
     * @return the created dynamic Box2D body
     */
    public Body createPlayerBody(float spawnX, float spawnY, float width, float height, float chamfer,
                                 float density, float friction, float restitution) {
        assertWorldNotLocked();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive. Given: " + width + "x" + height);
        }
        if (density <= 0) {
            throw new IllegalArgumentException("Density must be positive for dynamic bodies. Given: " + density);
        }
        if (chamfer < 0.0f) {
            throw new IllegalArgumentException("Chamfer must be non-negative. Given: " + chamfer);
        }

        float centerX = spawnX + width / 2.0f;
        float centerY = spawnY + height / 2.0f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DynamicBody;
        bodyDef.position.set(centerX, centerY);
        bodyDef.fixedRotation = true;

        Body body = world.createBody(bodyDef);
        PolygonShape bodyShape = new PolygonShape();
        PolygonShape bottomShape = new PolygonShape();
        PolygonShape topShape = new PolygonShape();
        PolygonShape leftShape = new PolygonShape();
        PolygonShape rightShape = new PolygonShape();

        try {
            // 1. Solid torso fixture: 8-vertex chamfered polygon to eliminate seam snagging
            float halfW = width / 2.0f;
            float halfH = height / 2.0f;
            float c = Math.min(chamfer, Math.min(halfW / 2.0f, halfH / 2.0f));

            if (c > 0.0f) {
                float[] vertices = new float[] {
                    -halfW + c, -halfH,
                     halfW - c, -halfH,
                     halfW,     -halfH + c,
                     halfW,      halfH - c,
                     halfW - c,  halfH,
                    -halfW + c,  halfH,
                    -halfW,      halfH - c,
                    -halfW,     -halfH + c
                };
                bodyShape.set(vertices);
            } else {
                bodyShape.setAsBox(halfW, halfH);
            }

            FixtureDef bodyFixtureDef = new FixtureDef();
            bodyFixtureDef.shape = bodyShape;
            bodyFixtureDef.density = density;
            bodyFixtureDef.friction = friction;
            bodyFixtureDef.restitution = restitution;

            Fixture bodyFixture = body.createFixture(bodyFixtureDef);
            bodyFixture.setUserData(FixtureType.PLAYER_BODY);

            // Sensor geometry: 0.1m thickness, inset by 0.1m at corners to avoid crosstalk
            float horizontalSensorWidth = Math.max(0.1f, width - 0.2f);
            float verticalSensorHeight = Math.max(0.1f, height - 0.2f);
            float sensorThickness = 0.1f;

            // 2. Bottom sensor (centered at y = -height/2, extending 0.05m outward)
            bottomShape.setAsBox(horizontalSensorWidth / 2.0f, sensorThickness / 2.0f,
                    new Vector2(0.0f, -height / 2.0f), 0.0f);
            FixtureDef bottomDef = new FixtureDef();
            bottomDef.shape = bottomShape;
            bottomDef.isSensor = true;
            body.createFixture(bottomDef).setUserData(FixtureType.PLAYER_BOTTOM_SENSOR);

            // 3. Top sensor (centered at y = +height/2, extending 0.05m outward)
            topShape.setAsBox(horizontalSensorWidth / 2.0f, sensorThickness / 2.0f,
                    new Vector2(0.0f, height / 2.0f), 0.0f);
            FixtureDef topDef = new FixtureDef();
            topDef.shape = topShape;
            topDef.isSensor = true;
            body.createFixture(topDef).setUserData(FixtureType.PLAYER_TOP_SENSOR);

            // 4. Left sensor (centered at x = -width/2, extending 0.05m outward)
            leftShape.setAsBox(sensorThickness / 2.0f, verticalSensorHeight / 2.0f,
                    new Vector2(-width / 2.0f, 0.0f), 0.0f);
            FixtureDef leftDef = new FixtureDef();
            leftDef.shape = leftShape;
            leftDef.isSensor = true;
            body.createFixture(leftDef).setUserData(FixtureType.PLAYER_LEFT_SENSOR);

            // 5. Right sensor (centered at x = +width/2, extending 0.05m outward)
            rightShape.setAsBox(sensorThickness / 2.0f, verticalSensorHeight / 2.0f,
                    new Vector2(width / 2.0f, 0.0f), 0.0f);
            FixtureDef rightDef = new FixtureDef();
            rightDef.shape = rightShape;
            rightDef.isSensor = true;
            body.createFixture(rightDef).setUserData(FixtureType.PLAYER_RIGHT_SENSOR);

            return body;
        } catch (RuntimeException e) {
            world.destroyBody(body);
            throw e;
        } finally {
            bodyShape.dispose();
            bottomShape.dispose();
            topShape.dispose();
            leftShape.dispose();
            rightShape.dispose();
        }
    }

    private void assertWorldNotLocked() {
        if (world.isLocked()) {
            throw new IllegalStateException("Cannot create Box2D bodies while the simulation world is locked!");
        }
    }
}
