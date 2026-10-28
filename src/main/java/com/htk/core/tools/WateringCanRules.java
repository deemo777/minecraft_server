package com.htk.core.tools;

/** Water capacity and migration from the former empty/full item data. */
public final class WateringCanRules {
    private WateringCanRules() {}

    public static int capacity(int level) {
        return 20 + Math.clamp(level, 0, 20) / 5 * 5;
    }

    public static int remaining(Integer stored, boolean legacyFull, int level) {
        int capacity = capacity(level);
        return stored == null ? (legacyFull ? capacity : 0) : Math.clamp(stored, 0, capacity);
    }
}
