package com.htk.core.tools;

public final class UpgradeRulesTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
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
