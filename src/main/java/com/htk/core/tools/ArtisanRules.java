package com.htk.core.tools;

/** Server adaptation of honing: failure bonus, persistent energy and a guaranteed next attempt. */
public final class ArtisanRules {
    public static final int FULL=1_000_000;
    private ArtisanRules() {}
    public record Progress(int failures,int energy) {
        public static final Progress EMPTY=new Progress(0,0);
        public Progress {failures=Math.clamp(failures,0,100);energy=Math.clamp(energy,0,FULL);}
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
    public static Attempt attempt(int current,double base,Progress progress,double roll) {
        UpgradeRules.required(current+1);
        double chance=effectivePercent(base,progress);
        if(progress.guaranteed() || roll<chance/100)
            return new Attempt(current+1,Progress.EMPTY,chance,progress.guaranteed());
        // 0.0001 percentage-point units prevent floating point drift at the 100% boundary.
        // A 1 percentage-point minimum also gives custom 0% stages a finite ceiling.
        int gain=Math.max(10_000,(int)Math.ceil(chance/2.15*10_000));
        Progress next=new Progress(progress.failures()+1,progress.energy()+gain);
        return new Attempt(current,next,chance,false);
    }
}
