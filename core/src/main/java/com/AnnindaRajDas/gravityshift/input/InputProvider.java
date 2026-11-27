package com.AnnindaRajDas.gravityshift.input;

import com.AnnindaRajDas.gravityshift.physics.Direction;

/**
 * Platform-independent input abstraction for gameplay.
 * Translates platform-specific input (keyboard, touch, or test doubles)
 * into screen-relative movement, jump, and gravity-shift commands.
 */
public interface InputProvider {

    /**
     * Updates any per-frame polling state.
     */
    void update();

    /**
     * Returns screen-relative horizontal movement in [-1.0, 1.0].
     * Negative is left, positive is right, 0 is neutral.
     */
    float getMoveX();

    /**
     * Returns screen-relative vertical movement in [-1.0, 1.0].
     * Negative is down, positive is up, 0 is neutral.
     */
    float getMoveY();

    /**
     * Returns true if a jump was newly requested since the last poll,
     * consuming the edge-triggered request.
     */
    boolean isJumpJustPressed();

    /**
     * Returns a newly requested gravity direction since the last poll,
     * consuming the edge-triggered request, or null if none.
     *
     * @param currentGravity the currently active gravity direction (used for relative rotations)
     * @return the requested cardinal direction, or null
     */
    Direction consumeRequestedGravityDirection(Direction currentGravity);

    /**
     * Clears all active and buffered input state (e.g. on pause, hide, or disposal).
     */
    void reset();
}
