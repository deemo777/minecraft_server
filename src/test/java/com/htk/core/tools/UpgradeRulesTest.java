package com.htk.core.tools;

public final class UpgradeRulesTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int level = 0; level <= 20; level++) {
            check(ToolPerformance.indestructible(level) == (level == 20), "max-tier durability and fire protection");
            check(WateringCanRules.infinite(level) == (level == 20), "only max-tier water is infinite");
            check(WateringCanRules.canWater(0, level) == (level == 20), "empty max-tier can needs no refill");
            check(WateringCanRules.canWater(1, level), "watered can is usable");
            check(WateringCanRules.afterUse(1, level) == (level == 20 ? 1 : 0), "last charge only consumed below max tier");
        }
        int unlimited = 0;
        for (int use = 0; use < 1000; use++) {
            check(WateringCanRules.canWater(unlimited, 20), "unlimited repeated watering without filling");
            unlimited = WateringCanRules.afterUse(unlimited, 20);
        }
        check(!WateringCanRules.canWater(unlimited, 19), "downgrade removes infinite-water access");
        check(!ToolPerformance.indestructible(19) && ToolPerformance.indestructible(20), "protection turns on/off at +20");
        for (int level = 0; level <= 20; level++) {
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
        for (int level = 0; level <= 20; level++) {
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
        check(ToolPerformance.efficiency(0)==0&&ToolPerformance.fortune(0)==0,"base diamond has no bonus");
        check(ToolPerformance.efficiency(10)==5&&ToolPerformance.efficiency(20)==7,"efficiency anchor levels");
        check(ToolPerformance.fortune(10)==3&&ToolPerformance.fortune(15)==4,"fortune anchor levels");
        for(int lv=1;lv<=20;lv++) {
            check(ToolPerformance.efficiency(lv)>=ToolPerformance.efficiency(lv-1),"efficiency monotonic");
            check(ToolPerformance.fortune(lv)>=ToolPerformance.fortune(lv-1),"fortune monotonic");
            check(Math.abs(ToolPerformance.axeDoubleChance(lv)-lv*.025)<1e-10,"axe probability increment");
            if(lv>=15)check(ToolPerformance.fortune(lv)==4,"fortune capped after +15");
        }
        check(ToolPerformance.axeDoubleChance(0)==0&&ToolPerformance.axeDoubleChance(20)==.5,"axe endpoints");
        check(ToolPerformance.efficiency(-1)==0&&ToolPerformance.fortune(999)==4,"bounded levels");

        for (int target=1;target<=20;target++) {
            check(UpgradeRules.required(target)==1+(target-1)/5, "material cost tier boundary");
            double chance=UpgradeRules.success(target)/100.0;
            check(UpgradeRules.resolve(target-1,0,.99,chance)==target, "success always advances exactly one");
            if (target>1) {
                check(UpgradeRules.resolve(target-1,chance,.99,chance)==target-1,"failure threshold is exclusive");
                int expected=target<=10 ? target-1 : Math.max(UpgradeRules.floor(target),target-2);
                check(UpgradeRules.resolve(target-1,.999,0,chance)==expected,"failure checkpoint floor");
            }
        }
        check(UpgradeRules.success(1)==100 && UpgradeRules.success(10)==55 && UpgradeRules.success(20)==10,"specified probabilities");
        check(UpgradeRules.resolve(10,.99,0,.5)==10,"+10 checkpoint is never lost");
        check(UpgradeRules.resolve(15,.99,0,.25)==15,"+15 checkpoint is never lost");
        check(UpgradeRules.resolve(13,.99,.299,.35)==12,"30 percent downgrade interval");
        check(UpgradeRules.resolve(13,.99,.30,.35)==13,"30 percent upper boundary");
        check(UpgradeRules.resolve(18,.99,.499,.12)==17,"50 percent downgrade interval");
        check(UpgradeRules.resolve(18,.99,.5,.12)==18,"50 percent upper boundary");
        for (int bad : new int[]{0,21,-1}) {
            boolean failed=false;try{UpgradeRules.required(bad);}catch(IllegalArgumentException ex){failed=true;}
            check(failed,"reject invalid or maxed target");
        }
        System.out.println("Enhancement rule regression checks passed: "+checks);
    }
}
