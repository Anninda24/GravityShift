package com.AnnindaRajDas.gravityshift.input;

import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Desktop keyboard implementation of {@link InputProvider}.
 * Maps WASD/Arrow keys to screen-relative movement, SPACE to jump,
 * and 1-4 / Q / E to cardinal or relative gravity shifts.
 */
public class KeyboardInput implements InputProvider {

    private float moveX = 0.0f;
    private float moveY = 0.0f;
    private boolean jumpJustPressed = false;
    private Direction requestedGravity = null;
    private int rotationDelta = 0; // -1 = CCW (Q), +1 = CW (E)

    @Override
    public void update() {
        if (Gdx.input == null) {
            reset();
            return;
        }

        boolean left = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        if (left && !right) {
            moveX = -1.0f;
        } else if (right && !left) {
            moveX = 1.0f;
        } else {
            moveX = 0.0f;
        }

        boolean down = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean up = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        if (down && !up) {
            moveY = -1.0f;
        } else if (up && !down) {
            moveY = 1.0f;
        } else {
            moveY = 0.0f;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            jumpJustPressed = true;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            requestedGravity = Direction.DOWN;
            rotationDelta = 0;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            requestedGravity = Direction.LEFT;
            rotationDelta = 0;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            requestedGravity = Direction.UP;
            rotationDelta = 0;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
            requestedGravity = Direction.RIGHT;
            rotationDelta = 0;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            requestedGravity = null;
            rotationDelta = -1;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            requestedGravity = null;
            rotationDelta = 1;
        }
    }

    @Override
    public float getMoveX() {
        return moveX;
    }

    @Override
    public float getMoveY() {
        return moveY;
    }

    @Override
    public boolean isJumpJustPressed() {
        boolean result = jumpJustPressed;
        jumpJustPressed = false;
        return result;
    }

    @Override
    public Direction consumeRequestedGravityDirection(Direction currentGravity) {
        if (requestedGravity != null) {
            Direction result = requestedGravity;
            requestedGravity = null;
            rotationDelta = 0;
            return result;
        }
        if (rotationDelta != 0 && currentGravity != null) {
            int delta = rotationDelta;
            rotationDelta = 0;
            return rotateDirection(currentGravity, delta);
        }
        rotationDelta = 0;
        return null;
    }

    @Override
    public void reset() {
        moveX = 0.0f;
        moveY = 0.0f;
        jumpJustPressed = false;
        requestedGravity = null;
        rotationDelta = 0;
    }

    /**
     * Rotates a cardinal direction clockwise (+1) or counter-clockwise (-1).
     * Order: DOWN -> LEFT -> UP -> RIGHT -> DOWN (clockwise on screen).
     */
    public static Direction rotateDirection(Direction current, int delta) {
        if (current == null) {
            return null;
        }
        Direction[] order = { Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT };
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                idx = i;
                break;
            }
        }
        int nextIdx = (idx + delta + order.length) % order.length;
        return order[nextIdx];
    }
}
