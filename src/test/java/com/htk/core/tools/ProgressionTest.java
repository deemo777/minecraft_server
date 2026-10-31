package com.htk.core.tools;

public final class ProgressionTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        double[] first={6,6.4,6.8,7.2,7.6,8,10,12,14,16,18};
        for(int l=0;l<first.length;l++)check(Math.abs(ToolPerformance.miningSpeed(ToolType.PICKAXE,l)-first[l])<1e-10,"Exact requested early pickaxe curve");
        for(int l=0;l<=25;l++) {
            for(double expected:new double[]{HarvestRules.expectedCells(l),WateringCanRules.expectedCells(l),ToolPerformance.fortuneStrength(l),ToolPerformance.hoeFortuneStrength(l)}) {
                double sum=0;
                for(int draw=0;draw<10_000;draw++)sum+=Progression.count(expected,(draw+.5)/10_000);
                check(Math.abs(sum/10_000-expected)<.00011,"Fractional count mean is unbiased at every level");
            }
        }
        for(int level=0;level<=25;level++) {
            check(ToolPerformance.extraBlocks(ToolType.PICKAXE,level)==Math.max(0,level-20),"Exact connected ore quota; +21..25 totals 2..6 blocks");
            check(level<20 || ToolPerformance.fortuneStrength(level)<=4,"Fortune never exceeds IV");
            check(level>5 || ToolPerformance.fortuneStrength(level)==0,"Pure diamond through +5");
        }
        check(ToolPerformance.fortuneStrength(10)==1 && ToolPerformance.fortuneStrength(15)==3
                && ToolPerformance.fortuneStrength(20)==3.5 && ToolPerformance.fortuneStrength(25)==4,"Fortune milestones");
        check(Math.abs(ToolPerformance.fortuneStrength(11)-1.4)<1e-10,"Intermediate fortune interpolation");
        check(ToolPerformance.fishingWaitFactor(0)==1 && ToolPerformance.fishingWaitFactor(25)==.5,"Fishing speed endpoints");
        check(ToolPerformance.fishingExtraChance(0)==0 && ToolPerformance.fishingExtraChance(25)==1,"Extra fish endpoints");
        check(ToolPerformance.fishingWaitFactor(-1)==1 && ToolPerformance.fishingExtraChance(999)==1,"Fishing levels clamp");
        for(var fish:new org.bukkit.Material[]{org.bukkit.Material.COD,org.bukkit.Material.SALMON,org.bukkit.Material.PUFFERFISH,org.bukkit.Material.TROPICAL_FISH})check(NumericFishing.eligible(fish),"Ordinary fish receive bonus");
        for(var treasure:new org.bukkit.Material[]{org.bukkit.Material.ENCHANTED_BOOK,org.bukkit.Material.BOW,org.bukkit.Material.FISHING_ROD,org.bukkit.Material.NAME_TAG,org.bukkit.Material.LEATHER_BOOTS})check(!NumericFishing.eligible(treasure),"Treasure and junk never receive rod duplication");
        check(ToolPerformance.expectedWoodExtraBlocks(24)==6 && ToolPerformance.expectedWoodExtraBlocks(25)==7,"Connected maximum remains bounded");
        check(ToolPerformance.maxDurability(ToolType.PICKAXE,0)==250 && ToolPerformance.maxDurability(ToolType.PICKAXE,25)==10155,"Tool durability endpoints");
        check(ToolDurability.convertedDamage(500,1000,1200)==600,"Maximum durability changes preserve wear ratio");
        for(var crop:new org.bukkit.Material[]{org.bukkit.Material.WHEAT,org.bukkit.Material.CARROTS,org.bukkit.Material.POTATOES,org.bukkit.Material.BEETROOTS,org.bukkit.Material.NETHER_WART,org.bukkit.Material.MELON,org.bukkit.Material.PUMPKIN,org.bukkit.Material.SUGAR_CANE,org.bukkit.Material.COCOA})check(NumericDrops.crop(crop),"Crop receives hoe multiplier");
        for(var other:new org.bukkit.Material[]{org.bukkit.Material.DIAMOND_ORE,org.bukkit.Material.OAK_LEAVES,org.bukkit.Material.GRAVEL})check(!NumericDrops.crop(other),"Hoe cannot multiply ores, leaves or gravel");
        int[] logs={0,0,0,0,0,0,0,0,0,0,1,1,2,2,3,3,3,3,4,4,4,4,5,5,6,7};
        for(int l=0;l<=25;l++) {
            check(ToolPerformance.extraBlocks(ToolType.AXE,l)==logs[l],"Exact deterministic connected-log quota");
            int cells=(int)Math.round(1+9*l/25.0);
            for(double roll:new double[]{0,.01,.5,.999999}) {
                check(HarvestRules.offsets(l,0,roll).size()==cells,"Harvest cells ignore randomness");
                check(WateringCanRules.offsets(l,0,roll).size()==cells,"Water cells ignore randomness");
            }
            int total=0;
            for(int draw=0;draw<10_000;draw++)total+=ToolPerformance.cropMultiplier(l,(draw+.5)/10_000);
            check(Math.abs(total/10_000.0-(1+ToolPerformance.hoeFortuneStrength(l)))<.00011,"Crop multiplier distribution at every level");
        }
        check(ToolPerformance.cropMultiplier(14,.999)==2,"+14 always doubles");
        check(ToolPerformance.cropMultiplier(15,0)==3 && ToolPerformance.cropMultiplier(15,.999)==2,"+15 doubles or triples");
        check(ToolPerformance.hoeFortuneStrength(15)>ToolPerformance.hoeFortuneStrength(14),"+15 has greater expected yield than +14");
        check(ToolPerformance.fishingExtraChance(24)==.5,"+24 extra fish chance is 50 percent");
        System.out.println("Per-level progression and fishing balance checks passed: "+checks);
    }
}
