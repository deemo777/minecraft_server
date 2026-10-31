package com.htk.core.skill;

/** One full heart (two health points) per five combat levels, capped at 100. */
public final class CombatHealth {
    private CombatHealth() {}
    public static double bonus(int level) { return 2 * (Math.clamp(level, 0, 100) / 5); }
}
