package com.htk.core.tools;

/** Crystal curves accept the legacy 0..50 reward scale, including fractional levels.
 * BlacksmithFeature converts displayed 0..100 skill levels via SkillScale.rewardLevel.
 * Never use the tool upgrade level here. */
public final class CrystalDropRules {
    private CrystalDropRules() {}
    /** Five legacy reward levels are ten displayed levels. All crystal growth shares these steps. */
    private static double milestoneLevel(double level) {
        return Double.isNaN(level)?0:Math.floor(Math.clamp(level,0,50)/5)*5;
    }
    public static double chance(double base,double perLevel,double maximum,double skillLevel) {
        double start=Double.isFinite(base)?Math.clamp(base,0,1):.02;
        double gain=Double.isFinite(perLevel)?Math.clamp(perLevel,0,1):.002;
        double cap=Double.isFinite(maximum)?Math.clamp(maximum,0,1):.12;
        return Math.min(Math.max(start,cap),start+milestoneLevel(skillLevel)*gain);
    }
    /** Shared weapon crystals: 8% base, +2 percentage points per ten combat levels, 28% at 100. */
    public static double weaponChance(double skillLevel) {
        return (8 + 2 * (milestoneLevel(skillLevel)/5)) / 100.0;
    }
    /** Fishing crystals follow the same 8..28% ten-level curve as weapons. */
    public static double fishingChance(double skillLevel) {
        return weaponChance(skillLevel);
    }
    /** Axe crystals: 4% base, +2 percentage points per ten woodcutting levels, 24% at 100. */
    public static double axeChance(double skillLevel) {
        return (4 + 2 * (milestoneLevel(skillLevel)/5)) / 100.0;
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
        // Rare ores retain 50% base and 100% at displayed level 100, in ten-level steps.
        if(material.equals("EMERALD_ORE") || material.equals("DEEPSLATE_EMERALD_ORE")
                || material.equals("ANCIENT_DEBRIS")) {
            return ore + (1 - ore) * (milestoneLevel(level) / 50.0);
        }
        if(!material.endsWith("DIAMOND_ORE")) {
            // The caller supplies displayed level / 2. Five reward levels = ten displayed levels.
            // Each completed milestone adds 20% of the INITIAL rate, without compounding.
            int milestones=(int)(milestoneLevel(level)/5);
            return ore*(1+.2*milestones);
        }
        double start=Double.isFinite(base)?Math.clamp(base,0,1):.02;
        double cap=Double.isFinite(maximum)?Math.clamp(maximum,0,1):.12;
        // Preserve diamond's base and final probability; chance() applies the shared milestones.
        double gain=Double.isFinite(perLevel)?Math.clamp(perLevel,0,1):.002;
        return chance(ore,gain,Math.min(1,ore+Math.max(0,cap-start)),level);
    }
}
