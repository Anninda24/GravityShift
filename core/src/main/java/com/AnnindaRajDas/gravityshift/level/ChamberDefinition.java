package com.AnnindaRajDas.gravityshift.level;

import java.util.Collections;
import java.util.List;

/**
 * Immutable data model for a single 20m x 12m puzzle chamber loaded from TMX.
 */
public class ChamberDefinition {

    public static final class RectSpec {
        public final float x;
        public final float y;
        public final float width;
        public final float height;

        public RectSpec(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final class CubeSpec {
        public final float x;
        public final float y;

        public CubeSpec(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class PlateSpec {
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final String channelId;

        public PlateSpec(float x, float y, float width, float height, String channelId) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.channelId = channelId;
        }
    }

    public static final class DoorSpec {
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final String channelId;

        public DoorSpec(float x, float y, float width, float height, String channelId) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.channelId = channelId;
        }
    }

    public static final class HazardSpec {
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final String channelId;
        public final boolean initiallyActive;

        public HazardSpec(float x, float y, float width, float height, String channelId, boolean initiallyActive) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.channelId = channelId;
            this.initiallyActive = initiallyActive;
        }
    }

    private final int chamberNumber;
    private final String name;
    private final float spawnX;
    private final float spawnY;
    private final RectSpec exit;
    private final List<RectSpec> solids;
    private final List<CubeSpec> cubes;
    private final List<PlateSpec> plates;
    private final List<DoorSpec> doors;
    private final List<HazardSpec> hazards;

    public ChamberDefinition(
            int chamberNumber,
            String name,
            float spawnX,
            float spawnY,
            RectSpec exit,
            List<RectSpec> solids,
            List<CubeSpec> cubes,
            List<PlateSpec> plates,
            List<DoorSpec> doors,
            List<HazardSpec> hazards) {
        this.chamberNumber = chamberNumber;
        this.name = name;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.exit = exit;
        this.solids = Collections.unmodifiableList(solids);
        this.cubes = Collections.unmodifiableList(cubes);
        this.plates = Collections.unmodifiableList(plates);
        this.doors = Collections.unmodifiableList(doors);
        this.hazards = Collections.unmodifiableList(hazards);
    }

    public int getChamberNumber() { return chamberNumber; }
    public String getName() { return name; }
    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public RectSpec getExit() { return exit; }
    public List<RectSpec> getSolids() { return solids; }
    public List<CubeSpec> getCubes() { return cubes; }
    public List<PlateSpec> getPlates() { return plates; }
    public List<DoorSpec> getDoors() { return doors; }
    public List<HazardSpec> getHazards() { return hazards; }
}
