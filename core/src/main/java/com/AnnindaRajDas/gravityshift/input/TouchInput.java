package com.AnnindaRajDas.gravityshift.input;

import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.badlogic.gdx.InputProcessor;

/**
 * Touch implementation of {@link InputProvider} and {@link InputProcessor}.
 * Handles virtual joystick movement, jump button, 4 cardinal gravity buttons,
 * and right-half-screen directional gravity swipes with strict per-pointer ownership
 * and zero per-frame allocations.
 */
public class TouchInput implements InputProvider, InputProcessor {

    private static final int NO_POINTER = -1;
    private static final float STICK_DEADZONE = 0.15f;

    // Viewport dimensions (screen pixels)
    private float viewportWidth = 1280f;
    private float viewportHeight = 720f;

    // Virtual joystick geometry (bottom-left, HUD bottom-up coordinates)
    private float stickCenterX;
    private float stickCenterY;
    private float stickRadius;
    private float stickHitRadius;
    private float stickKnobX;
    private float stickKnobY;

    // Jump button geometry (bottom-right, HUD bottom-up coordinates)
    private float jumpCenterX;
    private float jumpCenterY;
    private float jumpRadius;

    // 4 cardinal gravity buttons (top-right cluster, HUD bottom-up coordinates)
    // Order: 0=DOWN, 1=LEFT, 2=UP, 3=RIGHT
    private final float[] gravBtnX = new float[4];
    private final float[] gravBtnY = new float[4];
    private float gravBtnRadius;

    // Swipe threshold
    private float swipeThreshold;

    // Pointer ownership
    private int stickPointer = NO_POINTER;
    private int jumpPointer = NO_POINTER;
    private int gravBtnPointer = NO_POINTER;
    private int swipePointer = NO_POINTER;

    // Swipe tracking
    private float swipeStartX;
    private float swipeStartY;
    private boolean swipeTriggered = false;

    // Output state
    private float moveX = 0.0f;
    private float moveY = 0.0f;
    private boolean jumpJustPressed = false;
    private Direction requestedGravity = null;

    public TouchInput() {
        resize(1280, 720);
    }

    /**
     * Recomputes control positions and radii for the current HUD viewport size.
     */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.viewportWidth = width;
        this.viewportHeight = height;

        float minDim = Math.min(width, height);
        float margin = clamp(minDim * 0.06f, 24f, 64f);

        // Virtual stick (bottom-left)
        stickRadius = clamp(minDim * 0.13f, 60f, 125f);
        stickHitRadius = stickRadius * 1.45f;
        stickCenterX = margin + stickRadius;
        stickCenterY = margin + stickRadius;
        if (stickPointer == NO_POINTER) {
            stickKnobX = stickCenterX;
            stickKnobY = stickCenterY;
        }

        // Jump button (bottom-right)
        jumpRadius = clamp(minDim * 0.11f, 52f, 110f);
        jumpCenterX = width - margin - jumpRadius;
        jumpCenterY = margin + jumpRadius;

        // Gravity buttons (compact diamond/cross in top-right, clear of center & top-left debug overlay)
        gravBtnRadius = clamp(minDim * 0.055f, 26f, 52f);
        float clusterSpacing = gravBtnRadius * 2.25f;
        float clusterCenterX = width - margin - gravBtnRadius - clusterSpacing;
        float clusterCenterY = height - margin - gravBtnRadius - clusterSpacing;

        // 0=DOWN, 1=LEFT, 2=UP, 3=RIGHT
        gravBtnX[0] = clusterCenterX;
        gravBtnY[0] = clusterCenterY - clusterSpacing;
        gravBtnX[1] = clusterCenterX - clusterSpacing;
        gravBtnY[1] = clusterCenterY;
        gravBtnX[2] = clusterCenterX;
        gravBtnY[2] = clusterCenterY + clusterSpacing;
        gravBtnX[3] = clusterCenterX + clusterSpacing;
        gravBtnY[3] = clusterCenterY;

