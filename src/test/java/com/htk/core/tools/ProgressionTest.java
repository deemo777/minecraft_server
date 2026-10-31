package com.htk.core.tools;

public final class ProgressionTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        double[] first={6,7.2,8.4,9.6,10.8,12,13.2,14.4,15.6,16.8,18};
        for(int l=0;l<first.length;l++)check(Math.abs(ToolPerformance.miningSpeed(ToolType.PICKAXE,l)-first[l])<1e-10,"Exact requested early pickaxe curve");
        for(int l=0;l<=25;l++) {
            for(double expected:new double[]{HarvestRules.expectedCells(l),WateringCanRules.expectedCells(l),ToolPerformance.fortuneStrength(l),ToolPerformance.hoeFortuneStrength(l)}) {
                double sum=0;
                for(int draw=0;draw<10_000;draw++)sum+=Progression.count(expected,(draw+.5)/10_000);
                check(Math.abs(sum/10_000-expected)<.00011,"Fractional count mean is unbiased at every level");
            }
        }
        check(ToolPerformance.expectedExtraBlocks(5)==1,"+5 pickaxe mines one additional connected ore, independently of mining skill");
        int singles=0,doubles=0;
        // Exhaust all equally likely fortune-strength (0/1) and ore-roll outcomes.
        for(double fortuneRoll:new double[]{.25,.75})for(int oreRoll=0;oreRoll<6;oreRoll++) {
            int fortune=ToolPerformance.rolledFortune(5,fortuneRoll);
            final int draw=oreRoll;
            int count=NumericEffects.oreMultiplier(fortune,new java.util.Random(){
                @Override public int nextInt(int bound){return draw%bound;}
            });
            if(count==1)singles++;else if(count==2)doubles++;else throw new AssertionError("Unexpected +5 ore multiplier");
        }
        check(singles==10 && doubles==2,"+5 single-drop ores: one item 5/6, two items 1/6; never guaranteed double");
        check(ToolPerformance.fishingWaitFactor(0)==1 && ToolPerformance.fishingWaitFactor(25)==.5,"Fishing speed endpoints");
        check(ToolPerformance.fishingExtraChance(0)==0 && ToolPerformance.fishingExtraChance(25)==.25,"Extra fish endpoints");
        check(ToolPerformance.fishingWaitFactor(-1)==1 && ToolPerformance.fishingExtraChance(999)==.25,"Fishing levels clamp");
        for(var fish:new org.bukkit.Material[]{org.bukkit.Material.COD,org.bukkit.Material.SALMON,org.bukkit.Material.PUFFERFISH,org.bukkit.Material.TROPICAL_FISH})check(NumericFishing.eligible(fish),"Ordinary fish receive bonus");
        for(var treasure:new org.bukkit.Material[]{org.bukkit.Material.ENCHANTED_BOOK,org.bukkit.Material.BOW,org.bukkit.Material.FISHING_ROD,org.bukkit.Material.NAME_TAG,org.bukkit.Material.LEATHER_BOOTS})check(!NumericFishing.eligible(treasure),"Treasure and junk never receive rod duplication");
        check(ToolPerformance.expectedExtraBlocks(24)<4095 && ToolPerformance.expectedExtraBlocks(25)==4095,"Connected maximum remains bounded");
        check(ToolPerformance.maxDurability(ToolType.PICKAXE,0)==250 && ToolPerformance.maxDurability(ToolType.PICKAXE,25)==2031,"Tool durability endpoints");
        check(ToolDurability.convertedDamage(500,1000,1200)==600,"Maximum durability changes preserve wear ratio");
        System.out.println("Per-level progression and fishing balance checks passed: "+checks);
    }
}
