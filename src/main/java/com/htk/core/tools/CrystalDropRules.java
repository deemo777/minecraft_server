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
    /** Ordinary stone is a fixed exception; only actual ores receive skill growth. */
    public static double miningBase(String material) {
        String name=material.startsWith("DEEPSLATE_")?material.substring(10):material;
        return switch(name) {
            case "STONE","DEEPSLATE" -> .02;
            case "COAL_ORE","NETHER_GOLD_ORE","NETHER_QUARTZ_ORE" -> .04;
            case "COPPER_ORE" -> .05;
            case "IRON_ORE" -> .06;
            case "GOLD_ORE","REDSTONE_ORE" -> .08;
            case "LAPIS_ORE" -> .10;
            case "DIAMOND_ORE","EMERALD_ORE" -> .15;
            case "ANCIENT_DEBRIS" -> .20;
            default -> 0;
        };
    }
    public static double miningChance(String material,double base,double perLevel,double maximum,int level) {
        double ore=miningBase(material);
        if(ore==0)return 0;
        if(material.equals("STONE") || material.equals("DEEPSLATE"))return .02;
        double start=Double.isFinite(base)?Math.clamp(base,0,1):.02;
        double cap=Double.isFinite(maximum)?Math.clamp(maximum,0,1):.12;
        return chance(ore,perLevel,Math.min(1,ore+Math.max(0,cap-start)),level);
    }
}
