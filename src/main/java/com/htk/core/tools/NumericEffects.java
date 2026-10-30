package com.htk.core.tools;

import java.util.random.RandomGenerator;

/** Pure numeric effects; no enchantment objects or enchanted proxy items. */
public final class NumericEffects {
    private NumericEffects() {}
    public static double miningBonus(int strength) { return strength <= 0 ? 0 : strength * strength + 1; }
    public static double damageBonus(int strength) { return strength <= 0 ? 0 : .5 * strength + .5; }
    public static double sweepRatio(int strength) { return strength <= 0 ? 0 : (double) strength / (strength + 1); }
    public static int wear(int incoming, int conservation, RandomGenerator random) {
        int result = 0;
        for (int i=0; i<incoming; i++) if (random.nextInt(conservation + 1) == 0) result++;
        return result;
    }
    public static int oreMultiplier(int strength, RandomGenerator random) {
        return Math.max(1, random.nextInt(strength + 2));
    }
    public static int binomial(int trials, double probability, RandomGenerator random) {
        int result=0;
        for(int i=0;i<trials;i++) if(random.nextDouble()<probability)result++;
        return result;
    }
}
