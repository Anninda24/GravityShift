package com.AnnindaRajDas.gravityshift.input;

import com.AnnindaRajDas.gravityshift.entity.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Controller translating keyboard input events into high-level player domain commands.
 *
 * Demonstrates:
 * - Single Responsibility Principle (SRP): Isolates input polling from player physics and rendering.
 * - Dependency Injection: Operates on a Player reference passed via constructor.
 */
public class PlayerInputController {

    private final Player player;

    /**
     * Constructs a PlayerInputController bound to the specified player entity.
     *
     * @param player the player entity to command
     * @throws IllegalArgumentException if player is null
     */
    public PlayerInputController(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null for PlayerInputController.");
        }
        this.player = player;
    }

    /**
     * Polls active keyboard input state and forwards high-level movement and jump commands
     * to the player entity.
     * Must be called once per frame prior to EntityManager and PhysicsController updates.
     */
    public void update() {
        if (player.isDisposed()) {
            return;
        }

        handleMovementInput();
        handleJumpInput();
    }

    private void handleMovementInput() {
        boolean leftPressed = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean rightPressed = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);

        float direction = 0.0f;
        if (leftPressed && !rightPressed) {
            direction = -1.0f;
        } else if (rightPressed && !leftPressed) {
            direction = 1.0f;
        } else {
            direction = 0.0f;
        }

        player.setMoveDirection(direction);
    }

    private void handleJumpInput() {
        // isKeyJustPressed ensures holding SPACE triggers at most a single jump attempt
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            player.jump();
        }
    }

    public Player getPlayer() {
        return player;
    }
}

