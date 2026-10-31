package com.htk.core.tools;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.*;

/** Numeric level-25 passives. Own movement modifiers are removed independently of other buffs. */
final class WeaponTraits implements Listener {
    private final VillageToolFeature tools;
    private final NamespacedKey pursuitKey,slowKey;
    private final Map<UUID,WeaponTraitState> states=new HashMap<>();
    private final Map<UUID,Long> slowed=new HashMap<>();
    private final Map<UUID,LivingEntity> moved=new HashMap<>();
    private final Map<UUID,String> held=new HashMap<>();
    private final BukkitTask timer;
    WeaponTraits(ServerCore plugin,VillageToolFeature tools) {
        this.tools=tools;pursuitKey=new NamespacedKey(plugin,"weapon_pursuit");slowKey=new NamespacedKey(plugin,"weapon_pressure");
        for(var world:Bukkit.getWorlds())for(var entity:world.getLivingEntities())scrub(entity);
        timer=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    private long now(){return Integer.toUnsignedLong(Bukkit.getCurrentTick());}
    private WeaponTraitState state(Player player){return states.computeIfAbsent(player.getUniqueId(),id->new WeaponTraitState());}
    private boolean holding(Player player,ToolType type) {
        var item=player.getInventory().getItemInMainHand();return tools.typeOf(item)==type && tools.levelOf(item)==25;
    }
    private void modifier(LivingEntity entity,NamespacedKey key,double amount) {
        var attribute=entity.getAttribute(Attribute.MOVEMENT_SPEED);if(attribute==null)return;
        if(attribute.getModifier(key)==null)attribute.addTransientModifier(new AttributeModifier(key,amount,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        moved.put(entity.getUniqueId(),entity);
    }
    private void remove(LivingEntity entity,NamespacedKey key) {
        var attribute=entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if(attribute!=null){var modifier=attribute.getModifier(key);if(modifier!=null)attribute.removeModifier(modifier);}
    }
    private void scrub(LivingEntity entity){remove(entity,pursuitKey);remove(entity,slowKey);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(EntityDamageByEntityEvent event) {
        if(tools.secondaryWeaponHit(event))return;
        if(event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK || event.getFinalDamage()<=0
                || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)
                || player.isDead())return;
        var item=player.getInventory().getItemInMainHand();var type=tools.typeOf(item);
        if(type==null||!type.weapon()||tools.levelOf(item)!=25)return;
        // Use the pre-attack snapshot; the engine can reset cooldown before damage events.
        if(tools.weaponAttackCharge(event)<.85f)return;
        var state=state(player);String instance=tools.instance(item);if(instance==null)return;
        synchronizeHand(player,instance);
        if(type==ToolType.DAGGER) {
            if(state.dagger(event.getEntity().getUniqueId(),instance,now())) {
                modifier(player,pursuitKey,.2);player.sendActionBar(Text.mm("<gold>추격 · 2초간 이동 속도 +20%"));
            }
        } else {
            state.resetCombo();
            if(type==ToolType.TACHI && state.tachi(now()))player.sendActionBar(Text.mm("<gold>공방일체 · 받는 피해 10% 감소"));
        }
    }
    boolean pressure(Player player,Collection<LivingEntity> targets) {
        if(!holding(player,ToolType.GREATSWORD)||targets.size()<3||!state(player).pressure(now()))return false;
        for(var target:targets)slow(target);
        player.sendActionBar(Text.mm("<gold>압도 · 맞힌 대상 1초간 둔화 30%"));return true;
    }
    void slow(LivingEntity target) {
        if(target.isDead()||!target.isValid())return;
        modifier(target,slowKey,-.3);slowed.merge(target.getUniqueId(),now()+20,Math::max);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void guard(EntityDamageEvent event) {
        if(!(event.getEntity() instanceof Player player)||event.getDamage()<=0)return;
        synchronizeHand(player,tools.instance(player.getInventory().getItemInMainHand()));
        var state=states.get(player.getUniqueId());
        if(state!=null&&holding(player,ToolType.TACHI)&&state.guard(now())) {
            // Scale the already-computed components together: reducing only raw damage
            // would recompute nonlinear armor and could grant more than 10% protection.
            for(var component:EntityDamageEvent.DamageModifier.values())if(event.isApplicable(component))
                event.setDamage(component,WeaponTraitState.guardedDamage(event.getDamage(component)));
        }
    }
    private void synchronizeHand(Player player,String instance) {
        String previous=held.put(player.getUniqueId(),instance);
        if(!Objects.equals(previous,instance)) {
            var state=states.get(player.getUniqueId());if(state!=null)state.unequip();remove(player,pursuitKey);
        }
    }
    private void tick() {
        long tick=now();
        for(var player:Bukkit.getOnlinePlayers()) {
            if(!states.containsKey(player.getUniqueId()))continue;
            synchronizeHand(player,tools.instance(player.getInventory().getItemInMainHand()));
            var item=player.getInventory().getItemInMainHand();var type=tools.typeOf(item);
            if(type==null||!type.weapon()||tools.levelOf(item)!=25)state(player).unequip();
            if(!holding(player,ToolType.DAGGER)||!state(player).pursuit(tick))remove(player,pursuitKey);
        }
        for(var entry:List.copyOf(slowed.entrySet()))if(tick>=entry.getValue()) {
            var entity=moved.get(entry.getKey());if(entity!=null)remove(entity,slowKey);slowed.remove(entry.getKey());
        }
        moved.entrySet().removeIf(e->!slowed.containsKey(e.getKey())&&(!(e.getValue() instanceof Player p)||!states.containsKey(p.getUniqueId())||!state(p).pursuit(tick)));
    }
    private void clear(Player player){states.remove(player.getUniqueId());held.remove(player.getUniqueId());remove(player,pursuitKey);}
    @EventHandler public void held(PlayerItemHeldEvent event){clearActive(event.getPlayer());}
    @EventHandler(ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent event){clearActive(event.getPlayer());}
    private void clearActive(Player player){var state=states.get(player.getUniqueId());if(state!=null)state.unequip();remove(player,pursuitKey);held.remove(player.getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent event){clear(event.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent event){clear(event.getEntity());}
    @EventHandler public void added(EntityAddToWorldEvent event){if(event.getEntity() instanceof LivingEntity entity)scrub(entity);}
    @EventHandler public void removed(EntityRemoveFromWorldEvent event){
        if(event.getEntity() instanceof LivingEntity entity){scrub(entity);slowed.remove(entity.getUniqueId());moved.remove(entity.getUniqueId());if(entity instanceof Player player)clear(player);}
    }
    void close(){timer.cancel();for(var entity:moved.values())scrub(entity);for(var player:Bukkit.getOnlinePlayers())scrub(player);states.clear();slowed.clear();moved.clear();held.clear();}
}
