package com.htk.core.tools;

import java.util.List;
import java.util.function.IntToDoubleFunction;

/** Final outcome probabilities, sampled directly without rolling an enchantment level. */
public final class FortuneDrops {
    private FortuneDrops() {}

    private static double interpolate(double strength, IntToDoubleFunction probability) {
        double bounded=Math.clamp(strength,0,4);
        int lower=(int)Math.floor(bounded),upper=Math.min(4,lower+1);
        double fraction=bounded-lower;
        return probability.applyAsDouble(lower)*(1-fraction)+probability.applyAsDouble(upper)*fraction;
    }

    public static double oreProbability(double strength,int multiplier) {
        return interpolate(strength,level -> multiplier<1 || multiplier>level+1?0
                :multiplier==1?(level==0?1:2.0/(level+2)):1.0/(level+2));
    }

    public static int oreMultiplier(double strength,double roll) {
        return sample(1,5,m -> oreProbability(strength,m),roll);
    }

    public static double uniformProbability(double strength,int bonusMultiplier,int extra) {
        return interpolate(strength,level -> extra>=0 && extra<=level*bonusMultiplier
                ?1.0/(level*bonusMultiplier+1):0);
    }

    public static int uniformBonus(double strength,int bonusMultiplier,double roll) {
        return sample(0,4*bonusMultiplier,n -> uniformProbability(strength,bonusMultiplier,n),roll);
    }

    public static double tableChance(double strength,List<Double> chances) {
        return interpolate(strength,level -> chances.get(Math.min(level,chances.size()-1)));
    }

    public static double binomialProbability(double strength,double probability,int extra) {
        return interpolate(strength,level -> {
            if(extra<0 || extra>level)return 0;
            int combinations=1;
            for(int i=1;i<=extra;i++)combinations=combinations*(level-i+1)/i;
            return combinations*Math.pow(probability,extra)*Math.pow(1-probability,level-extra);
        });
    }

    public static int binomialBonus(double strength,double probability,double roll) {
        return sample(0,4,n -> binomialProbability(strength,probability,n),roll);
    }

    private static int sample(int first,int last,IntToDoubleFunction probability,double roll) {
        double cumulative=0;int lastPossible=first;
        for(int outcome=first;outcome<=last;outcome++) {
            double mass=probability.applyAsDouble(outcome);
            if(mass>0)lastPossible=outcome;
            cumulative+=mass;
            if(roll<cumulative)return outcome;
        }
        return lastPossible;
    }
}
