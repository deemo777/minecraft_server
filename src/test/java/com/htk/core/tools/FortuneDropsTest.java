package com.htk.core.tools;

public final class FortuneDropsTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-12,message);}
    public static void main(String[] args) {
        double[][] vanilla={{1,0,0,0,0},{2.0/3,1.0/3,0,0,0},{.5,.25,.25,0,0},
                {.4,.2,.2,.2,0},{1.0/3,1.0/6,1.0/6,1.0/6,1.0/6}};
        for(int level=0;level<=4;level++)for(int multiplier=1;multiplier<=5;multiplier++)
            near(FortuneDrops.oreProbability(level,multiplier),vanilla[level][multiplier-1],"Exact vanilla integer-level ore distribution");
        near(FortuneDrops.oreProbability(.2,2),.2/3,"0.2 directly scales double chance");
        near(FortuneDrops.oreProbability(.8,2),.8/3,"+9 double chance");
        near(FortuneDrops.oreProbability(1.5,1),7.0/12,"1.5 single probability");
        near(FortuneDrops.oreProbability(1.5,2),7.0/24,"1.5 double probability");
        near(FortuneDrops.oreProbability(1.5,3),1.0/8,"1.5 triple probability");
        double previousMean=0,previousBonus=0;
        for(int upgrade=0;upgrade<=25;upgrade++) {
            double strength=ToolPerformance.fortuneStrength(upgrade),sum=0,mean=0;
            int[] observed=new int[6];
            for(int draw=0;draw<12000;draw++)observed[FortuneDrops.oreMultiplierAtLevel(upgrade,(draw+.5)/12000)]++;
            for(int m=1;m<=5;m++) {
                double probability=FortuneDrops.oreProbabilityAtLevel(upgrade,m);
                check(probability>=0,"Nonnegative probability");sum+=probability;mean+=m*probability;
                check(Math.abs(observed[m]/12000.0-probability)<=1.0/12000,"Sampler matches final outcome probabilities");
            }
            near(sum,1,"Ore probabilities total one");
            double bonus=1-FortuneDrops.oreProbabilityAtLevel(upgrade,1);
            check(mean+1e-12>=previousMean && bonus+1e-12>=previousBonus,"Expected drops and bonus chance never regress");
            previousMean=mean;previousBonus=bonus;
            for(int multiplier:new int[]{1,2}) {
                double uniformSum=0;
                for(int extra=0;extra<=8;extra++)uniformSum+=FortuneDrops.uniformProbability(strength,multiplier,extra);
                near(uniformSum,1,"Uniform bonus probability total");
                for(double roll:new double[]{0,.2,.5,.999999,Math.nextDown(1.0)}) {
                    int extra=FortuneDrops.uniformBonus(strength,multiplier,roll);
                    check(FortuneDrops.uniformProbability(strength,multiplier,extra)>0,"Uniform sampler never produces impossible count");
                }
            }
            for(double probability:new double[]{0,.25,.5714286,1}) {
                double binomialSum=0;
                for(int extra=0;extra<=4;extra++)binomialSum+=FortuneDrops.binomialProbability(strength,probability,extra);
                near(binomialSum,1,"Binomial probability total");
                for(double roll:new double[]{0,.5,.999999,Math.nextDown(1.0)}) {
                    int extra=FortuneDrops.binomialBonus(strength,probability,roll);
                    check(FortuneDrops.binomialProbability(strength,probability,extra)>0,"Binomial sampler never produces impossible count");
                }
            }
        }
        for(int upgrade=0;upgrade<=25;upgrade++) {
            if(upgrade<20) {
                for(int m=1;m<=5;m++)near(FortuneDrops.oreProbabilityAtLevel(upgrade,m),
                        FortuneDrops.oreProbability(ToolPerformance.fortuneStrength(upgrade),m),"Below +20 unchanged");
            } else {
                near(FortuneDrops.oreProbabilityAtLevel(upgrade,1),0,"No base-only drop from +20");
                for(double roll:new double[]{0,.25,.5,.75,.999999,Math.nextDown(1.0)})
                    check(FortuneDrops.oreMultiplierAtLevel(upgrade,roll)>=2,"Always at least double, including sampler boundaries");
                double strength=ToolPerformance.fortuneStrength(upgrade);
                for(int m=2;m<=5;m++)near(FortuneDrops.oreProbabilityAtLevel(upgrade,m),
                        FortuneDrops.oreProbability(strength,m)/(1-FortuneDrops.oreProbability(strength,1)),"Bonus probabilities redistributed proportionally");
            }
        }
        for(int m=2;m<=4;m++)near(FortuneDrops.oreProbabilityAtLevel(20,m),11.0/38,"+20 double/triple/quadruple chance");
        near(FortuneDrops.oreProbabilityAtLevel(20,5),5.0/38,"+20 quintuple chance");
        for(int m=2;m<=5;m++)near(FortuneDrops.oreProbabilityAtLevel(25,m),.25,"+25 equal bonus outcomes");
        check(FortuneDrops.oreProbabilityAtLevel(19,1)>0,"Bonus threshold starts exactly at +20");
        near(FortuneDrops.uniformProbability(1,1,0),.5,"Redstone Fortune I zero bonus");
        near(FortuneDrops.uniformProbability(1,1,1),.5,"Redstone Fortune I one bonus");
        near(FortuneDrops.uniformProbability(.8,1,1),.4,"Redstone fractional bonus");
        near(FortuneDrops.tableChance(1,java.util.List.of(.1,1.0/7,.25,1.0)),1.0/7,"Gravel Fortune I chance");
        near(FortuneDrops.tableChance(.5,java.util.List.of(.1,1.0/7,.25,1.0)),(.1+1.0/7)/2,"Direct table interpolation");
        System.out.println("Direct Fortune drop probability checks passed: "+checks);
    }
}
