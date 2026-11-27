package com.AnnindaRajDas.gravityshift.input;

import com.AnnindaRajDas.gravityshift.physics.Direction;

/**
 * Combines a primary and secondary {@link InputProvider} (e.g. {@link KeyboardInput}
 * and {@link TouchInput}) without allocating per frame.
 */
public class CompositeInputProvider implements InputProvider {

    private final InputProvider primary;
    private final InputProvider secondary;

    public CompositeInputProvider(InputProvider primary, InputProvider secondary) {
        if (primary == null || secondary == null) {
            throw new IllegalArgumentException("InputProviders cannot be null.");
        }
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public void update() {
        primary.update();
        secondary.update();
    }

    @Override
    public float getMoveX() {
        float x = secondary.getMoveX();
        return (x != 0.0f) ? x : primary.getMoveX();
    }

    @Override
    public float getMoveY() {
        float y = secondary.getMoveY();
        return (y != 0.0f) ? y : primary.getMoveY();
    }

    @Override
    public boolean isJumpJustPressed() {
        boolean p = primary.isJumpJustPressed();
        boolean s = secondary.isJumpJustPressed();
        return p || s;
    }

    @Override
    public Direction consumeRequestedGravityDirection(Direction currentGravity) {
        Direction s = secondary.consumeRequestedGravityDirection(currentGravity);
        Direction p = primary.consumeRequestedGravityDirection(currentGravity);
        return (s != null) ? s : p;
    }

    @Override
    public void reset() {
        primary.reset();
        secondary.reset();
    }
}
