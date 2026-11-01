package com.htk.core.tools;

/** Full-charge, non-critical hits, with no skill/effect bonus or equipment on the mob. */
final class WeaponDamageRegression {
    static void run() {
        ToolType[] types={ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD};
        double[] start={4,6,9},oldCap={10.7,13,24};
        for(int i=0;i<types.length;i++) {
            for(int level=0;level<=5;level++) {
                double legacy=start[i]+(oldCap[i]-start[i])*Math.pow(level/25.0,1.5);
                check(Math.abs(WeaponPerformance.damage(types[i],level)-legacy)<1e-10,"all +0..5 damage values stay unchanged");
            }
            double previousGain=WeaponPerformance.damage(types[i],5)-WeaponPerformance.damage(types[i],4);
            for(int level=6;level<=25;level++) {
                double damage=WeaponPerformance.damage(types[i],level);
                double legacy=start[i]+(oldCap[i]-start[i])*Math.pow(level/25.0,1.5);
                double gain=damage-WeaponPerformance.damage(types[i],level-1);
                check(damage>legacy,"every +6..25 weapon is stronger than before");
                check(gain+1e-10>=previousGain,"damage growth does not slow down at milestone boundaries");
                previousGain=gain;
            }
            check(WeaponPerformance.damage(types[i],-10)==start[i],"negative level clamps to zero");
            check(WeaponPerformance.damage(types[i],30)==WeaponPerformance.damage(types[i],25),"over-cap level clamps to +25");
        }
        double at10=WeaponPerformance.damage(ToolType.TACHI,10);
        double at15=WeaponPerformance.damage(ToolType.TACHI,15);
        check(hits(20,2,0,at10)==2,"+10 tachi kills a normal zombie in exactly two hits including natural armor");
        check(hits(20,2,0,at15)==1,"+15 tachi kills a normal zombie in one hit including natural armor");
        check(hits(30,0,0,at10)==3,"+10 tachi needs three hits against 30 unarmored HP");
        check(hits(40,0,0,at10)==3,"+10 tachi needs three hits against 40 unarmored HP");
        check(hits(20,20,8,at15)>1,"armored targets still receive normal armor mitigation");
        System.out.println("Weapon damage: preserved +0..5, stronger +6..25, zombie 2/1 hits and 30/40 HP 3-hit checks passed");
    }

    // Java's armor/toughness reduction. This is a test oracle; runtime keeps native damage processing.
    private static int hits(double health,double armor,double toughness,double damage) {
        double effectiveArmor=Math.clamp(armor-damage/(2+toughness/4),armor*.2,20);
        double received=damage*(1-effectiveArmor/25);
        return (int)Math.ceil(health/received);
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
