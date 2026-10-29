package com.htk.core.tools;

/** One gold per restored durability point, independent of the tool's maximum durability. */
public final class RepairRules {
    private RepairRules() {}
    public static long cost(int damage, int maximum) {
        if (maximum <= 0 || damage <= 0) return 0;
        return Math.clamp(damage, 0, maximum);
    }
}
