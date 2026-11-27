package com.AnnindaRajDas.gravityshift.input;

import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;

/**
 * Lightweight, zero-per-frame-allocation renderer for on-screen touch controls:
 * - Virtual movement joystick (bottom-left)
 * - Jump button (bottom-right)
 * - Four cardinal gravity buttons (top-right)
 */
public class TouchHud implements Disposable {

    private final TouchInput touchInput;
    private ShapeRenderer shapeRenderer;
    private boolean disposed = false;

    public TouchHud(TouchInput touchInput) {
        if (touchInput == null) {
            throw new IllegalArgumentException("TouchInput cannot be null for TouchHud.");
        }
        this.touchInput = touchInput;
    }

    public void render(OrthographicCamera hudCamera, Direction activeGravity) {
        if (disposed || hudCamera == null) {
            return;
        }
        if (shapeRenderer == null) {
            shapeRenderer = new ShapeRenderer();
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(hudCamera.combined);

        // 1. Filled translucent bases
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Virtual stick base
        shapeRenderer.setColor(1f, 1f, 1f, touchInput.isStickActive() ? 0.22f : 0.14f);
        shapeRenderer.circle(touchInput.getStickCenterX(), touchInput.getStickCenterY(), touchInput.getStickRadius(), 32);

        // Virtual stick knob
        shapeRenderer.setColor(0.85f, 0.92f, 1f, touchInput.isStickActive() ? 0.65f : 0.40f);
        shapeRenderer.circle(touchInput.getStickKnobX(), touchInput.getStickKnobY(), touchInput.getStickRadius() * 0.42f, 24);

        // Jump button
        shapeRenderer.setColor(0.35f, 0.85f, 0.55f, touchInput.isJumpActive() ? 0.60f : 0.30f);
        shapeRenderer.circle(touchInput.getJumpCenterX(), touchInput.getJumpCenterY(), touchInput.getJumpRadius(), 32);

        // 4 cardinal gravity buttons: 0=DOWN, 1=LEFT, 2=UP, 3=RIGHT
        for (int i = 0; i < 4; i++) {
            boolean isActive = (activeGravity == indexToDirection(i));
            if (isActive) {
                shapeRenderer.setColor(0.30f, 0.72f, 1.0f, 0.58f);
            } else {
                shapeRenderer.setColor(1f, 1f, 1f, 0.18f);
            }
            shapeRenderer.circle(
                    touchInput.getGravityButtonX(i),
                    touchInput.getGravityButtonY(i),
                    touchInput.getGravityButtonRadius(),
                    24);
        }

        shapeRenderer.end();

        // 2. Crisp outlines
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 0.45f);
        shapeRenderer.circle(touchInput.getStickCenterX(), touchInput.getStickCenterY(), touchInput.getStickRadius(), 32);
        shapeRenderer.setColor(0.45f, 0.95f, 0.65f, 0.75f);
        shapeRenderer.circle(touchInput.getJumpCenterX(), touchInput.getJumpCenterY(), touchInput.getJumpRadius(), 32);
        for (int i = 0; i < 4; i++) {
            boolean isActive = (activeGravity == indexToDirection(i));
            shapeRenderer.setColor(isActive ? 0.4f : 1f, isActive ? 0.85f : 1f, 1f, isActive ? 0.95f : 0.45f);
            shapeRenderer.circle(
                    touchInput.getGravityButtonX(i),
                    touchInput.getGravityButtonY(i),
                    touchInput.getGravityButtonRadius(),
                    24);
        }
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
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

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
            shapeRenderer = null;
        }
    }
}
