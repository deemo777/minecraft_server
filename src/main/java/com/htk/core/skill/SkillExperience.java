package com.htk.core.skill;

/** Original shared XP curve, extended through level 100 without a mining multiplier. */
public final class SkillExperience {
    private SkillExperience() {}
    public static long required(Skill skill,int level,double a,double b,double c) {
        return Math.max(1,Math.round(a+b*level+c*level*(double)level));
    }
}
