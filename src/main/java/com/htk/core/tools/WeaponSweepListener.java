package com.htk.core.tools;

import com.htk.core.ServerCore;
import org.bukkit.Bukkit;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import java.util.*;

/** Custom player-centred sweep. Native victim-centred sweeps are always suppressed. */
final class WeaponSweepListener implements Listener {
    private final ServerCore plugin;
    private final VillageToolFeature tools;
    private final WeaponTraits traits;
    private final Set<Runnable> pending=new HashSet<>();
    private final Map<UUID,Prepared> prepared=new HashMap<>();
    private static final class Prepared {
        final PrePlayerAttackEntityEvent event;
        final int tick;
        final String instance;
        final float charge;
        boolean handled;
        Prepared(PrePlayerAttackEntityEvent event,String instance,float charge) {
            this.event=event;this.tick=Bukkit.getCurrentTick();this.instance=instance;this.charge=charge;
        }
    }
    private Player source;
    private LivingEntity secondaryTarget;
    private EntityDamageByEntityEvent secondaryEvent;
    WeaponSweepListener(ServerCore plugin,VillageToolFeature tools,WeaponTraits traits){this.plugin=plugin;this.tools=tools;this.traits=traits;}
    void close(){pending.clear();prepared.clear();source=null;secondaryTarget=null;secondaryEvent=null;}
    boolean secondary(EntityDamageByEntityEvent event){return event.getDamager()==source && event.getEntity()==secondaryTarget;}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void prepare(PrePlayerAttackEntityEvent event) {
        Player player=event.getPlayer();var item=player.getInventory().getItemInMainHand();
        var type=tools.typeOf(item);UUID id=player.getUniqueId();prepared.remove(id);
        if(!event.willAttack() || type==null || !type.weapon())return;
        var attack=new Prepared(event,tools.instance(item),player.getCooledAttackStrength(.5f));
        prepared.put(id,attack);
        Bukkit.getScheduler().runTask(plugin,()->prepared.remove(id,attack));
    }
    private Prepared prepared(EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Player player))return null;
        var attack=prepared.get(player.getUniqueId());
        return attack!=null && attack.tick==Bukkit.getCurrentTick() && !attack.event.isCancelled()
                && attack.event.getAttacked()==event.getEntity()
                && Objects.equals(attack.instance,tools.instance(player.getInventory().getItemInMainHand()))?attack:null;
    }
    float charge(EntityDamageByEntityEvent event) {
        var attack=prepared(event);return attack==null?0:attack.charge;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void captureSecondary(EntityDamageByEntityEvent event) {
        if(secondary(event))secondaryEvent=event;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void suppressNative(EntityDamageByEntityEvent event) {
        if(event.getCause()!=DamageCause.ENTITY_SWEEP_ATTACK || !(event.getDamager() instanceof Player player))return;
        var type=tools.typeOf(player.getInventory().getItemInMainHand());
        if(type!=null && type.weapon())event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void direct(EntityDamageByEntityEvent event) {
        if(secondary(event) || event.getCause()!=DamageCause.ENTITY_ATTACK || event.getFinalDamage()<=0
                || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity primary))return;
        var item=player.getInventory().getItemInMainHand();var type=tools.typeOf(item);
        String instance=tools.instance(item);
        var attack=prepared(event);
        if(type==null || !type.weapon() || instance==null || attack==null || attack.handled)return;
        attack.handled=true;
        if(!SweepTargets.ready(attack.charge,player.isOnGround(),player.isSprinting(),player.getFallDistance()))return;
        int level=tools.levelOf(item);
        var origin=player.getLocation();double reach=WeaponPerformance.reach(type,level);
        // Use the attack's player position, not the struck mob or the player's next-tick position.
        var candidates=new ArrayList<LivingEntity>();
        for(var entity:player.getWorld().getNearbyEntities(origin,reach,reach,reach)) {
            if(!(entity instanceof LivingEntity target) || target.equals(player) || target.equals(primary)
                    || !eligible(player,target,event) || !SweepTargets.inRange(origin.distanceSquared(target.getLocation()),reach))continue;
            candidates.add(target);
        }
        var nearest=Comparator.<LivingEntity>comparingDouble(t->origin.distanceSquared(t.getLocation()))
                .thenComparing(Entity::getUniqueId);
        var budget=new SweepTargets.Budget<>(candidates,WeaponPerformance.maxTargets(type),nearest);
        var selected=candidates.stream().filter(budget::claim).sorted(nearest).toList();
        double damage=1+player.getAttribute(Attribute.ATTACK_DAMAGE).getValue()*WeaponPerformance.sweepRatio(type,level);
        Runnable task=()->{
            if(event.isCancelled() || event.getFinalDamage()<=0 || !player.isOnline() || player.isDead()
                    || !player.getWorld().equals(origin.getWorld()))return;
            var held=player.getInventory().getItemInMainHand();
            if(tools.typeOf(held)!=type || tools.levelOf(held)!=level || !instance.equals(tools.instance(held)))return;
            Set<LivingEntity> landed=new HashSet<>();landed.add(primary);
            boolean pressure=false;
            for(var target:selected) {
                if(!eligible(player,target,event) || !target.getWorld().equals(origin.getWorld())
                        || !SweepTargets.inRange(origin.distanceSquared(target.getLocation()),reach))continue;
                source=player;secondaryTarget=target;secondaryEvent=null;
                try {
                    // Bukkit damage performs armour, protection/PvP events and kill attribution normally.
                    // The scoped secondary marker prevents recursion and direct-hit trait activation.
                    target.damage(damage,player);
                    if(secondaryEvent==null || secondaryEvent.isCancelled() || secondaryEvent.getFinalDamage()<=0)continue;
                    landed.add(target);
                    if(type==ToolType.GREATSWORD && level==25) {
                        if(pressure)traits.slow(target);
                        else if(landed.size()>=3)pressure=traits.pressure(player,landed);
                    }
                } finally {source=null;secondaryTarget=null;secondaryEvent=null;}
            }
        };
        pending.add(task);
        Bukkit.getScheduler().runTask(plugin,()->{if(pending.remove(task))task.run();});
    }
    private boolean eligible(Player player,LivingEntity target,EntityDamageByEntityEvent event) {
        return target.isValid() && !target.isDead() && !target.isInvulnerableTo(event.getDamageSource())
                && !(target instanceof ArmorStand stand && stand.isMarker())
                && !(target instanceof Player other && (other.getGameMode()==org.bukkit.GameMode.SPECTATOR || !player.getWorld().getPVP()))
                && !allied(player,target) && player.hasLineOfSight(target);
    }
    private boolean allied(Player player,LivingEntity target) {
        if(target instanceof Tameable pet && player.getUniqueId().equals(pet.getOwnerUniqueId()))return true;
        var scoreboard=Bukkit.getScoreboardManager().getMainScoreboard();
        var team=scoreboard.getEntryTeam(player.getScoreboardEntryName());
        return team!=null && team.equals(scoreboard.getEntryTeam(target.getScoreboardEntryName()));
    }
}
