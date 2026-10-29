package com.htk.core.tools;

/** Upgrade-tier service fee plus one gold per restored durability point. */
public final class RepairRules {
    private RepairRules() {}
    public static long baseCost(int level) {
        return level >= 25 ? 10_000 : level >= 20 ? 4_000 : level >= 15 ? 1_000 : level >= 10 ? 500 : 0;
    }
    public static long cost(int damage, int maximum, int level) {
        if (maximum <= 0 || damage <= 0) return 0;
        return baseCost(level) + Math.clamp(damage, 0, maximum);
    }
}
