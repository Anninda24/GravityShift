package com.AnnindaRajDas.gravityshift.entity;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.physics.BodyFactory;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Concrete player entity representing the character navigated through puzzle chambers.
 *
 * Demonstrates:
 * - Inheritance: Subclasses Entity and fulfills the lifecycle contract.
 * - Encapsulation: Protects player dimensions, physical properties, movement inputs,
 *   active support direction, and directional contact counters.
 * - Composition: Owns its Box2D dynamic body with locked rotation and directional sensors.
 */
public class Player extends Entity {

    // Default Dimensions in world meters (~human scale)
    public static final float DEFAULT_WIDTH = 0.8f;
    public static final float DEFAULT_HEIGHT = 1.4f;

    // Physical Material Parameters
    public static final float DENSITY = 1.2f;
    public static final float FRICTION = 0.4f;
    public static final float RESTITUTION = 0.0f; // No bounciness for responsive platforming

    // Movement Parameters
    public static final float MOVE_SPEED = 5.0f;     // Horizontal walking/running speed in m/s
    public static final float JUMP_VELOCITY = 6.5f;  // Initial vertical jump launch velocity in m/s

    private final float width;
    private final float height;

    // Directional Support and Contact Counters
    private Direction supportDirection = Direction.DOWN;
    private int bottomContacts = 0;
    private int topContacts = 0;
    private int leftContacts = 0;
    private int rightContacts = 0;

    private boolean jumpConsumed = false;
    private float moveDirection = 0.0f;

    /**
     * Constructs a Player entity wrapping an existing Box2D dynamic body.
     *
     * @param body   the dynamic Box2D body
     * @param width  player width in world meters
     * @param height player height in world meters
     */
    public Player(Body body, float width, float height) {
        super(body);
        this.width = width;
        this.height = height;

        // Ensure character does not tip over when colliding
        this.body.setFixedRotation(true);
    }

