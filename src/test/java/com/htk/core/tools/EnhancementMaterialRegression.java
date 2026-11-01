package com.htk.core.tools;

import com.htk.core.skill.Skill;
import com.htk.core.skill.SkillScale;

final class EnhancementMaterialRegression {
    static void run() {
        for(ToolType material:ToolType.values()) {
            for(ToolType tool:ToolType.values()) {
                boolean expected=material==tool || material.weapon() && tool.weapon();
                check(EnhancementMaterialRules.matches(material,tool)==expected,"weapon crystals cross-match; work tools stay type-specific");
                ToolType stored=EnhancementMaterialRules.fromId(material.id);
                check(EnhancementMaterialRules.matches(stored,tool)==expected,"all legacy crystal IDs retain correct eligibility");
            }
            ToolType decoded=EnhancementMaterialRules.fromId(EnhancementMaterialRules.id(material));
            check(EnhancementMaterialRules.matches(decoded,material),"new crystal ID round-trips");
            check(EnhancementMaterialRules.id(decoded).equals(EnhancementMaterialRules.id(material)),"crystal normalization is idempotent");
            if(material.weapon()) {
                check(EnhancementMaterialRules.id(material).equals("weapon"),"all weapons create one shared PDC identity");
                check(EnhancementMaterialRules.name(material).equals("무기 강화 결정"),"all weapons create one shared name");
                check(EnhancementMaterialRules.model(material).equals("enhancement_tachi"),"all weapons reuse the existing purple model");
                check(material.relatedSkill()==Skill.COMBAT,"all weapon drops use combat skill");
            } else {
                check(EnhancementMaterialRules.id(material).equals(material.id),"work crystal IDs are unchanged");
                check(EnhancementMaterialRules.model(material).equals("enhancement_"+material.id),"work crystal models are unchanged");
            }
        }
        check(!EnhancementMaterialRules.matches(null,ToolType.TACHI),"missing material cannot upgrade a weapon");
        check(!EnhancementMaterialRules.matches(ToolType.TACHI,null),"missing tool cannot match");
        check(EnhancementMaterialRules.fromId(null)==null && EnhancementMaterialRules.fromId("unknown")==null,"unmarked items remain invalid");
        check(EnhancementMaterialRules.matches(EnhancementMaterialRules.fromId("weapon"),ToolType.GREATSWORD),"new common ID upgrades any weapon");
        int[] levels={0,1,9,10,19,50,99,100};double[] expected={.08,.08,.08,.10,.10,.18,.26,.28};
        for(int i=0;i<levels.length;i++)
            check(Math.abs(CrystalDropRules.weaponChance(SkillScale.rewardLevel(levels[i]))-expected[i])<1e-12,"requested weapon drop rate at displayed combat level "+levels[i]);
        for(int target=1;target<=25;target++)
            check(UpgradeRules.hasMaterials(target,true,UpgradeRules.required(target)),"common crystal quantity covers every upgrade tier");
        System.out.println("Shared weapon crystal: legacy IDs, all material/tool pairs, presentation identity and eight-to-twenty-eight-percent combat milestone curve passed");
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
