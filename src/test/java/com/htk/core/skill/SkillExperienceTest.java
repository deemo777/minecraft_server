package com.htk.core.skill;

import org.bukkit.configuration.file.YamlConfiguration;
import com.htk.core.tools.CrystalDropRules;

public final class SkillExperienceTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static long need(Skill skill,int level){return SkillExperience.required(skill,level,50,40,3);}
    public static void main(String[] args) throws Exception {
        for(int level=1;level<=100;level++)for(var skill:Skill.values())
            check(need(skill,level)==50+40L*level+3L*level*level,"Original XP formula for every skill through 100");
        check(need(Skill.MINING,10)==750,"Mining multiplier reverted");
        check(need(Skill.MINING,50)==9550 && need(Skill.MINING,99)==33413,"Curve continues through old cap to new cap");
        for(int old=0;old<=50;old++) {
            check(SkillScale.rewardLevel(old*2)==old,"Old 25/50 rewards move to 50/100");
            check(Math.abs(SkillScale.rewardLevel(old*2)*.0005-old*.0005)<1e-12,"Passive reward endpoints preserved");
            for(String ore:new String[]{"STONE","COPPER_ORE","IRON_ORE","DIAMOND_ORE","EMERALD_ORE","ANCIENT_DEBRIS"})
                check(CrystalDropRules.miningChance(ore,.02,.002,.12,SkillScale.rewardLevel(old*2))==CrystalDropRules.miningChance(ore,.02,.002,.12,old),"Crystal endpoints preserve earlier nerf");
            check(CrystalDropRules.fishingChance(SkillScale.rewardLevel(old*2))==CrystalDropRules.fishingChance(old),"Fishing endpoints move to 100");
        }
        check(SkillScale.rewardLevel(51)==25.5,"Inserted levels interpolate rewards");
        check(CrystalDropRules.fishingChance(SkillScale.rewardLevel(99))<.40 && CrystalDropRules.fishingChance(SkillScale.rewardLevel(100))==.40,"Fishing maximum only at 100");
        check(CrystalDropRules.miningChance("EMERALD_ORE",.02,.002,.12,SkillScale.rewardLevel(99))<1,"Rare crystal guarantee waits until 100");
        var config=new YamlConfiguration();config.loadFromString("max-level: 50\ncurve:\n  mining:\n    multiplier-at-10: 1.5\ntitles:\n  mining:\n    10: first\n    25: middle\n    50: final\nperks:\n  double-drop-per-level: 0.0005\n");
        SkillScale.prepareConfig(config);
        check(config.getInt("max-level")==100,"Existing max-level 50 is upgraded");
        check(config.getString("titles.mining.50").equals("middle") && config.getString("titles.mining.100").equals("final"),"Configured 25 and 50 rewards shift correctly");
        check(!config.contains("titles.mining.10") && !config.contains("curve.mining"),"Old thresholds and mining multiplier removed");
        String once=config.saveToString();SkillScale.prepareConfig(config);check(config.saveToString().equals(once),"Reload does not double thresholds again");
        var fresh=new YamlConfiguration();SkillScale.prepareConfig(fresh);fresh.setDefaults(YamlConfiguration.loadConfiguration(java.nio.file.Path.of("src/main/resources/skills.yml").toFile()));
        check(fresh.getInt("max-level")==100 && fresh.getString("titles.mining.100").contains("전설"),"Missing legacy keys use new defaults exactly once");
        long xp=need(Skill.MINING,49)+need(Skill.MINING,50)+17;int level=49;
        while(xp>=need(Skill.MINING,level)){xp-=need(Skill.MINING,level);level++;}
        check(level==51 && xp==17,"Multi-level gains cross former 50 cap");
        System.out.println("100-level XP/reward/config compatibility checks passed: "+checks);
    }
}