    /**
     * Convenience factory method to construct a Player instance with standard physics parameters,
     * including a solid torso fixture and four directional support sensors.
     *
     * @param bodyFactory the BodyFactory attached to the active physics world
     * @param spawnX      spawn position bottom-left X in world meters
     * @param spawnY      spawn position bottom-left Y in world meters
     * @return initialized Player entity
     */
    public static Player create(BodyFactory bodyFactory, float spawnX, float spawnY) {
        Body body = bodyFactory.createPlayerBody(
            spawnX, spawnY,
            DEFAULT_WIDTH, DEFAULT_HEIGHT,
            DENSITY, FRICTION, RESTITUTION
        );
        return new Player(body, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Updates player-specific domain state and applies tangential movement velocity before the physics step.
     * Strictly preserves the velocity component parallel to gravity (freefall and collision reactions)
     * while setting the tangential velocity component perpendicular to gravity.
     *
     * @param delta time elapsed since last frame in seconds
     */
    @Override
    public void update(float delta) {
        if (isDisposed() || body == null) {
            return;
        }

        Vector2 currentVelocity = body.getLinearVelocity();
        float targetSpeed = moveDirection * MOVE_SPEED;

        switch (supportDirection) {
            case DOWN, UP ->
                body.setLinearVelocity(targetSpeed, currentVelocity.y);

            case LEFT, RIGHT ->
                body.setLinearVelocity(currentVelocity.x, targetSpeed);
        }
    }

    /**
     * Executes a jump opposite to the active gravity direction if the player is grounded,
     * not disposed, and has not already consumed the jump for the current grounded period.
     * Preserves current tangential velocity perpendicular to gravity.
     *
     * @return true if jump was successfully executed, false otherwise
     */
    public boolean jump() {
        if (isDisposed() || body == null) {
            return false;
        }

        if (!isGrounded() || jumpConsumed) {
            return false;
        }

        Vector2 currentVelocity = body.getLinearVelocity();

        switch (supportDirection) {
            case DOWN -> body.setLinearVelocity(currentVelocity.x, JUMP_VELOCITY);
            case UP -> body.setLinearVelocity(currentVelocity.x, -JUMP_VELOCITY);
            case LEFT -> body.setLinearVelocity(JUMP_VELOCITY, currentVelocity.y);
            case RIGHT -> body.setLinearVelocity(-JUMP_VELOCITY, currentVelocity.y);
        }

        this.jumpConsumed = true;
        return true;
    }

    /**
     * Sets the desired horizontal movement direction in the range [-1.0, 1.0].
     * Clamps values outside this range and safely ignores non-finite values (NaN / Infinity).
     * Ignored if the player is already disposed.
     *
     * @param direction movement direction (-1.0 = left, 0.0 = idle, 1.0 = right)
     */
    public void setMoveDirection(float direction) {
        if (isDisposed()) {
            return;
        }
        if (!Float.isFinite(direction)) {
            return;
        }
        this.moveDirection = Math.max(-1.0f, Math.min(1.0f, direction));
    }

    /**
     * Returns the current horizontal movement direction [-1.0, 1.0].
     *
     * @return movement direction
     */
    public float getMoveDirection() {
        return moveDirection;
    }

    /**
     * Synchronizes the player's active support direction with the current
     * world gravity direction.
     *
     * Physical contact counters remain untouched. Grounded state immediately
     * reflects whether the newly selected directional sensor has active contacts.
     *
     * @param newGravityDirection the cardinal direction gravity now points
     * @throws IllegalArgumentException if newGravityDirection is null
     */
    public void onGravityDirectionChanged(Direction newGravityDirection) {
        if (isDisposed()) {
            return;
        }

        if (newGravityDirection == null) {
            throw new IllegalArgumentException(
                "Gravity direction cannot be null."
            );
        }

        if (this.supportDirection == newGravityDirection) {
            return;
        }

        this.supportDirection = newGravityDirection;
    }

    /**
     * Sets the cardinal direction in which the player expects supporting contact.
     * Preserved for compatibility; delegates to {@link #onGravityDirectionChanged(Direction)}.
     *
     * @param direction expected support direction (DOWN, UP, LEFT, RIGHT)
     * @throws IllegalArgumentException if direction is null on an active player
     */
    public void setSupportDirection(Direction direction) {
        onGravityDirectionChanged(direction);
    }

    /**
     * Returns the currently active support direction.
     *
     * @return active support direction
     */
    public Direction getSupportDirection() {
        return supportDirection;
    }

    /**
     * Callback invoked by GameContactListener when a directional sensor enters contact
     * with a valid supporting surface (GROUND, WALL, CEILING).
     * Increments the sensor counter and resets the jump latch ONLY when transitioning
     * from 0 to 1 contacts along the active support direction.
     *
     * @param sensorType the sensor fixture type that made contact
     */
    public void onSensorContactBegin(FixtureType sensorType) {
        if (isDisposed() || sensorType == null) {
            return;
        }

        Direction dir = toDirection(sensorType);
        if (dir == null) {
            return;
        }

        if (dir == supportDirection && getContactCount(dir) == 0) {
            jumpConsumed = false;
        }

        incrementContact(dir);
    }

    /**
     * Callback invoked by GameContactListener when a directional sensor leaves contact
     * with a supporting surface.
     * Decrements the corresponding contact counter with underflow protection.
     *
     * @param sensorType the sensor fixture type that broke contact
     */
    public void onSensorContactEnd(FixtureType sensorType) {
        if (isDisposed() || sensorType == null) {
            return;
        }

        Direction dir = toDirection(sensorType);
        if (dir == null) {
            return;
        }

        decrementContact(dir);
    }

    public void onGroundContactBegin() {
        onSensorContactBegin(FixtureType.PLAYER_BOTTOM_SENSOR);
    }

    public void onGroundContactEnd() {
        onSensorContactEnd(FixtureType.PLAYER_BOTTOM_SENSOR);
    }

    private Direction toDirection(FixtureType sensorType) {
        return switch (sensorType) {
            case PLAYER_BOTTOM_SENSOR -> Direction.DOWN;
            case PLAYER_TOP_SENSOR -> Direction.UP;
            case PLAYER_LEFT_SENSOR -> Direction.LEFT;
            case PLAYER_RIGHT_SENSOR -> Direction.RIGHT;
            default -> null;
        };
    }

    private void incrementContact(Direction dir) {
        switch (dir) {
            case DOWN -> bottomContacts++;
            case UP -> topContacts++;
            case LEFT -> leftContacts++;
            case RIGHT -> rightContacts++;
        }
    }

    private void decrementContact(Direction dir) {
        switch (dir) {
            case DOWN -> {
                if (bottomContacts > 0) bottomContacts--;
                else logUnderflow(dir);
            }
            case UP -> {
                if (topContacts > 0) topContacts--;
                else logUnderflow(dir);
            }
            case LEFT -> {
                if (leftContacts > 0) leftContacts--;
                else logUnderflow(dir);
            }
            case RIGHT -> {
                if (rightContacts > 0) rightContacts--;
                else logUnderflow(dir);
            }
        }
    }

    private void logUnderflow(Direction dir) {
        if (GameConfig.DEBUG_PHYSICS && Gdx.app != null) {
            Gdx.app.error("Player", "Unexpected contact counter underflow for sensor: " + dir);
        }
    }

    /**
     * Returns true if the player currently contacts a valid supporting surface along its
     * active support direction. Returns false if unsupported or disposed.
     *
     * @return true if supported, false otherwise
     */
    public boolean isGrounded() {
        return !isDisposed() && getContactCount(supportDirection) > 0;
    }

    /**
     * Returns the active contact count for the specified cardinal direction.
     *
     * @param direction direction to query
     * @return active contact count
     */
    public int getContactCount(Direction direction) {
        if (direction == null) {
            return 0;
        }
        return switch (direction) {
            case DOWN -> bottomContacts;
            case UP -> topContacts;
            case LEFT -> leftContacts;
            case RIGHT -> rightContacts;
        };
    }

    /**
     * Returns the active contact count along the active support direction.
     *
     * @return active supporting contact count
     */
    public int getGroundContacts() {
        return getContactCount(supportDirection);
    }

    /**
     * Returns whether the jump input has been consumed for the current unsupported cycle.
     *
     * @return true if jump is consumed, false otherwise
     */
    public boolean isJumpConsumed() {
        return jumpConsumed;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (isDisposed()) {
            bottomContacts = 0;
            topContacts = 0;
            leftContacts = 0;
            rightContacts = 0;
            jumpConsumed = false;
            moveDirection = 0.0f;
            supportDirection = Direction.DOWN;
        }
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}
