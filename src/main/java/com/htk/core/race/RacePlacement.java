package com.htk.core.race;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Bounded nearby search; the predicate checks only already-loaded terrain. No world edits. */
final class RacePlacement {
    private RacePlacement() {}
    static List<Location> candidates(Location player) {
        Location center = player.clone();
        center.setX(player.getBlockX() + 0.5); center.setY(player.getBlockY()); center.setZ(player.getBlockZ() + 0.5);
        center.setPitch(0); center.setYaw(Math.round(player.getYaw() / 90f) * 90f);
        List<Location> result = new ArrayList<>();
        for (int height : new int[]{0, -1, 1}) for (int side : new int[]{0, -8, 8, -16, 16})
            for (int turn : new int[]{0, 90, -90, 180}) {
                Location origin = center.clone(); origin.setYaw(center.getYaw() + turn);
                result.add(RaceScene.point(origin, 2, side).add(0, height, 0));
            }
        return List.copyOf(result);
    }
    static Location find(Location player, Predicate<Location> usable) {
        for (Location candidate : candidates(player)) if (usable.test(candidate)) return candidate;
        return null; // The shared GUI still works if a physical track cannot fit.
    }
}
