package com.AnnindaRajDas.gravityshift.physics;

/**
 * Enumeration representing the cardinal directions for directional support
 * detection and future gravity shifts.
 */
public enum Direction {
    DOWN,
    UP,
    LEFT,
    RIGHT;

    /** Cached array of all enum values to avoid per-call array allocations from values(). */
    public static final Direction[] VALUES = values();
}

