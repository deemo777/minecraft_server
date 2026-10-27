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
        for (int x : new int[]{-17,-1,0,15}) for (int z : new int[]{-17,-1,0,15}) {
            Set<Set<Long>> variants=new HashSet<>();
            for (int corner=0;corner<4;corner++) {
                var selected=LandLayout.fromCorner(x,z,corner);
                check(LandLayout.isSquare(selected) && selected.iterator().next()==LandLayout.key(x,z), "selected corner keeps core first in a square");
                int ax=selected.stream().mapToInt(LandLayout::x).min().orElseThrow();
                int az=selected.stream().mapToInt(LandLayout::z).min().orElseThrow();
                check(x-ax==((corner==1||corner==2)?1:0) && z-az==((corner==2||corner==3)?1:0), "selected corner matches world axes");
                variants.add(selected);
            }
            check(variants.size()==4,"four distinct selectable footprints");
            check(LandLayout.fromCorner(x,z,4).equals(LandLayout.fromCorner(x,z,0)),"corner selection wraps");
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
