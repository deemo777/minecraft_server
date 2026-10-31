package com.htk.core.skill;

/** One threshold calculation for levelling, HUD, commands and skill menus. */
public final class SkillExperience {
    private SkillExperience() {}
    public static double miningMultiplier(int level,double atTen,double perLevel) {
        if(level<10)return 1;
        double start=Double.isFinite(atTen)?Math.max(1.5,atTen):1.5;
        double growth=Double.isFinite(perLevel)?Math.max(0,perLevel):.05;
        return start+(level-10)*growth;
    }
    public static long required(Skill skill,int level,double a,double b,double c,double atTen,double perLevel) {
        long base=Math.max(1,Math.round(a+b*level+c*level*(double)level));
        if(skill!=Skill.MINING || level<10)return base;
        return Math.max(1,(long)Math.ceil(base*miningMultiplier(level,atTen,perLevel)));
    }
}
