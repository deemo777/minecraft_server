package com.htk.core.tools;

/** Proportional durability repair, rounded up to whole gold. */
public final class RepairRules {
    private RepairRules() {}
    public static long cost(int damage, int maximum, long fullCost) {
        if (maximum <= 0 || damage <= 0) return 0;
        long cap = Math.clamp(fullCost, 0, 1_000_000_000L);
        long used = Math.clamp(damage, 0, maximum);
        return (cap * used + maximum - 1L) / maximum;
    }
}
