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
    public static double damage(ToolType type,int level) {
        double base=type==ToolType.DAGGER?4.5:type==ToolType.TACHI?7:12;
        return Progression.value(level,base*6/7,base,base+2,base+3,base+4,base+5)+(type==ToolType.TACHI?2:0);
    }
    public static double sweepRatio(ToolType type,int level){
        double maximum=type==ToolType.DAGGER?.3:type==ToolType.TACHI?.65:1.4;
        return Progression.value(level,0,.25,.5,.75,.78,.8)/.8*maximum;
    }
    /** The engine caps the sweep attribute at 1; extend only its extra sweep damage. */
    public static double extendedSweepDamage(double nativeDamage,double ratio){
        return ratio<=1?nativeDamage:1+Math.max(0,nativeDamage-1)*ratio;
    }
    public static double speed(ToolType type) {return type==ToolType.DAGGER?3.6:type==ToolType.TACHI?2:1;}
    public static double reach(ToolType type) {return type==ToolType.DAGGER?2:type==ToolType.TACHI?3:3.5;}
    public static double speed(ToolType type,int level){return speed(type)*(1+.004*Math.clamp(level,0,25));}
    public static double reach(ToolType type,int level){return reach(type)+.02*Math.clamp(level,0,25);}
    static void apply(ServerCore plugin,ToolType type,int level,ItemMeta meta) {
        set(plugin,meta,Attribute.SWEEPING_DAMAGE_RATIO,"weapon_sweep",Math.min(1,sweepRatio(type,level)));
        set(plugin,meta,Attribute.ATTACK_DAMAGE,"weapon_damage",damage(type,level)-1);
        set(plugin,meta,Attribute.ATTACK_SPEED,"weapon_speed",speed(type,level)-4);
        set(plugin,meta,Attribute.ENTITY_INTERACTION_RANGE,"weapon_reach",reach(type,level)-3);
        var lore=new java.util.ArrayList<>(meta.lore());
        lore.add(Text.mm("<gray>공격력: <white>"+Progression.label(damage(type,level))+" <gray>공격 속도: <white>"+Progression.label(speed(type,level))));
        lore.add(Text.mm("<gray>사거리: <white>"+Progression.label(reach(type,level))+" <gray>범위 피해 비율: <white>"+Math.round(sweepRatio(type,level)*100)+"%"));
        meta.lore(lore);
    }
    private static void set(ServerCore plugin,ItemMeta meta,Attribute attribute,String key,double amount) {
        meta.removeAttributeModifier(attribute);
        meta.addAttributeModifier(attribute,new AttributeModifier(new NamespacedKey(plugin,key),amount,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
    }
}
