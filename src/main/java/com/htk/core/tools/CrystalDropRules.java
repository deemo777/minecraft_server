package com.htk.core.tools;

/** Crystal curves accept the legacy 0..50 reward scale, including fractional levels.
 * BlacksmithFeature converts displayed 0..100 skill levels via SkillScale.rewardLevel.
 * Never use the tool upgrade level here. */
public final class CrystalDropRules {
    private CrystalDropRules() {}
    public static double chance(double base,double perLevel,double maximum,double skillLevel) {
        double start=Double.isFinite(base)?Math.clamp(base,0,1):.02;
        double gain=Double.isFinite(perLevel)?Math.clamp(perLevel,0,1):.002;
        double cap=Double.isFinite(maximum)?Math.clamp(maximum,0,1):.12;
        return Math.min(Math.max(start,cap),start+Math.max(0,skillLevel)*gain);
    }
    /** Fishing has an independent ten-to-forty-percent curve through skill level 50. */
    public static double fishingChance(double skillLevel) {
        return .10 + .30 * (Math.clamp(skillLevel, 0, 50) / 50.0);
    }
    /** Initial mining rates before any skill milestone. */
    public static double miningBase(String material) {
        String name=material.startsWith("DEEPSLATE_")?material.substring(10):material;
        return switch(name) {
            case "STONE","DEEPSLATE" -> .01;
            case "COAL_ORE","NETHER_GOLD_ORE","NETHER_QUARTZ_ORE","LAPIS_ORE" -> .02;
            case "COPPER_ORE" -> .0125;
            case "IRON_ORE","REDSTONE_ORE" -> .025;
            case "GOLD_ORE" -> .02;
            case "DIAMOND_ORE" -> .15;
            case "EMERALD_ORE","ANCIENT_DEBRIS" -> .50;
            default -> 0;
        };
    }
    public static double miningChance(String material,double base,double perLevel,double maximum,double level) {
        double ore=miningBase(material);
        if(ore==0)return 0;
        // Rare natural ores reach a guaranteed single crystal at mining level 50.
        // Preserve their base rates at level zero and interpolate linearly to 100%.
        if(material.equals("EMERALD_ORE") || material.equals("DEEPSLATE_EMERALD_ORE")
                || material.equals("ANCIENT_DEBRIS")) {
            return ore + (1 - ore) * (Math.clamp(level, 0, 50) / 50.0);
        }
        if(!material.endsWith("DIAMOND_ORE")) {
            // The caller supplies displayed level / 2. Five reward levels = ten displayed levels.
            // Each completed milestone adds 20% of the INITIAL rate, without compounding.
            int milestones=(int)(Math.clamp(level,0,50)/5);
            return ore*(1+.2*milestones);
        }
        double start=Double.isFinite(base)?Math.clamp(base,0,1):.02;
        double cap=Double.isFinite(maximum)?Math.clamp(maximum,0,1):.12;
        // Preserve the existing diamond curve and configurable bonus cap.
        double gain=Double.isFinite(perLevel)?Math.clamp(perLevel,0,1):.002;
        return chance(ore,gain,Math.min(1,ore+Math.max(0,cap-start)),level);
    }
}
