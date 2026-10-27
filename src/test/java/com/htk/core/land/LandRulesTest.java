package com.htk.core.land;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dependency-free regression checks executed by Gradle check. */
public final class LandRulesTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long core = LandLayout.key(0, 0);
        var plot = LandLayout.square(0, 0, core);
        check(plot.size() == 4 && LandLayout.isSquare(plot), "exactly a 2x2 reservation");
        check(new ArrayList<>(plot).equals(List.of(core, LandLayout.key(1, 0),
                LandLayout.key(0, 1), LandLayout.key(1, 1))), "deterministic unlock order");
        for (int x : new int[]{-100, -1, 0, 1, 100}) {
            for (int z : new int[]{-100, -1, 0, 1, 100}) {
                long key = LandLayout.key(x, z);
                check(LandLayout.x(key) == x && LandLayout.z(key) == z, "signed chunk round trip");
                check(LandLayout.isSquare(LandLayout.square(x, z, key)), "negative-coordinate plot");
            }
        }
        var neighbor = LandLayout.square(2, 0, LandLayout.key(2, 0));
        check(java.util.Collections.disjoint(plot, neighbor), "touching 2x2 plots need no empty gap");
        check(!java.util.Collections.disjoint(plot, LandLayout.square(1, 0, LandLayout.key(1, 0))),
                "overlapping footprints must be rejected including locked chunks");
        for (int ox = -1; ox <= 0; ox++) for (int oz = -1; oz <= 0; oz++) {
            var candidate = LandLayout.square(ox, oz, core);
            check(candidate.iterator().next() == core && candidate.size() == 4,
                    "legacy migration keeps the core first for each corner");
        }
        Set<Long> irregular = new HashSet<>(plot);
        irregular.remove(LandLayout.key(1, 1));
        irregular.add(LandLayout.key(2, 0));
        check(!LandLayout.isSquare(irregular), "irregular legacy claims are not silently shrunk");
        boolean rejected = false;
        try { LandLayout.square(5, 5, core); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid off-footprint core rejected");
        LandStoreRegression.run();
        System.out.println("Land layout regression checks passed: " + checks);
    }
}
