package com.AnnindaRajDas.gravityshift.input;

import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.physics.Direction;

/**
 * Controller translating platform-independent {@link InputProvider} state
 * into high-level player domain commands.
 */
public class PlayerInputController {

    private final Player player;
    private final InputProvider inputProvider;

    /**
     * Constructs a PlayerInputController using a default {@link KeyboardInput}.
     */
    public PlayerInputController(Player player) {
        this(player, new KeyboardInput());
    }

    /**
     * Constructs a PlayerInputController bound to the specified player and input provider.
     *
     * @param player        the player entity to command
     * @param inputProvider the platform-independent input source
     * @throws IllegalArgumentException if player or inputProvider is null
     */
    public PlayerInputController(Player player, InputProvider inputProvider) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null for PlayerInputController.");
        }
        if (inputProvider == null) {
            throw new IllegalArgumentException("InputProvider cannot be null for PlayerInputController.");
        }
        this.player = player;
        this.inputProvider = inputProvider;
    }

    /**
     * Polls active input state and forwards screen-relative movement and jump commands
     * to the player entity.
     * Must be called once per frame prior to EntityManager and PhysicsController updates.
     */
    public void update() {
        if (player.isDisposed()) {
            return;
        }

        inputProvider.update();
        handleMovementInput();
        handleJumpInput();
    }

    private void handleMovementInput() {
        Direction support = player.getSupportDirection();
        float direction;

        if (support == Direction.DOWN || support == Direction.UP) {
            direction = inputProvider.getMoveX();
        } else {
            // Horizontal gravity (LEFT/RIGHT): up/down controls movement along the wall
            direction = inputProvider.getMoveY();
        }

        player.setMoveDirection(direction);
    }

    private void handleJumpInput() {
        if (inputProvider.isJumpJustPressed()) {
            player.jump();
        }
    }

    /**
     * Consumes any pending gravity-direction change request from the input provider.
     *
     * @param currentGravity the currently active gravity direction
     * @return the requested direction, or null if none
     */
    public Direction consumeGravityRequest(Direction currentGravity) {
        return inputProvider.consumeRequestedGravityDirection(currentGravity);
    }

    /**
     * Resets all active/buffered input state.
     */
    public void reset() {
        inputProvider.reset();
        if (!player.isDisposed()) {
            player.setMoveDirection(0.0f);
        }
    }

    public Player getPlayer() {
        return player;
    }

    public InputProvider getInputProvider() {
        return inputProvider;
    }
}
