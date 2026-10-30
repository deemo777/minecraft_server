package com.htk.core.tools;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;

/** Weapon milestones share the existing enhancement, repair and durability transactions. */
public final class WeaponPerformance {
    private WeaponPerformance() {}
    public static int sharpness(int level) { return level>=25?9:level>=20?7:level>=15?5:level>=10?3:0; }
    public static int sweeping(int level) { return level>=25?4:level>=15?3:level>=10?1:0; }
    public static double damage(ToolType type,int level) {
        double diamond=type==ToolType.DAGGER?4.5:type==ToolType.TACHI?7:12;
        return (level<5?diamond*6/7:diamond)+NumericEffects.damageBonus(sharpness(level));
    }
    public static double speed(ToolType type) {return type==ToolType.DAGGER?3.6:type==ToolType.TACHI?2:1;}
    public static double reach(ToolType type) {return type==ToolType.DAGGER?2:type==ToolType.TACHI?3:3.5;}
    static void apply(ServerCore plugin,ToolType type,int level,ItemMeta meta) {
        set(plugin,meta,Attribute.SWEEPING_DAMAGE_RATIO,"weapon_sweep",NumericEffects.sweepRatio(sweeping(level)));
        set(plugin,meta,Attribute.ATTACK_DAMAGE,"weapon_damage",damage(type,level)-1);
        set(plugin,meta,Attribute.ATTACK_SPEED,"weapon_speed",speed(type)-4);
        set(plugin,meta,Attribute.ENTITY_INTERACTION_RANGE,"weapon_reach",reach(type)-3);
        var lore=new java.util.ArrayList<>(meta.lore());
        lore.add(Text.mm("<gray>공격력: <white>"+damage(type,level)+" <gray>공격 속도: <white>"+speed(type)));
        lore.add(Text.mm("<gray>사거리: <white>"+reach(type)+" <gray>범위 피해 비율: <white>"+Math.round(NumericEffects.sweepRatio(sweeping(level))*100)+"%"));
        meta.lore(lore);
    }
    private static void set(ServerCore plugin,ItemMeta meta,Attribute attribute,String key,double amount) {
        meta.removeAttributeModifier(attribute);
        meta.addAttributeModifier(attribute,new AttributeModifier(new NamespacedKey(plugin,key),amount,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
    }
}
