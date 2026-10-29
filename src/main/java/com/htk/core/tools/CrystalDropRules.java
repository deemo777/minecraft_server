package com.htk.core.tools;

/** Crystal chance follows the player's related life skill, never the tool's upgrade level. */
public final class CrystalDropRules {
    private CrystalDropRules() {}
    public static double chance(double base,double perLevel,double maximum,int skillLevel) {
        double start=Double.isFinite(base)?Math.clamp(base,0,1):.02;
        double gain=Double.isFinite(perLevel)?Math.clamp(perLevel,0,1):.002;
        double cap=Double.isFinite(maximum)?Math.clamp(maximum,0,1):.12;
        return Math.min(Math.max(start,cap),start+Math.max(0,skillLevel)*gain);
    }
    public static double fishingChance(double chance,double multiplier) {
        double scale=Double.isFinite(multiplier)?Math.max(1,multiplier):3;
        return Math.min(1,Math.clamp(chance,0,1)*scale);
    }
}
