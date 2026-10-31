package com.htk.core.skill;

public final class SkillExperienceTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static long need(Skill skill,int level){return SkillExperience.required(skill,level,50,40,3,1.5,.05);}
    public static void main(String[] args) {
        long previous=0;double previousMultiplier=1;
        for(int level=1;level<50;level++) {
            long base=50+40*level+3L*level*level;
            for(var skill:Skill.values()) {
                long threshold=need(skill,level);
                if(skill!=Skill.MINING || level<10)check(threshold==base,"Other skills and mining through reaching level ten are unchanged");
                else {
                    check(threshold>=Math.ceil(base*1.5),"Mining always costs at least 1.5 times from ten onward");
                    check(SkillExperience.miningMultiplier(level,1.5,.05)>previousMultiplier,"Multiplier rises with every mining level");
                    check(threshold>previous,"Mining thresholds strictly increase");
                    previousMultiplier=SkillExperience.miningMultiplier(level,1.5,.05);
                }
            }
            previous=need(Skill.MINING,level);
        }
        check(need(Skill.MINING,9)==653,"Nine to ten remains unchanged");
        check(need(Skill.MINING,10)==1125,"Ten to eleven: 750 times 1.5");
        check(need(Skill.MINING,20)==4100,"Twenty to twenty-one: twice base");
        check(need(Skill.MINING,30)==9875,"Thirty: 2.5 times base");
        check(need(Skill.MINING,40)==19350,"Forty: three times base");
        check(need(Skill.MINING,49)==31785,"Fractional requirements round upward");
        check(SkillExperience.miningMultiplier(10,0,.05)==1.5,"Configured multiplier cannot undercut minimum");
        check(SkillExperience.miningMultiplier(20,Double.NaN,Double.NaN)==2,"Invalid settings use defaults");
        // Large XP awards must pay each threshold, rather than repeatedly paying the starting tier.
        long xp=need(Skill.MINING,9)+need(Skill.MINING,10)+17;int level=9;
        while(xp>=need(Skill.MINING,level)){xp-=need(Skill.MINING,level);level++;}
        check(level==11 && xp==17,"A multi-level gain crosses the level-ten boundary correctly");
        System.out.println("Skill-specific XP threshold checks passed: "+checks);
    }
}
