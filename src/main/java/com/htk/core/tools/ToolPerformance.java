package com.htk.core.tools;

/** 강화 성능 기준. 바닐라 인챈트 레벨은 정수 단위로 올린다. */
public final class ToolPerformance {
    private ToolPerformance() {}
    public static int efficiency(int level) {
        int n=Math.clamp(level,0,20);
        return n<=10?(int)Math.floor(n*.5):5+(int)Math.floor((n-10)*.2);
    }
    public static int fortune(int level) {
        int n=Math.clamp(level,0,20);
        return n<=10?(int)Math.floor(n*.3):Math.min(4,3+(int)Math.floor((n-10)*.2));
    }
    public static double axeDoubleChance(int level) {return Math.clamp(level,0,20)*.025;}
}
