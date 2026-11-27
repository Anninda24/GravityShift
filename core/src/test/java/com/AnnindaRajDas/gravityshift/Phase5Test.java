package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.config.GameConfig;
import com.AnnindaRajDas.gravityshift.entity.EntityManager;
import com.AnnindaRajDas.gravityshift.entity.GravityCube;
import com.AnnindaRajDas.gravityshift.entity.LaserHazard;
import com.AnnindaRajDas.gravityshift.entity.Player;
import com.AnnindaRajDas.gravityshift.entity.PressurePlate;
import com.AnnindaRajDas.gravityshift.entity.SlidingDoor;
import com.AnnindaRajDas.gravityshift.physics.Direction;
import com.AnnindaRajDas.gravityshift.physics.FixtureType;
import com.AnnindaRajDas.gravityshift.physics.PhysicsController;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerBus;
import com.AnnindaRajDas.gravityshift.puzzle.TriggerListener;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class Phase5Test {

    private PhysicsController physicsController;
    private EntityManager entityManager;

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        physicsController = new PhysicsController();
        entityManager = new EntityManager();
        physicsController.setStepListener(entityManager);
    }

    @AfterEach
    void tearDown() {
        if (entityManager != null && !entityManager.isDisposed()) {
            entityManager.dispose();
        }
        if (physicsController != null && !physicsController.isDisposed()) {
            physicsController.dispose();
        }
    }

    private void stepFrames(int frames) {
        for (int i = 0; i < frames; i++) {
            entityManager.update(GameConfig.TIME_STEP);
            physicsController.update(GameConfig.TIME_STEP);
        }
    }

    @Test
    void testGravityCubeRespondsToAllFourGravityDirectionsAndDisposesSafely() {
        GravityCube cube = GravityCube.create(physicsController.getBodyFactory(), 4.0f, 6.0f);
        entityManager.addEntity(cube);

        // DOWN
        physicsController.getGravityController().setGravityDirection(Direction.DOWN);
        cube.wakeUp();
        stepFrames(10);
        assertTrue(cube.getBody().getLinearVelocity().y < -0.5f);

        // LEFT
        physicsController.getGravityController().setGravityDirection(Direction.LEFT);
        cube.wakeUp();
        stepFrames(10);
        assertTrue(cube.getBody().getLinearVelocity().x < -0.5f);

        // UP
        physicsController.getGravityController().setGravityDirection(Direction.UP);
        cube.wakeUp();
        stepFrames(25);
        assertTrue(cube.getBody().getLinearVelocity().y > 0.0f);

        // RIGHT
        physicsController.getGravityController().setGravityDirection(Direction.RIGHT);
        cube.wakeUp();
        stepFrames(15);
        assertTrue(cube.getBody().getLinearVelocity().x > 0.0f);

        cube.dispose();
        assertTrue(cube.isDisposed());
    }

    @Test
    void testTriggerBusDeliveryIsolationAndCleanup() {
        TriggerBus bus = new TriggerBus();
        AtomicInteger ch1Count = new AtomicInteger(0);
        AtomicInteger ch2Count = new AtomicInteger(0);

        TriggerListener l1 = (ch, active) -> ch1Count.incrementAndGet();
        TriggerListener l2 = (ch, active) -> ch2Count.incrementAndGet();

        bus.subscribe("ch1", l1);
        bus.subscribe("ch2", l2);
        assertEquals(1, bus.getSubscriberCount("ch1"));
        assertEquals(1, bus.getSubscriberCount("ch2"));

        bus.publish("ch1", true);
        assertEquals(1, ch1Count.get());
        assertEquals(0, ch2Count.get());

        bus.unsubscribe("ch1", l1);
        assertEquals(0, bus.getSubscriberCount("ch1"));
        bus.publish("ch1", false);
        assertEquals(1, ch1Count.get());
    }

    @Test
    void testPressurePlateMultipleBodiesMultipleFixturesAndRemoval() {
        TriggerBus bus = new TriggerBus();
        AtomicInteger activations = new AtomicInteger(0);
        AtomicInteger deactivations = new AtomicInteger(0);
        bus.subscribe("plate_a", (ch, active) -> {
            if (active) activations.incrementAndGet();
            else deactivations.incrementAndGet();
        });

        PressurePlate plate = PressurePlate.create(
                physicsController.getBodyFactory(), 10.0f, 1.0f, 2.0f, 0.4f, "plate_a", bus);
        entityManager.addEntity(plate);

        // Cube 1 falls onto left half of plate
        GravityCube cube1 = GravityCube.create(physicsController.getBodyFactory(), 10.0f, 2.5f);
        entityManager.addEntity(cube1);
        stepFrames(40);
        assertTrue(plate.isPressed());
        assertEquals(1, activations.get());
        assertEquals(0, deactivations.get());

        // Cube 2 falls onto right half of plate -> no duplicate activation
        GravityCube cube2 = GravityCube.create(physicsController.getBodyFactory(), 11.1f, 2.5f);
        entityManager.addEntity(cube2);
        stepFrames(40);
        assertTrue(plate.isPressed());
        assertEquals(1, activations.get());

        // Dispose cube1 while cube2 remains -> plate stays pressed!
        cube1.dispose();
        stepFrames(5);
        assertTrue(plate.isPressed());
        assertEquals(0, deactivations.get());

        // Dispose cube2 -> plate deactivates!
        cube2.dispose();
        stepFrames(5);
        assertFalse(plate.isPressed());
        assertEquals(1, deactivations.get());

        // Multiple fixtures on one body
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.StaticBody;
        bd.position.set(11.0f, 1.2f);
        Body multiBody = physicsController.getWorld().createBody(bd);
        PolygonShape s = new PolygonShape();
        s.setAsBox(0.2f, 0.2f);
        FixtureDef fd = new FixtureDef();
        fd.shape = s;
        fd.isSensor = true;
        Fixture f1 = multiBody.createFixture(fd);
        f1.setUserData(FixtureType.GRAVITY_CUBE);
        Fixture f2 = multiBody.createFixture(fd);
        f2.setUserData(FixtureType.GRAVITY_CUBE);
        s.dispose();

        plate.onSensorContactBegin(f1);
        plate.onSensorContactBegin(f2);
        stepFrames(2);
        assertTrue(plate.isPressed());
        plate.onSensorContactEnd(f1);
        stepFrames(2);
        assertTrue(plate.isPressed());
        plate.onSensorContactEnd(f2);
        stepFrames(2);
        assertFalse(plate.isPressed());
    }

    @Test
    void testSlidingDoorRespondsToTriggerBusAndDisposesSafely() {
        TriggerBus bus = new TriggerBus();
        SlidingDoor door = SlidingDoor.create(
                physicsController.getBodyFactory(), 16.0f, 1.0f, 0.6f, 3.0f, "door_x", bus);
        entityManager.addEntity(door);

        stepFrames(2);
        assertFalse(door.isOpen());
        assertFalse(door.getBody().getFixtureList().get(0).isSensor());

        bus.publish("door_x", true);
        stepFrames(2);
        assertTrue(door.isOpen());
        assertTrue(door.getBody().getFixtureList().get(0).isSensor());

        // Repeated open event stays open
        bus.publish("door_x", true);
        stepFrames(2);
        assertTrue(door.isOpen());

        bus.publish("door_x", false);
        stepFrames(2);
        assertFalse(door.isOpen());
        assertFalse(door.getBody().getFixtureList().get(0).isSensor());

        door.dispose();
        assertEquals(0, bus.getSubscriberCount("door_x"));
    }

    @Test
    void testLaserHazardKillsOnlyWhenActiveAndNoDuplicateTransitions() {
        AtomicInteger killCount = new AtomicInteger(0);
        LaserHazard hazard = LaserHazard.create(
                physicsController.getBodyFactory(), 5.0f, 3.0f, 2.0f, 0.5f, killCount::incrementAndGet);
        entityManager.addEntity(hazard);

        // Inactive hazard does not kill
        hazard.setActive(false);
        hazard.onPlayerContactBegin();
        stepFrames(3);
        assertEquals(0, killCount.get());

        // Active hazard kills once per contact
        hazard.setActive(true);
        hazard.onPlayerContactBegin();
        stepFrames(5);
        assertEquals(1, killCount.get());

        // Contact ends and begins again -> kills again
        hazard.onPlayerContactEnd();
        hazard.onPlayerContactBegin();
        stepFrames(2);
        assertEquals(2, killCount.get());

        hazard.dispose();
        assertTrue(hazard.isDisposed());
    }
}
