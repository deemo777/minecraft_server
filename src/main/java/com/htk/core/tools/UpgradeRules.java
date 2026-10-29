package com.htk.core.tools;

/** Pure enhancement rules, target level defines both cost and probability. */
public final class UpgradeRules {
    public static final int MAX_LEVEL = 25;
    public static final int[] SUCCESS = {100,95,90,85,80,75,70,65,60,55,50,45,40,35,30,25,20,15,12,10,8,6,5,4,3};
    private UpgradeRules() {}
    public static int required(int target) {
        if (target < 1 || target > MAX_LEVEL) throw new IllegalArgumentException("Target level must be 1..25");
        return (target + 4) / 5;
    }
    public static int success(int target) { required(target); return SUCCESS[target - 1]; }
    public static int floor(int target) { return target>=21?20:target >= 16 ? 15 : target >= 11 ? 10 : 0; }
    public static double downgradeChance(int target) { return target >= 16 ? .5 : target >= 11 ? .3 : 0; }
    public static int resolve(int current, double successRoll, double downgradeRoll, double successChance) {
        int target = current + 1;
        required(target);
        if (successRoll < successChance) return target;
        return downgradeRoll < downgradeChance(target) ? Math.max(floor(target), current - 1) : current;
    }
}
