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

    /** Private cached array of all enum values to avoid per-call array allocations from values(). */
    private static final Direction[] VALUES = values();

    /** Returns the number of cardinal directions without allocating an array. */
    public static int count() {
        return VALUES.length;
    }

    /** Returns the cardinal direction at the given ordinal index without allocating an array. */
    public static Direction fromIndex(int index) {
        return VALUES[index];
    }
}

