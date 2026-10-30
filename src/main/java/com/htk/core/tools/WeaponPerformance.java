package com.htk.core.tools;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Weapon milestones share the existing enhancement, repair and durability transactions. */
public final class WeaponPerformance {
    private WeaponPerformance() {}
    public static int maxTargets(ToolType type){return switch(type){case DAGGER->2;case TACHI->3;case GREATSWORD->5;default->1;};}
    public static double growth(int level){return Math.pow(Math.clamp(level,0,25)/25.0,1.5);}
    private static double between(int level,double start,double end){return start+(end-start)*growth(level);}
    public static double damage(ToolType type,int level) {
        return switch(type){case DAGGER->between(level,4,10.7);case TACHI->between(level,6,13);case GREATSWORD->between(level,9,24);default->throw new IllegalArgumentException("Not a weapon");};
    }
    public static double sweepRatio(ToolType type,int level){return switch(type){case DAGGER->.1;case TACHI->between(level,.2,.77);case GREATSWORD->between(level,.35,1.8);default->0;};}
    /** The engine caps the sweep attribute at 1; extend only its extra sweep damage. */
    public static double extendedSweepDamage(double nativeDamage,double ratio){
        return ratio<=1?nativeDamage:1+Math.max(0,nativeDamage-1)*ratio;
    }
    public static double speed(ToolType type) {return type==ToolType.DAGGER?3:type==ToolType.TACHI?2:1.3;}
    public static double reach(ToolType type) {return type==ToolType.DAGGER?2:type==ToolType.TACHI?3:3.5;}
    public static double speed(ToolType type,int level){return between(level,speed(type),type==ToolType.DAGGER?4.5:type==ToolType.TACHI?2.4:1);}
    /** Match one attack charge period, rounded to the nearest client animation tick. */
    static int animationTicks(ToolType type,int level){return Math.max(1,(int)Math.round(20.0/speed(type,level)));}
    static SwingAnimation.Animation animationType(ToolType type){return type==ToolType.DAGGER?SwingAnimation.Animation.STAB:SwingAnimation.Animation.WHACK;}
    static void applyAnimation(ItemStack item,ToolType type,int level) {
        // Apply after ItemMeta: setting a previously captured meta would overwrite component changes.
        item.setData(DataComponentTypes.ATTACK_ANIMATION,SwingAnimation.swingAnimation()
                .type(animationType(type)).duration(animationTicks(type,level)).build());
    }
    public static double reach(ToolType type,int level){return reach(type)+.02*Math.clamp(level,0,25);}
    static void apply(ServerCore plugin,ToolType type,int level,ItemMeta meta) {
        set(plugin,meta,Attribute.SWEEPING_DAMAGE_RATIO,"weapon_sweep",Math.min(1,sweepRatio(type,level)));
        set(plugin,meta,Attribute.ATTACK_DAMAGE,"weapon_damage",damage(type,level)-1);
        set(plugin,meta,Attribute.ATTACK_SPEED,"weapon_speed",speed(type,level)-4);
        set(plugin,meta,Attribute.ENTITY_INTERACTION_RANGE,"weapon_reach",reach(type,level)-3);
        var lore=new java.util.ArrayList<>(meta.lore());
        lore.add(Text.mm("<gray>최대 타격: <white>"+maxTargets(type)+"마리 <dark_gray>(직접 대상 포함)"));
        lore.add(Text.mm("<gray>공격력: <white>"+Progression.label(damage(type,level))+" <gray>공격 속도: <white>"+Progression.label(speed(type,level))));
        lore.add(Text.mm("<gray>사거리: <white>"+Progression.label(reach(type,level))+" <gray>범위 피해 비율: <white>"+Math.round(sweepRatio(type,level)*100)+"%"));
        if(level>=25) {
            String trait=switch(type){case DAGGER->"추격: 같은 대상 3회 명중 → 2초간 이동 속도 +20% (대기 5초)";case TACHI->"공방일체: 명중 후 2초간 받는 피해 10% 감소";case GREATSWORD->"압도: 한 공격에 3마리 명중 → 1초간 대상 둔화 30% (대기 3초)";default->"";};
            lore.add(Text.mm("<gold>"+trait));
        }
        meta.lore(lore);
    }
    private static void set(ServerCore plugin,ItemMeta meta,Attribute attribute,String key,double amount) {
        meta.removeAttributeModifier(attribute);
        meta.addAttributeModifier(attribute,new AttributeModifier(new NamespacedKey(plugin,key),amount,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
    }
}
