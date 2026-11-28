package com.AnnindaRajDas.gravityshift.replay;

import com.AnnindaRajDas.gravityshift.input.InputProvider;
import com.AnnindaRajDas.gravityshift.physics.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, fixed-step {@link InputProvider} for recorded solution replays.
 * Routes movement, jump, and gravity-shift commands through the exact same
 * {@link com.AnnindaRajDas.gravityshift.input.PlayerInputController} path as live input.
 */
public class ReplayInputProvider implements InputProvider {

    public static final class ReplayAction {
        public final int startStep;
        public final int endStep;
        public final float moveX;
        public final float moveY;
        public final boolean jump;
        public final Direction gravityDirection;

        public ReplayAction(int startStep, int endStep, float moveX, float moveY,
                            boolean jump, Direction gravityDirection) {
            this.startStep = startStep;
            this.endStep = endStep;
            this.moveX = moveX;
            this.moveY = moveY;
            this.jump = jump;
            this.gravityDirection = gravityDirection;
        }
    }

    private final List<ReplayAction> actions = new ArrayList<>();
    private int currentStep = -1;
    private float moveX = 0.0f;
    private float moveY = 0.0f;
    private boolean jumpJustPressed = false;
    private Direction requestedGravity = null;

    public ReplayInputProvider addMove(int startStep, int endStep, float moveX, float moveY) {
        actions.add(new ReplayAction(startStep, endStep, moveX, moveY, false, null));
        return this;
    }

    public ReplayInputProvider addJump(int step) {
        actions.add(new ReplayAction(step, step, 0.0f, 0.0f, true, null));
        return this;
    }

    public ReplayInputProvider addGravityShift(int step, Direction direction) {
        actions.add(new ReplayAction(step, step, 0.0f, 0.0f, false, direction));
        return this;
    }

    public void setStep(int step) {
        this.currentStep = step;
    }

    @Override
    public void update() {
        moveX = 0.0f;
        moveY = 0.0f;
        jumpJustPressed = false;
        requestedGravity = null;

        for (int i = 0; i < actions.size(); i++) {
            ReplayAction a = actions.get(i);
            if (currentStep >= a.startStep && currentStep <= a.endStep) {
                if (a.moveX != 0.0f) {
                    moveX = a.moveX;
                }
                if (a.moveY != 0.0f) {
                    moveY = a.moveY;
                }
                if (a.jump && currentStep == a.startStep) {
                    jumpJustPressed = true;
                }
                if (a.gravityDirection != null && currentStep == a.startStep) {
                    requestedGravity = a.gravityDirection;
                }
            }
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
        Direction result = requestedGravity;
        requestedGravity = null;
        return result;
    }

    @Override
    public void reset() {
        currentStep = -1;
        moveX = 0.0f;
        moveY = 0.0f;
        jumpJustPressed = false;
        requestedGravity = null;
    }
}
