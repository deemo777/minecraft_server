package com.htk.core.tools;

import com.htk.core.skill.SkillScale;
import java.util.ArrayList;
import java.util.function.DoubleUnaryOperator;

final class CrystalMilestoneRegression {
    private record Curve(String name,double initial,double at10,double maximum,DoubleUnaryOperator chance) {}
    static void run() {
        var curves=new ArrayList<Curve>();
        curves.add(new Curve("weapon",.08,.10,.28,CrystalDropRules::weaponChance));
        curves.add(new Curve("axe",.04,.06,.24,CrystalDropRules::axeChance));
        curves.add(new Curve("hoe/can",.02,.03,.12,l->CrystalDropRules.chance(.02,.002,.12,l)));
        curves.add(new Curve("fishing",.08,.10,.28,CrystalDropRules::fishingChance));
        String[] ores={"STONE","DEEPSLATE","COPPER_ORE","DEEPSLATE_COPPER_ORE","COAL_ORE","DEEPSLATE_COAL_ORE",
                "IRON_ORE","DEEPSLATE_IRON_ORE","GOLD_ORE","DEEPSLATE_GOLD_ORE","REDSTONE_ORE","DEEPSLATE_REDSTONE_ORE",
                "LAPIS_ORE","DEEPSLATE_LAPIS_ORE","NETHER_GOLD_ORE","NETHER_QUARTZ_ORE",
                "DIAMOND_ORE","DEEPSLATE_DIAMOND_ORE","EMERALD_ORE","DEEPSLATE_EMERALD_ORE","ANCIENT_DEBRIS"};
        for(String ore:ores) {
            double initial=CrystalDropRules.miningBase(ore);
            boolean diamond=ore.endsWith("DIAMOND_ORE"),rare=ore.endsWith("EMERALD_ORE") || ore.equals("ANCIENT_DEBRIS");
            double maximum=diamond?.25:rare?1:initial*3;
            double at10=diamond?.16:rare?.55:initial*1.2;
            curves.add(new Curve(ore,initial,at10,maximum,l->CrystalDropRules.miningChance(ore,.02,.002,.12,l)));
        }
        for(Curve curve:curves) {
            double previous=curve.initial;
            for(int displayed=0;displayed<=100;displayed++) {
                double chance=curve.chance.applyAsDouble(SkillScale.rewardLevel(displayed));
                double expected=curve.initial+(curve.at10-curve.initial)*(displayed/10);
                check(Math.abs(chance-expected)<1e-12,curve.name+": exact requested rate at displayed level "+displayed);
                check(chance>=previous-1e-12 && chance<=curve.maximum+1e-12,curve.name+": monotonic and bounded");
                if(displayed%10!=0)check(Math.abs(chance-previous)<1e-12,curve.name+": no increase within a ten-level band");
                else if(displayed>0)check(chance>previous,curve.name+": increases at every completed ten-level milestone");
                if(displayed<10)check(Math.abs(chance-curve.initial)<1e-12,curve.name+": levels 0..9 use initial chance");
                if(displayed==10 || displayed==19)check(Math.abs(chance-curve.at10)<1e-12,curve.name+": first milestone and plateau");
                if(displayed==99)check(chance<curve.maximum,curve.name+": maximum waits until level 100");
                if(displayed==100)check(Math.abs(chance-curve.maximum)<1e-12,curve.name+": requested or preserved final chance");
                previous=chance;
            }
            check(Math.abs(curve.chance.applyAsDouble(-10)-curve.initial)<1e-12,curve.name+": negative level clamps");
            check(Math.abs(curve.chance.applyAsDouble(1000)-curve.maximum)<1e-12,curve.name+": over-cap level clamps");
        }
        // Existing custom tool configurations retain their actual level-100 outcome, even below the cap.
        for(double[] config:new double[][]{{.02,.0004,.12},{.04,.01,.08},{.1,.002,.05}}) {
            double expected=Math.min(Math.max(config[0],config[2]),config[0]+50*config[1]);
            check(Math.abs(CrystalDropRules.chance(config[0],config[1],config[2],50)-expected)<1e-12,"custom final tool chance preserved");
        }
        System.out.println("All crystal families: displayed levels 0..100, ten-level bands, first milestones and preserved final rates passed");
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
