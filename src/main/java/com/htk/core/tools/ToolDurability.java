package com.htk.core.tools;

/** Preserve wear when an existing tool migrates between iron, diamond and netherite. */
final class ToolDurability {
    private ToolDurability() {}
    static int convertedDamage(int damage, int oldMaximum, int newMaximum) {
        if (newMaximum <= 1) return 0;
        if (oldMaximum <= 0) return Math.clamp(damage, 0, newMaximum - 1);
        return (int) Math.min(newMaximum - 1L, (long) Math.max(0, damage) * newMaximum / oldMaximum);
    }
}