        swipeThreshold = clamp(minDim * 0.08f, 36f, 80f);
    }

    @Override
    public void update() {
        // Event-driven via InputProcessor; nothing to poll per frame.
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
        Direction result = requestedGravity;
        requestedGravity = null;
        return result;
    }

    @Override
    public void reset() {
        stickPointer = NO_POINTER;
        jumpPointer = NO_POINTER;
        gravBtnPointer = NO_POINTER;
        swipePointer = NO_POINTER;
        swipeTriggered = false;
        moveX = 0.0f;
        moveY = 0.0f;
        jumpJustPressed = false;
        requestedGravity = null;
        stickKnobX = stickCenterX;
        stickKnobY = stickCenterY;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        float hudX = screenX;
        float hudY = viewportHeight - screenY;

        // 1. Virtual joystick (left side / stick hit zone)
        if (stickPointer == NO_POINTER && isInsideCircle(hudX, hudY, stickCenterX, stickCenterY, stickHitRadius)) {
            stickPointer = pointer;
            updateStickFromTouch(hudX, hudY);
            return true;
        }

        // 2. Jump button (bottom-right)
        if (jumpPointer == NO_POINTER && isInsideCircle(hudX, hudY, jumpCenterX, jumpCenterY, jumpRadius * 1.15f)) {
            jumpPointer = pointer;
            jumpJustPressed = true;
            return true;
        }

        // 3. Cardinal gravity buttons (top-right)
        if (gravBtnPointer == NO_POINTER) {
            for (int i = 0; i < 4; i++) {
                if (isInsideCircle(hudX, hudY, gravBtnX[i], gravBtnY[i], gravBtnRadius * 1.15f)) {
                    gravBtnPointer = pointer;
                    requestedGravity = indexToDirection(i);
                    return true;
                }
            }
        }

        // 4. Gravity swipe on right half of the screen
        if (swipePointer == NO_POINTER && hudX >= viewportWidth * 0.5f) {
            swipePointer = pointer;
            swipeStartX = hudX;
            swipeStartY = hudY;
            swipeTriggered = false;
            return true;
        }

        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        float hudX = screenX;
        float hudY = viewportHeight - screenY;

        if (pointer == stickPointer) {
            updateStickFromTouch(hudX, hudY);
            return true;
        }

        if (pointer == swipePointer && !swipeTriggered) {
            float dx = hudX - swipeStartX;
            float dy = hudY - swipeStartY;
            float distSq = dx * dx + dy * dy;
            if (distSq >= swipeThreshold * swipeThreshold) {
                swipeTriggered = true;
                if (Math.abs(dx) >= Math.abs(dy)) {
                    requestedGravity = (dx >= 0f) ? Direction.RIGHT : Direction.LEFT;
                } else {
                    requestedGravity = (dy >= 0f) ? Direction.UP : Direction.DOWN;
                }
            }
            return true;
        }

        return pointer == jumpPointer || pointer == gravBtnPointer;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return releasePointer(pointer);
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return releasePointer(pointer);
    }

    private boolean releasePointer(int pointer) {
        if (pointer == stickPointer) {
            stickPointer = NO_POINTER;
            moveX = 0.0f;
            moveY = 0.0f;
            stickKnobX = stickCenterX;
            stickKnobY = stickCenterY;
            return true;
        }
        if (pointer == jumpPointer) {
            jumpPointer = NO_POINTER;
            return true;
        }
        if (pointer == gravBtnPointer) {
            gravBtnPointer = NO_POINTER;
            return true;
        }
        if (pointer == swipePointer) {
            swipePointer = NO_POINTER;
            swipeTriggered = false;
            return true;
        }
        return false;
    }

    private void updateStickFromTouch(float hudX, float hudY) {
        float dx = hudX - stickCenterX;
        float dy = hudY - stickCenterY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > stickRadius && dist > 0.0001f) {
            float scale = stickRadius / dist;
            dx *= scale;
            dy *= scale;
            dist = stickRadius;
        }

        stickKnobX = stickCenterX + dx;
        stickKnobY = stickCenterY + dy;

        float normDist = (stickRadius > 0.0001f) ? (dist / stickRadius) : 0f;
        if (normDist < STICK_DEADZONE) {
            moveX = 0.0f;
            moveY = 0.0f;
        } else {
            moveX = clamp(dx / stickRadius, -1.0f, 1.0f);
            moveY = clamp(dy / stickRadius, -1.0f, 1.0f);
        }
    }

    private static boolean isInsideCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static Direction indexToDirection(int index) {
        switch (index) {
            case 0: return Direction.DOWN;
            case 1: return Direction.LEFT;
            case 2: return Direction.UP;
            case 3: return Direction.RIGHT;
            default: return Direction.DOWN;
        }
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    // --- Unused InputProcessor methods ---

    @Override
    public boolean keyDown(int keycode) { return false; }

    @Override
    public boolean keyUp(int keycode) { return false; }

    @Override
    public boolean keyTyped(char character) { return false; }

    @Override
    public boolean mouseMoved(int screenX, int screenY) { return false; }

    @Override
    public boolean scrolled(float amountX, float amountY) { return false; }

    // --- Layout & State Getters for TouchHud and Tests ---

    public float getStickCenterX() { return stickCenterX; }
    public float getStickCenterY() { return stickCenterY; }
    public float getStickRadius() { return stickRadius; }
    public float getStickKnobX() { return stickKnobX; }
    public float getStickKnobY() { return stickKnobY; }
    public boolean isStickActive() { return stickPointer != NO_POINTER; }

    public float getJumpCenterX() { return jumpCenterX; }
    public float getJumpCenterY() { return jumpCenterY; }
    public float getJumpRadius() { return jumpRadius; }
    public boolean isJumpActive() { return jumpPointer != NO_POINTER; }

    public float getGravityButtonX(int index) { return gravBtnX[index]; }
    public float getGravityButtonY(int index) { return gravBtnY[index]; }
    public float getGravityButtonRadius() { return gravBtnRadius; }
    public float getViewportWidth() { return viewportWidth; }
    public float getViewportHeight() { return viewportHeight; }
}
