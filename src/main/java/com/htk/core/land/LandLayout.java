package com.htk.core.land;

import java.util.LinkedHashSet;
import java.util.Set;

/** Fixed world-aligned 2x2 plots. No Bukkit dependency. */
final class LandLayout {
    private LandLayout() {}

    static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    static int x(long key) { return (int) (key >> 32); }
    static int z(long key) { return (int) key; }

    static LinkedHashSet<Long> square(int anchorX, int anchorZ, long core) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        result.add(core);
        for (int dz = 0; dz < 2; dz++)
            for (int dx = 0; dx < 2; dx++) result.add(key(anchorX + dx, anchorZ + dz));
        if (result.size() != 4) throw new IllegalArgumentException("Core must lie in the 2x2 square");
        return result;
    }

    /** 0=북서, 1=북동, 2=남동, 3=남서에 집터 청크를 둔다. */
    static LinkedHashSet<Long> fromCorner(int coreX, int coreZ, int corner) {
        int c = Math.floorMod(corner, 4);
        int anchorX = coreX - ((c == 1 || c == 2) ? 1 : 0);
        int anchorZ = coreZ - ((c == 2 || c == 3) ? 1 : 0);
        return square(anchorX, anchorZ, key(coreX, coreZ));
    }

    static boolean isSquare(Set<Long> plot) {
        if (plot.size() != 4) return false;
        int minX = plot.stream().mapToInt(LandLayout::x).min().orElseThrow();
        int minZ = plot.stream().mapToInt(LandLayout::z).min().orElseThrow();
        return plot.containsAll(square(minX, minZ, key(minX, minZ)));
    }

}
