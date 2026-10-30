package com.htk.core.tools;

public final class UpgradeRulesTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int level = 0; level <= 25; level++) {
            check(ToolPerformance.indestructible(level) == (level == 25), "max-tier durability and fire protection");
            check(WateringCanRules.infinite(level) == (level == 25), "only max-tier water is infinite");
            check(WateringCanRules.canWater(0, level) == (level == 25), "empty max-tier can needs no refill");
            check(WateringCanRules.canWater(1, level), "watered can is usable");
            check(WateringCanRules.afterUse(1, level) == (level == 25 ? 1 : 0), "last charge only consumed below max tier");
        }
        int unlimited = 0;
        for (int use = 0; use < 1000; use++) {
            check(WateringCanRules.canWater(unlimited, 25), "unlimited repeated watering without filling");
            unlimited = WateringCanRules.afterUse(unlimited, 25);
        }
        check(!WateringCanRules.canWater(unlimited, 24), "downgrade removes infinite-water access");
        check(!ToolPerformance.indestructible(24) && ToolPerformance.indestructible(25), "protection turns on/off at +25");
        for(int level=0;level<=25;level++) {
            check(WateringCanRules.capacity(level)==20+level,"water capacity increases at every level");
            check(WateringCanRules.remaining(null,true,level)==20+level,"legacy full migration");
            check(WateringCanRules.remaining(0,true,level)==0,"empty stays empty");
            check(WateringCanRules.remaining(999,false,level)==20+level,"overfill clamps");
            for(float yaw:new float[]{0,90,180,270,-90,360,44.9f,45})for(double roll:new double[]{0,.5,.999999}) {
                var water=WateringCanRules.offsets(level,yaw,roll);
                var harvest=HarvestRules.offsets(level,yaw,roll);
                check(water.size()==Progression.count(WateringCanRules.expectedCells(level),roll),"fractional water footprint");
                check(harvest.size()==Progression.count(HarvestRules.expectedCells(level),roll),"fractional harvest footprint");
                check(new java.util.HashSet<>(water).size()==water.size(),"water unique cells");
                check(new java.util.HashSet<>(harvest).size()==harvest.size(),"harvest unique cells");
                check(water.contains(new WateringCanRules.Offset(0,0)),"water contains root");
                check(harvest.contains(new HarvestRules.Offset(0,0)),"harvest contains root");
            }
            if(level>0) {
                check(ToolPerformance.miningSpeed(ToolType.PICKAXE,level)>ToolPerformance.miningSpeed(ToolType.PICKAXE,level-1),"pickaxe improves each level");
                check(ToolPerformance.miningSpeed(ToolType.AXE,level)>ToolPerformance.miningSpeed(ToolType.AXE,level-1),"axe improves each level");
                check(ToolPerformance.wearChance(level)<ToolPerformance.wearChance(level-1),"durability improves each level");
                check(ToolPerformance.fortuneStrength(level)>ToolPerformance.fortuneStrength(level-1),"mining yield improves each level");
                check(ToolPerformance.hoeFortuneStrength(level)>ToolPerformance.hoeFortuneStrength(level-1),"crop yield improves each level");
                check(ToolPerformance.preserveChance(level)>ToolPerformance.preserveChance(level-1),"preservation improves each level");
                check(ToolPerformance.expectedExtraBlocks(level)>ToolPerformance.expectedExtraBlocks(level-1),"connected quota improves each level");
                check(HarvestRules.expectedCells(level)>HarvestRules.expectedCells(level-1),"harvest area improves each level");
                check(WateringCanRules.expectedCells(level)>WateringCanRules.expectedCells(level-1),"water area improves each level");
                check(ToolPerformance.fishingWaitFactor(level)<ToolPerformance.fishingWaitFactor(level-1),"fishing faster each level");
                check(ToolPerformance.fishingExtraChance(level)>ToolPerformance.fishingExtraChance(level-1),"extra fish improves each level");
                for(ToolType type:ToolType.values())check(ToolPerformance.maxDurability(type,level)>ToolPerformance.maxDurability(type,level-1),"maximum durability improves each level");
            }
        }
        check(ToolPerformance.miningSpeed(ToolType.PICKAXE,0)==6 && ToolPerformance.miningSpeed(ToolType.PICKAXE,5)==12 && ToolPerformance.miningSpeed(ToolType.PICKAXE,10)==18,"requested pickaxe anchors");
        check(ToolPerformance.expectedExtraBlocks(25)==4095,"connected safety cap retained");
        check(WateringCanRules.capacity(-1)==20 && WateringCanRules.capacity(999)==45,"bounded water capacity");
        check(WateringCanRules.offsets(25,0,0).size()==25 && HarvestRules.offsets(25,0,0).size()==25,"full 5x5 maximum footprint");
        check(WateringCanRules.offsets(5,0,0).equals(WateringCanRules.offsets(5,360,0)),"water orientation wraps");
        check(HarvestRules.offsets(5,-90,0).equals(HarvestRules.offsets(5,270,0)),"harvest orientation wraps");

        for (int target=1;target<=25;target++) {
            check(UpgradeRules.required(target)==new int[]{0,1,2,4,8}[(target-1)/5], "material cost tier boundary");
            double chance=UpgradeRules.success(target)/100.0;
            check(ArtisanRules.attempt(target-1,chance*100,ArtisanRules.Progress.EMPTY,0).level()==target, "success always advances exactly one");
            if (target>1) {
                check(ArtisanRules.attempt(target-1,chance*100,ArtisanRules.Progress.EMPTY,chance).level()==target-1,"failure threshold is exclusive");
                int expected=target-1;
                check(ArtisanRules.attempt(target-1,chance*100,ArtisanRules.Progress.EMPTY,.999).level()==expected,"failure always retains the current level");
            }
        }
        check(UpgradeRules.success(1)==100 && UpgradeRules.success(10)==65 && UpgradeRules.success(20)==10 && UpgradeRules.success(25)==3,"specified probabilities");
        for (int bad : new int[]{0,26,-1}) {
            boolean failed=false;try{UpgradeRules.required(bad);}catch(IllegalArgumentException ex){failed=true;}
            check(failed,"reject invalid or maxed target");
        }
        for(int target=1;target<=25;target++){
            int required=UpgradeRules.required(target);
            check(UpgradeRules.hasMaterials(target,false,0)==(target<=5),"only first five targets allow an empty material slot");
            check(UpgradeRules.hasMaterials(target,true,required),"exact material cost is accepted");
            if(required>0){
                check(!UpgradeRules.hasMaterials(target,true,required-1),"insufficient materials are rejected");
                check(!UpgradeRules.hasMaterials(target,false,required),"wrong material type is rejected for paid upgrades");
            }
        }
        for(int target=6;target<=15;target++)check(UpgradeRules.success(target)==75-(target-6)*2.5,"+6..15 decrease by 2.5 percentage points");
        var legacy=java.util.List.of(100d,95d,90d,85d,80d,75d,70d,65d,60d,55d,50d,45d,40d,35d,30d,25d,20d,15d,12d,10d,8d,6d,5d,4d,3d);
        check(UpgradeRules.configuredSuccess(7,legacy)==72.5,"existing 25-entry default config adopts new fractional probabilities");
        check(UpgradeRules.configuredSuccess(15,legacy.subList(0,20))==52.5,"existing 20-entry default config migrates too");
        check(UpgradeRules.configuredSuccess(7,java.util.List.of())==72.5,"missing config uses new default");
        var custom=new java.util.ArrayList<>(legacy);custom.set(6,71.25);
        check(UpgradeRules.configuredSuccess(7,custom)==71.25,"custom fractional probability is preserved without integer truncation");
        custom.set(6,Double.NaN);check(UpgradeRules.configuredSuccess(7,custom)==72.5,"non-finite config falls back safely");
        custom.set(6,101d);check(UpgradeRules.configuredSuccess(7,custom)==100,"probability clamps to 100 percent");
        var cyclic=java.util.Map.of(0,java.util.List.of(1,2),1,java.util.List.of(0,3),2,java.util.List.of(0,3),3,java.util.List.of(1,2));
        check(ConnectedBlocks.collect(0,10,cyclic::get).equals(java.util.List.of(0,1,2,3)),"cyclic veins are visited once in nearest-first order");
        check(ConnectedBlocks.collect(0,2,cyclic::get).equals(java.util.List.of(0,1)),"connected traversal respects the exact budget");
        check(ConnectedBlocks.collect(0,0,cyclic::get).isEmpty(),"zero budget does not inspect a node");
        check(ConnectedBlocks.collect(0,10,n->n==0?java.util.List.of(1):java.util.List.of()).equals(java.util.List.of(0,1)),"excluded neighbors cannot bridge into other resources");
        check(ConnectedBlocks.collect(0,4096,n->java.util.List.of(n+1)).size()==4096,"even an unbounded vein stops at the safety cap");
        for(int lv=0;lv<=25;lv++){
            check(ToolType.PICKAXE.materialAt(lv)==(lv<5?org.bukkit.Material.IRON_PICKAXE:lv<20?org.bukkit.Material.DIAMOND_PICKAXE:org.bukkit.Material.NETHERITE_PICKAXE),"pickaxe base material matches milestone");
            check(ToolType.AXE.materialAt(lv)==(lv<5?org.bukkit.Material.IRON_AXE:lv<20?org.bukkit.Material.DIAMOND_AXE:org.bukkit.Material.NETHERITE_AXE),"axe base material matches milestone");
            check(ToolType.HOE.materialAt(lv)==(lv<5?org.bukkit.Material.IRON_HOE:lv<20?org.bukkit.Material.DIAMOND_HOE:org.bukkit.Material.NETHERITE_HOE),"hoe base material matches milestone");
            check(ToolType.FISHING_ROD.materialAt(lv)==org.bukkit.Material.FISHING_ROD,"fishing rod material preserved");
            check(ToolType.WATERING_CAN.materialAt(lv)==org.bukkit.Material.CARROT_ON_A_STICK,"watering can material preserved");
            check(ToolPerformance.wearChance(lv)>=0 && ToolPerformance.wearChance(lv)<=1,"bounded numeric wear chance");
            check(lv<25 || ToolPerformance.indestructible(lv),"last tier uses infinite durability instead of unbreaking");
        }
        check(ToolDurability.convertedDamage(125,250,1561)==780,"iron-to-diamond migration preserves half wear");
        check(ToolDurability.convertedDamage(780,1561,2031)==1014,"diamond-to-netherite migration preserves wear without free repair");
        check(ToolDurability.convertedDamage(1014,2031,1561)==779,"downgrade rescales wear to diamond");
        check(ToolDurability.convertedDamage(200,1300,1300)==200,"custom maximum durability is preserved during material conversion");
        check(ToolDurability.convertedDamage(0,1561,2031)==0,"new undamaged tool stays undamaged");
        check(ToolDurability.convertedDamage(9999,250,1561)==1560,"invalid old damage cannot create a broken destination");
        check(ToolDurability.convertedDamage(-1,250,1561)==0,"negative damage is clamped");
        check(ToolDurability.convertedDamage(200,0,100)==99 && ToolDurability.convertedDamage(200,100,0)==0,"invalid maximum durability boundaries");
        check(ToolDurability.convertedDamage(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE)==Integer.MAX_VALUE-1,"large values do not overflow");
        System.out.println("Enhancement rule regression checks passed: "+checks);
    }
}
