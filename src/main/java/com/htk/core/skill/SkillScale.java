package com.htk.core.skill;

import java.util.LinkedHashMap;
import org.bukkit.configuration.file.YamlConfiguration;

/** A displayed 100-level track retains the old 50-level reward endpoints. */
public final class SkillScale {
    private SkillScale() {}
    public static double rewardLevel(int level){return Math.clamp(level,0,100)/2.0;}
    /** Normalize legacy settings in memory; do not rewrite the operator's file. */
    public static void prepareConfig(YamlConfiguration config) {
        if(config.getInt("level-scale",50)>=100)return;
        if(config.getInt("max-level",50)==50)config.set("max-level",100);
        for(var skill:Skill.values()) {
            String path="titles."+skill.key();var section=config.getConfigurationSection(path);
            if(section==null)continue;
            var shifted=new LinkedHashMap<String,Object>();
            for(String key:section.getKeys(false)) {
                try {shifted.put(Integer.toString(Math.multiplyExact(Integer.parseInt(key),2)),section.get(key));}
                catch(NumberFormatException | ArithmeticException ignored) { }
            }
            config.set(path,null);shifted.forEach((key,value)->config.set(path+"."+key,value));
        }
        config.set("level-scale",100);
        config.set("curve.mining",null);
    }
}
