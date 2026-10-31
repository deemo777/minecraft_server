package com.htk.core.dogam;

/** Shared deposit and remaining-count rules; completed icons always have stack size one. */
final class CollectionProgress {
    record Deposit(int taken,int total,boolean complete) {}
    static Deposit deposit(int required,int registered,int available) {
        int target=Math.max(1,required),before=Math.clamp(registered,0,target);
        int taken=Math.min(target-before,Math.max(0,available));
        return new Deposit(taken,before+taken,before+taken==target);
    }
    static int iconAmount(int required,int registered,boolean done) {
        return done?1:Math.max(1,Math.max(1,required)-Math.max(0,registered));
    }
}
