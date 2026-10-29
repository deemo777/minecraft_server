package com.htk.core.tools;

import java.util.ArrayList;
import java.util.List;

/** Water capacity, enhancement footprint and legacy item migration. */
public final class WateringCanRules {
    private WateringCanRules() {}

    public static boolean infinite(int level) { return level >= 25; }

    public static boolean canWater(int uses, int level) { return infinite(level) || uses > 0; }

    public static int afterUse(int uses, int level) {
        int bounded = Math.clamp(uses, 0, capacity(level));
        return infinite(level) ? bounded : Math.max(0, bounded - 1);
    }

    public static int capacity(int level) {
        return 20 + Math.clamp(level, 0, 20) / 5 * 5;
    }

    public static int remaining(Integer stored, boolean legacyFull, int level) {
        int capacity = capacity(level);
        return stored == null ? (legacyFull ? capacity : 0) : Math.clamp(stored, 0, capacity);
    }

    public record Offset(int x, int z) {}

    public static String areaLabel(int level) {
        return level < 5 ? "1칸" : level < 10 ? "좌우 3칸" : level < 15 ? "3×3" : "4×4";
    }

    /** Yaw snaps to the nearest cardinal direction, independently of looking up/down. */
    public static List<Offset> offsets(int level, float yaw) {
        if (level < 5) return List.of(new Offset(0, 0));
        int direction = Math.floorMod((int) Math.floor((yaw + 45.0) / 90.0), 4);
        int forwardX = switch (direction) { case 1 -> -1; case 3 -> 1; default -> 0; };
        int forwardZ = switch (direction) { case 0 -> 1; case 2 -> -1; default -> 0; };
        int back = level < 10 ? 0 : -1;
        int front = level < 10 ? 0 : level < 15 ? 1 : 2;
        int right = level < 15 ? 1 : 2;
        List<Offset> result = new ArrayList<>();
        for (int side = -1; side <= right; side++) for (int ahead = back; ahead <= front; ahead++) {
            // Right = (-forwardZ, forwardX). 4x4 extends one extra cell forward/right.
            result.add(new Offset(-forwardZ * side + forwardX * ahead,
                    forwardX * side + forwardZ * ahead));
        }
        return List.copyOf(result);
    }
}
