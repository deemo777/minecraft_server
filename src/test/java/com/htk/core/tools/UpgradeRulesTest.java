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
        for (int level = 0; level <= 25; level++) {
            int count = level < 5 ? 1 : level < 10 ? 3 : level < 15 ? 9 : 16;
            for (float yaw : new float[]{0, 90, 180, 270, -90, 360, 44.9f, 45, -45}) {
                var cells = WateringCanRules.offsets(level, yaw);
                check(cells.size() == count, "water footprint count at level " + level);
                check(new java.util.HashSet<>(cells).size() == count, "no duplicate watering targets");
                check(cells.contains(new WateringCanRules.Offset(0, 0)), "clicked soil always included");
                int width = cells.stream().mapToInt(WateringCanRules.Offset::x).max().orElseThrow()
                        - cells.stream().mapToInt(WateringCanRules.Offset::x).min().orElseThrow() + 1;
                int depth = cells.stream().mapToInt(WateringCanRules.Offset::z).max().orElseThrow()
                        - cells.stream().mapToInt(WateringCanRules.Offset::z).min().orElseThrow() + 1;
                check(width * depth == count, "footprint is a filled rectangle");
                if (level >= 10) check(width == depth, "high-tier watering is square");
            }
        }
        var southRow = java.util.Set.of(new WateringCanRules.Offset(-1, 0),
                new WateringCanRules.Offset(0, 0), new WateringCanRules.Offset(1, 0));
        var westRow = java.util.Set.of(new WateringCanRules.Offset(0, -1),
                new WateringCanRules.Offset(0, 0), new WateringCanRules.Offset(0, 1));
        check(new java.util.HashSet<>(WateringCanRules.offsets(5, 0)).equals(southRow), "south-facing left/right row");
        check(new java.util.HashSet<>(WateringCanRules.offsets(5, 90)).equals(westRow), "west-facing left/right row");
        check(WateringCanRules.offsets(5, -90).equals(WateringCanRules.offsets(5, 270)), "negative yaw wraps");
        check(WateringCanRules.offsets(5, 0).equals(WateringCanRules.offsets(5, 360)), "full-turn yaw wraps");
        check(WateringCanRules.offsets(5, 44.9f).equals(WateringCanRules.offsets(5, 0)), "diagonal lower boundary");
        check(WateringCanRules.offsets(5, 45).equals(WateringCanRules.offsets(5, 90)), "diagonal upper boundary");
        var southSquare = WateringCanRules.offsets(15, 0);
        check(southSquare.contains(new WateringCanRules.Offset(-2, 2))
                && !southSquare.contains(new WateringCanRules.Offset(2, 0))
                && !southSquare.contains(new WateringCanRules.Offset(0, -2)), "4x4 extends forward/right");
        for (int yaw = 0; yaw < 360; yaw += 90)
            check(WateringCanRules.offsets(15, yaw).equals(WateringCanRules.offsets(20, yaw)), "+20 keeps +15 range");
        check(WateringCanRules.areaLabel(0).equals("1칸") && WateringCanRules.areaLabel(5).equals("좌우 3칸")
                && WateringCanRules.areaLabel(10).equals("3×3") && WateringCanRules.areaLabel(15).equals("4×4")
                && WateringCanRules.areaLabel(20).equals("4×4"), "item lore matches tier areas");
        for (int level = 0; level <= 25; level++) {
            int capacity = level < 5 ? 20 : level < 10 ? 25 : level < 15 ? 30 : level < 20 ? 35 : 40;
            check(WateringCanRules.capacity(level) == capacity, "water capacity at level " + level);
            check(WateringCanRules.remaining(null, true, level) == capacity, "legacy full can migration");
            check(WateringCanRules.remaining(null, false, level) == 0, "new/legacy empty can stays empty");
            check(WateringCanRules.remaining(7, true, level) == 7, "counter overrides legacy full flag");
            check(WateringCanRules.remaining(0, true, level) == 0, "empty counter cannot resurrect old water");
            check(WateringCanRules.remaining(-5, false, level) == 0, "negative water count clamps");
            check(WateringCanRules.remaining(999, false, level) == capacity, "overfilled count clamps");
        }
        check(WateringCanRules.capacity(-1) == 20 && WateringCanRules.capacity(999) == 40, "water capacity level bounds");
        check(WateringCanRules.remaining(20, false, 5) == 20, "upgrade preserves remaining water without refilling");
        check(WateringCanRules.remaining(40, false, 0) == 20, "downgrade caps water to new capacity");
        check(ToolPerformance.efficiency(0)==0&&ToolPerformance.fortune(0)==0,"base iron has no bonus");
        check(ToolPerformance.efficiency(5)==0&&ToolPerformance.efficiency(10)==3&&ToolPerformance.efficiency(15)==5&&ToolPerformance.efficiency(20)==6&&ToolPerformance.efficiency(25)==7,"efficiency anchor levels");
        check(ToolPerformance.fortune(10)==1&&ToolPerformance.fortune(15)==3&&ToolPerformance.fortune(20)==4,"fortune anchor levels");
        for(int lv=1;lv<=25;lv++) {
            check(ToolPerformance.efficiency(lv)>=ToolPerformance.efficiency(lv-1),"efficiency monotonic");
            check(ToolPerformance.fortune(lv)>=ToolPerformance.fortune(lv-1),"fortune monotonic");
            if(lv>=20)check(ToolPerformance.fortune(lv)==4,"fortune capped from +20");
            check(ToolPerformance.axeEfficiency(lv)>=ToolPerformance.axeEfficiency(lv-1),"axe speed monotonic");
        }
        check(ToolPerformance.axeEfficiency(0)==0&&ToolPerformance.axeEfficiency(5)==0&&ToolPerformance.axeEfficiency(10)==4
            &&ToolPerformance.axeEfficiency(15)==5&&ToolPerformance.axeEfficiency(20)==6&&ToolPerformance.axeEfficiency(25)==7,"axe milestone speeds");
        check(!ToolPerformance.silkTouch(14)&&ToolPerformance.silkTouch(15),"silk touch starts at +15");
        check(ToolPerformance.hoeFortune(19)==0&&ToolPerformance.hoeFortune(20)==2&&ToolPerformance.hoeFortune(25)==3,"hoe fortune milestones");
        for(int lv=10;lv<15;lv++){
            check(ToolPerformance.rolledFortune(lv,0)==2&&ToolPerformance.rolledFortune(lv,.49999)==2,"upper fortune half");
            check(ToolPerformance.rolledFortune(lv,.5)==1&&ToolPerformance.rolledFortune(lv,.999)==1,"lower fortune half");
        }
        check(ToolPerformance.rolledFortune(9,0)==0&&ToolPerformance.rolledFortune(15,0)==3,"fractional fortune limited to +10..14");
        check(ToolPerformance.extraBlocks(19)==0,"no connected breaking below +20");
        for(int lv=20;lv<25;lv++)check(ToolPerformance.extraBlocks(lv)==lv-19,"one additional resource per level from +20");
        check(ToolPerformance.extraBlocks(25)==4095,"full connected vein/tree uses a bounded batch");
        for(int lv=0;lv<=25;lv++)for(float yaw:new float[]{0,90,180,270,-90,360,44.9f,45}){
            var cells=HarvestRules.offsets(lv,yaw);int expected=lv<5?1:lv<10?3:lv<15?5:lv<25?8:25;
            check(cells.size()==expected,"hoe sweep target count");
            check(new java.util.HashSet<>(cells).size()==expected,"hoe sweep has no duplicates");
            check(cells.contains(new HarvestRules.Offset(0,0)),"hoe sweep includes center");
        }
        check(HarvestRules.offsets(15,0).contains(new HarvestRules.Offset(1,1))
            &&!HarvestRules.offsets(15,0).contains(new HarvestRules.Offset(2,1)),"eight-cell footprint has five in clicked row, three forward");
        check(HarvestRules.offsets(25,0).contains(new HarvestRules.Offset(-2,-2))
            &&HarvestRules.offsets(25,0).contains(new HarvestRules.Offset(2,2)),"max hoe covers centered 5x5");
        check(HarvestRules.offsets(5,0).equals(HarvestRules.offsets(5,360))
            &&HarvestRules.offsets(5,-90).equals(HarvestRules.offsets(5,270)),"harvest yaw wraps");
        check(ToolPerformance.efficiency(-1)==0&&ToolPerformance.fortune(999)==4,"bounded levels");

        for (int target=1;target<=25;target++) {
            check(UpgradeRules.required(target)==new int[]{0,1,2,4,8}[(target-1)/5], "material cost tier boundary");
            double chance=UpgradeRules.success(target)/100.0;
            check(UpgradeRules.resolve(target-1,0,.99,chance)==target, "success always advances exactly one");
            if (target>1) {
                check(UpgradeRules.resolve(target-1,chance,.99,chance)==target-1,"failure threshold is exclusive");
                int expected=target<=10 ? target-1 : Math.max(UpgradeRules.floor(target),target-2);
                check(UpgradeRules.resolve(target-1,.999,0,chance)==expected,"failure checkpoint floor");
            }
        }
        check(UpgradeRules.success(1)==100 && UpgradeRules.success(10)==65 && UpgradeRules.success(20)==10 && UpgradeRules.success(25)==3,"specified probabilities");
        check(UpgradeRules.resolve(10,.99,0,.5)==10,"+10 checkpoint is never lost");
        check(UpgradeRules.resolve(20,.99,0,.08)==20,"+20 checkpoint is never lost");
        check(UpgradeRules.resolve(15,.99,0,.25)==15,"+15 checkpoint is never lost");
        check(UpgradeRules.resolve(13,.99,.299,.35)==12,"30 percent downgrade interval");
        check(UpgradeRules.resolve(13,.99,.30,.35)==13,"30 percent upper boundary");
        check(UpgradeRules.resolve(18,.99,.499,.12)==17,"50 percent downgrade interval");
        check(UpgradeRules.resolve(18,.99,.5,.12)==18,"50 percent upper boundary");
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
            check(ToolType.PICKAXE.materialAt(lv)==(lv<5?org.bukkit.Material.IRON_PICKAXE:org.bukkit.Material.DIAMOND_PICKAXE),"pickaxe base material matches milestone");
            check(ToolType.AXE.materialAt(lv)==(lv<5?org.bukkit.Material.IRON_AXE:org.bukkit.Material.DIAMOND_AXE),"axe base material matches milestone");
            check(ToolType.HOE.materialAt(lv)==(lv<5?org.bukkit.Material.IRON_HOE:org.bukkit.Material.DIAMOND_HOE),"hoe base material matches milestone");
            check(ToolType.FISHING_ROD.materialAt(lv)==org.bukkit.Material.FISHING_ROD,"fishing rod material preserved");
            check(ToolType.WATERING_CAN.materialAt(lv)==org.bukkit.Material.CARROT_ON_A_STICK,"watering can material preserved");
        }
        System.out.println("Enhancement rule regression checks passed: "+checks);
    }
}
