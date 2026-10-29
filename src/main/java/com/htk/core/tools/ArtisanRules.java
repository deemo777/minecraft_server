package com.htk.core.tools;

/** Server adaptation of honing: failure bonus, persistent energy and a guaranteed next attempt. */
public final class ArtisanRules {
    public static final int FULL=1_000_000;
    private ArtisanRules() {}
    public record Progress(int failures,int energy) {
        public static final Progress EMPTY=new Progress(0,0);
        public Progress {failures=Math.clamp(failures,0,200);energy=Math.clamp(energy,0,FULL);}
        public boolean guaranteed(){return energy==FULL;}
        public double percent(){return energy/10_000.0;}
    }
    public record Attempt(int level,Progress progress,double percent,boolean guaranteed) {}
    public static String energyLabel(Progress progress) {
        return java.math.BigDecimal.valueOf(progress.energy()/100).movePointLeft(2).stripTrailingZeros().toPlainString();
    }
    public static double effectivePercent(double base,Progress progress) {
        if(progress.guaranteed())return 100;
        double bounded=Double.isFinite(base)?Math.clamp(base,0,100):0;
        return Math.min(100,bounded*(1+Math.min(10,progress.failures())*.1));
    }
    public static double effectivePercent(int target,double base,Progress progress) {
        double chance=effectivePercent(base,progress);
        // Keep a possible failure until the extended energy ceiling; configured 100% stays certain.
        return target>=10 && base<100 && !progress.guaranteed()?Math.min(99.99,chance):chance;
    }
    /** Twice the original worst-case failure count, including custom success-rate settings. */
    public static int extendedFailureCeiling(double base) {
        var progress=Progress.EMPTY;
        while(!progress.guaranteed()) {
            double chance=effectivePercent(base,progress);
            if(chance>=100)break;
            progress=new Progress(progress.failures()+1,progress.energy()+originalGain(chance));
        }
        return Math.max(1,progress.failures()*2);
    }
    private static int originalGain(double chance) {
        return Math.max(10_000,(int)Math.ceil(chance/2.15*10_000));
    }
    public static Attempt attempt(int current,double base,Progress progress,double roll) {
        UpgradeRules.required(current+1);
        double chance=effectivePercent(current+1,base,progress);
        if(progress.guaranteed() || roll<chance/100)
            return new Attempt(current+1,Progress.EMPTY,chance,progress.guaranteed());
        // 0.0001 percentage-point units prevent floating point drift at the 100% boundary.
        // From target +10, normalize energy so the original failure ceiling doubles exactly.
        int ceiling=current+1>=10?extendedFailureCeiling(base):0;
        int gain=ceiling>0?(FULL+ceiling-1)/ceiling:originalGain(chance);
        Progress next=new Progress(progress.failures()+1,progress.energy()+gain);
        return new Attempt(current,next,chance,false);
    }
}
