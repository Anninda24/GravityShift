package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.input.CompositeInputProvider;
import com.AnnindaRajDas.gravityshift.input.InputProvider;
import com.AnnindaRajDas.gravityshift.input.KeyboardInput;
import com.AnnindaRajDas.gravityshift.input.PlayerInputController;
import com.AnnindaRajDas.gravityshift.input.TouchInput;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase4Test {

    private PhysicsController physicsController;
    private Player player;

    @BeforeEach
    void setUp() {
        physicsController = new PhysicsController();
        player = Player.create(physicsController.getBodyFactory(), 5.0f, 3.0f);
    }

    @AfterEach
    void tearDown() {
        if (player != null && !player.isDisposed()) {
            player.dispose();
        }
        if (physicsController != null && !physicsController.isDisposed()) {
            physicsController.dispose();
        }
    }

    private static int toScreenY(TouchInput touch, float hudY) {
        return Math.round(touch.getViewportHeight() - hudY);
    }

    @Test
    void testKeyboardInputNeutralWhenNoGdxInput() {
        KeyboardInput kb = new KeyboardInput();
        kb.update();
        assertEquals(0.0f, kb.getMoveX(), 0.0001f);
        assertEquals(0.0f, kb.getMoveY(), 0.0001f);
        assertFalse(kb.isJumpJustPressed());
        assertNull(kb.consumeRequestedGravityDirection(Direction.DOWN));
    }

    @Test
    void testKeyboardRotateDirection() {
        assertEquals(Direction.LEFT, KeyboardInput.rotateDirection(Direction.DOWN, 1));
        assertEquals(Direction.UP, KeyboardInput.rotateDirection(Direction.LEFT, 1));
        assertEquals(Direction.RIGHT, KeyboardInput.rotateDirection(Direction.UP, 1));
        assertEquals(Direction.DOWN, KeyboardInput.rotateDirection(Direction.RIGHT, 1));

        assertEquals(Direction.RIGHT, KeyboardInput.rotateDirection(Direction.DOWN, -1));
        assertEquals(Direction.UP, KeyboardInput.rotateDirection(Direction.RIGHT, -1));
    }

    @Test
    void testTouchStickMovementAndReleaseNeutral() {
        TouchInput touch = new TouchInput();
        touch.resize(1280, 720);

        float cx = touch.getStickCenterX();
        float cy = touch.getStickCenterY();
        float r = touch.getStickRadius();

        assertTrue(touch.touchDown(Math.round(cx), toScreenY(touch, cy), 0, 0));
        assertTrue(touch.isStickActive());
        assertEquals(0.0f, touch.getMoveX(), 0.0001f);
        assertEquals(0.0f, touch.getMoveY(), 0.0001f);

        // Drag right
        touch.touchDragged(Math.round(cx + r), toScreenY(touch, cy), 0);
        assertEquals(1.0f, touch.getMoveX(), 0.01f);
        assertEquals(0.0f, touch.getMoveY(), 0.01f);

        // Release -> neutral
        touch.touchUp(Math.round(cx + r), toScreenY(touch, cy), 0, 0);
        assertFalse(touch.isStickActive());
        assertEquals(0.0f, touch.getMoveX(), 0.0001f);
        assertEquals(0.0f, touch.getMoveY(), 0.0001f);
    }

    @Test
    void testScreenRelativeMovementAcrossAllFourGravityDirections() {
        TouchInput touch = new TouchInput();
        touch.resize(1280, 720);
        PlayerInputController controller = new PlayerInputController(player, touch);

        float cx = touch.getStickCenterX();
        float cy = touch.getStickCenterY();
        float r = touch.getStickRadius();

        touch.touchDown(Math.round(cx), toScreenY(touch, cy), 0, 0);

        // DOWN gravity: horizontal stick (+1) -> +1
        player.onGravityDirectionChanged(Direction.DOWN);
        touch.touchDragged(Math.round(cx + r), toScreenY(touch, cy), 0);
        controller.update();
        assertEquals(1.0f, player.getMoveDirection(), 0.01f);

        // UP gravity: horizontal stick (-1) -> -1
        player.onGravityDirectionChanged(Direction.UP);
        touch.touchDragged(Math.round(cx - r), toScreenY(touch, cy), 0);
        controller.update();
        assertEquals(-1.0f, player.getMoveDirection(), 0.01f);

        // LEFT gravity: vertical stick (+1 up) -> +1
        player.onGravityDirectionChanged(Direction.LEFT);
        touch.touchDragged(Math.round(cx), toScreenY(touch, cy + r), 0);
        controller.update();
        assertEquals(1.0f, player.getMoveDirection(), 0.01f);

        // RIGHT gravity: vertical stick (-1 down) -> -1
        player.onGravityDirectionChanged(Direction.RIGHT);
        touch.touchDragged(Math.round(cx), toScreenY(touch, cy - r), 0);
        controller.update();
        assertEquals(-1.0f, player.getMoveDirection(), 0.01f);
    }

    @Test
    void testTouchJumpEdgeTriggerAndNoBypassWhenHeld() {
        TouchInput touch = new TouchInput();
        touch.resize(1280, 720);
        PlayerInputController controller = new PlayerInputController(player, touch);

        int jx = Math.round(touch.getJumpCenterX());
        int jy = toScreenY(touch, touch.getJumpCenterY());

        assertTrue(touch.touchDown(jx, jy, 1, 0));
        assertTrue(touch.isJumpActive());

        // First poll consumes jump request
        assertTrue(touch.isJumpJustPressed());
        // Holding pointer does not re-trigger jump
        assertFalse(touch.isJumpJustPressed());

        touch.touchUp(jx, jy, 1, 0);
        assertFalse(touch.isJumpActive());
        assertFalse(touch.isJumpJustPressed());

        // Verify controller routes jump to player
        assertTrue(touch.touchDown(jx, jy, 1, 0));
        controller.update();
        assertFalse(touch.isJumpJustPressed());
    }

    @Test
    void testAllFourGravityButtonsSelectExpectedDirection() {
        TouchInput touch = new TouchInput();
        touch.resize(1280, 720);

        Direction[] expected = { Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT };
        for (int i = 0; i < 4; i++) {
            int bx = Math.round(touch.getGravityButtonX(i));
            int by = toScreenY(touch, touch.getGravityButtonY(i));
            assertTrue(touch.touchDown(bx, by, 2, 0));
            assertEquals(expected[i], touch.consumeRequestedGravityDirection(Direction.DOWN));
            assertNull(touch.consumeRequestedGravityDirection(Direction.DOWN));
            touch.touchUp(bx, by, 2, 0);
        }
    }

    @Test
    void testGravitySwipeSingleTriggerAndPointerIsolation() {
        TouchInput touch = new TouchInput();
        touch.resize(1280, 720);

        // Start swipe in open right-half area (center-right)
        int sx = 800;
        int sy = toScreenY(touch, 360f);
        assertTrue(touch.touchDown(sx, sy, 3, 0));

        // Drag up past threshold -> UP
        touch.touchDragged(sx, sy - 120, 3);
        assertEquals(Direction.UP, touch.consumeRequestedGravityDirection(Direction.DOWN));

        // Continuing same swipe must NOT trigger a second gravity change
        touch.touchDragged(sx + 150, sy - 120, 3);
        assertNull(touch.consumeRequestedGravityDirection(Direction.UP));

        touch.touchUp(sx + 150, sy - 120, 3, 0);

        // Stick interaction on left side must NOT trigger gravity swipe
        int stickX = Math.round(touch.getStickCenterX());
        int stickY = toScreenY(touch, touch.getStickCenterY());
        assertTrue(touch.touchDown(stickX, stickY, 0, 0));
        touch.touchDragged(stickX + 120, stickY, 0);
        assertNull(touch.consumeRequestedGravityDirection(Direction.DOWN));
    }

    @Test
    void testTouchCancellationAndResetClearAllState() {
        TouchInput touch = new TouchInput();
        touch.resize(1280, 720);

        int stickX = Math.round(touch.getStickCenterX());
        int stickY = toScreenY(touch, touch.getStickCenterY());
        touch.touchDown(stickX, stickY, 0, 0);
        touch.touchDragged(stickX + 80, stickY, 0);
        assertTrue(touch.getMoveX() > 0.0f);

        int jx = Math.round(touch.getJumpCenterX());
        int jy = toScreenY(touch, touch.getJumpCenterY());
        touch.touchDown(jx, jy, 1, 0);

        // Cancel stick pointer
        touch.touchCancelled(stickX + 80, stickY, 0, 0);
        assertFalse(touch.isStickActive());
        assertEquals(0.0f, touch.getMoveX(), 0.0001f);

        // Reset clears remaining jump pointer & buffered jump
        touch.reset();
        assertFalse(touch.isJumpActive());
        assertFalse(touch.isJumpJustPressed());
    }

    @Test
    void testCompositeInputProviderSourceSwitching() {
        KeyboardInput kb = new KeyboardInput();
        TouchInput touch = new TouchInput();
        CompositeInputProvider composite = new CompositeInputProvider(kb, touch);

        touch.resize(1280, 720);
        int stickX = Math.round(touch.getStickCenterX());
        int stickY = toScreenY(touch, touch.getStickCenterY());
        touch.touchDown(stickX, stickY, 0, 0);
        touch.touchDragged(stickX + 80, stickY, 0);
        assertTrue(composite.getMoveX() > 0.0f);

        composite.reset();
        assertEquals(0.0f, composite.getMoveX(), 0.0001f);
        assertEquals(0.0f, composite.getMoveY(), 0.0001f);
        assertFalse(composite.isJumpJustPressed());
    }
}
