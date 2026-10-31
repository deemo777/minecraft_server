package com.htk.core.tools;

import java.util.Random;

public final class ToolDurabilityTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static final class Cycle extends Random {
        private int cursor;
        @Override public double nextDouble(){return (cursor++%20+.5)/20;}
    }
    public static void main(String[] args) {
        for(var type:new ToolType[]{ToolType.PICKAXE,ToolType.AXE,ToolType.HOE,ToolType.FISHING_ROD,ToolType.WATERING_CAN}) {
            int baseline=type==ToolType.FISHING_ROD?64:type==ToolType.WATERING_CAN?25:1561;
            check(ToolPerformance.maxDurability(type,5)==baseline,"+5 pure baseline");
            check(ToolPerformance.maxDurability(type,10)==baseline*2,"+10 deterministic Unbreaking I lifetime");
            check(ToolPerformance.maxDurability(type,15)==baseline*4,"+15 deterministic Unbreaking III lifetime");
            int netherite=type==ToolType.FISHING_ROD?64:type==ToolType.WATERING_CAN?25:2031;
            check(ToolPerformance.maxDurability(type,20)==netherite*5,"+20 deterministic Unbreaking IV lifetime");
            for(int l=0;l<=25;l++) {
                check(ToolPerformance.wearChance(type,l)==(l==25?0:1),"Every action consumes vanilla damage until +25");
                check(NumericEffects.wear(20,ToolPerformance.wearChance(type,l),new Cycle())==(l==25?0:20),"Multi-point actions keep exact vanilla damage");
            }
            for(int level=0;level<=25;level++) {
                check(ToolPerformance.maxDurability(type,level)>0,"Positive maximum");
                if(level>0)check(ToolPerformance.wearChance(type,level)<=ToolPerformance.wearChance(type,level-1),"Conservation never regresses");
            }
        }
        for(var type:new ToolType[]{ToolType.PICKAXE,ToolType.AXE,ToolType.HOE}) {
            check(ToolPerformance.maxDurability(type,0)==250,"Initial iron baseline");
            check(ToolPerformance.maxDurability(type,5)==1561,"Exact diamond maximum at +5, never 606");
            check(ToolPerformance.maxDurability(type,20)==10155,"Existing netherite maximum at +20");
        }
        check(ToolPerformance.miningSpeed(ToolType.PICKAXE,5)==8 && ToolPerformance.miningSpeed(ToolType.AXE,5)==8,"+5 diamond work speed");
        check(ToolDurability.convertedDamage(303,606,1561)==780,"Existing 606 maximum migrates without free repair");
        check(ToolDurability.convertedDamage(780,1561,1561)==780,"Unbreaking milestone does not change displayed damage");
        for(var weapon:new ToolType[]{ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD}) {
            check(ToolPerformance.maxDurability(weapon,5)==512,"Weapon maximum curve preserved");
            check(ToolPerformance.wearChance(weapon,15)==1,"Tool change does not rebalance weapon wear");
        }
        System.out.println("Tool diamond/unbreaking/damage-migration checks passed: "+checks);
    }
}
