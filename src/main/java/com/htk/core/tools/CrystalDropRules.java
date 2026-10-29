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
    /** Fishing has an independent ten-to-forty-percent curve through skill level 50. */
    public static double fishingChance(int skillLevel) {
        return .10 + .30 * (Math.clamp(skillLevel, 0, 50) / 50.0);
    }
    /** Ordinary stone is a fixed exception; only actual ores receive skill growth. */
    public static double miningBase(String material) {
        String name=material.startsWith("DEEPSLATE_")?material.substring(10):material;
        return switch(name) {
            case "STONE","DEEPSLATE" -> .02;
            case "COAL_ORE","NETHER_GOLD_ORE","NETHER_QUARTZ_ORE","LAPIS_ORE" -> .08;
            case "COPPER_ORE" -> .05;
            case "IRON_ORE","REDSTONE_ORE" -> .10;
            case "GOLD_ORE" -> .08;
            case "DIAMOND_ORE" -> .15;
            case "EMERALD_ORE","ANCIENT_DEBRIS" -> .50;
            default -> 0;
        };
    }
    public static double miningChance(String material,double base,double perLevel,double maximum,int level) {
        double ore=miningBase(material);
        if(ore==0)return 0;
        if(material.equals("STONE") || material.equals("DEEPSLATE"))return .02;
        // Rare natural ores reach a guaranteed single crystal at mining level 50.
        // Preserve their base rates at level zero and interpolate linearly to 100%.
        if(material.equals("EMERALD_ORE") || material.equals("DEEPSLATE_EMERALD_ORE")
                || material.equals("ANCIENT_DEBRIS")) {
            return ore + (1 - ore) * (Math.clamp(level, 0, 50) / 50.0);
        }
        double start=Double.isFinite(base)?Math.clamp(base,0,1):.02;
        double cap=Double.isFinite(maximum)?Math.clamp(maximum,0,1):.12;
        return chance(ore,perLevel,Math.min(1,ore+Math.max(0,cap-start)),level);
    }
}
