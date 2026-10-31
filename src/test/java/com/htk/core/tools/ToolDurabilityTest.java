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
            for(int level:new int[]{5,10,15,19})check(ToolPerformance.maxDurability(type,level)==baseline,"Diamond/tool base is not a synthetic linear maximum");
            check(ToolPerformance.wearChance(type,5)==1,"+5 has no unbreaking bonus");
            check(ToolPerformance.wearChance(type,10)==.5,"+10 unbreaking I");
            check(ToolPerformance.wearChance(type,15)==.25,"+15 unbreaking III");
            check(ToolPerformance.wearChance(type,20)==.2,"+20 retains unbreaking IV milestone");
            check(ToolPerformance.wearChance(type,25)==0,"+25 infinite");
            check(NumericEffects.wear(20,ToolPerformance.wearChance(type,5),new Cycle())==20,"Every +5 damage point wears");
            check(NumericEffects.wear(20,ToolPerformance.wearChance(type,10),new Cycle())==10,"I rolls each incoming damage point");
            check(NumericEffects.wear(20,ToolPerformance.wearChance(type,15),new Cycle())==5,"III rolls each incoming damage point");
            check(NumericEffects.wear(20,ToolPerformance.wearChance(type,20),new Cycle())==4,"IV rolls each incoming damage point");
            check(NumericEffects.wear(20,ToolPerformance.wearChance(type,25),new Cycle())==0,"Infinite consumes nothing");
            for(int level=0;level<=25;level++) {
                check(ToolPerformance.maxDurability(type,level)>0,"Positive maximum");
                if(level>0)check(ToolPerformance.wearChance(type,level)<=ToolPerformance.wearChance(type,level-1),"Conservation never regresses");
            }
        }
        for(var type:new ToolType[]{ToolType.PICKAXE,ToolType.AXE,ToolType.HOE}) {
            check(ToolPerformance.maxDurability(type,0)==250,"Initial iron baseline");
            check(ToolPerformance.maxDurability(type,5)==1561,"Exact diamond maximum at +5, never 606");
            check(ToolPerformance.maxDurability(type,20)==2031,"Existing netherite maximum at +20");
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
