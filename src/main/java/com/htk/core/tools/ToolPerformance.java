package com.htk.core.tools;

/** Five-level performance milestones; fractional fortune is a per-block I/II mixture. */
public final class ToolPerformance {
    private ToolPerformance() {}
    public static boolean indestructible(int level) { return level >= 25; }
    public static int efficiency(int level) {
        return level>=25?7:level>=20?6:level>=15?5:level>=10?3:0;
    }
    public static int fortune(int level) {
        return level>=20?4:level>=15?3:level>=10?1:0;
    }
    public static int rolledFortune(int level,double roll) {return level>=10&&level<15&&roll<.5?2:fortune(level);}
    public static int axeEfficiency(int level) {return level>=25?7:level>=20?6:level>=15?5:level>=10?4:0;}
    public static boolean silkTouch(int level) {return level>=15;}
    public static int hoeFortune(int level) {return level>=25?3:level>=20?2:0;}
    /** Extra blocks, excluding the clicked block. Final tier is bounded for server safety. */
    public static int extraBlocks(int level) {return level>=25?4095:level>=20?level-19:0;}
}
