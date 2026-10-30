package com.htk.core.tools;

import com.htk.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;

/** Keep native sweep geometry and protection events, with a bounded nearest-target budget. */
final class WeaponSweepListener implements Listener {
    private final ServerCore plugin;
    private final VillageToolFeature tools;
    private final Map<UUID,Attack> attacks=new HashMap<>();
    private record Attack(ToolType type,int level,String instance,int tick,SweepTargets.Budget<UUID> budget,
                          Set<EntityDamageByEntityEvent> admitted) {}
    WeaponSweepListener(ServerCore plugin,VillageToolFeature tools){this.plugin=plugin;this.tools=tools;}
    void close(){attacks.clear();}
    @EventHandler(priority=EventPriority.MONITOR)
    public void direct(EntityDamageByEntityEvent event) {
        if(event.getCause()!=DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player))return;
        UUID playerId=player.getUniqueId();attacks.remove(playerId);
        if(event.isCancelled())return;
        var item=player.getInventory().getItemInMainHand();var type=tools.typeOf(item);
        String instance=tools.instance(item);
        if(type==null || !type.weapon() || instance==null)return;
        var primary=event.getEntity();var origin=primary.getLocation();
        var box=primary.getBoundingBox().expand(1,.25,1);
        Map<UUID,Double> distance=new HashMap<>();
        for(var entity:primary.getWorld().getNearbyEntities(box)) {
            if(!(entity instanceof LivingEntity target) || target.equals(player) || target.equals(primary)
                    || !target.isValid() || target.isDead() || target.isInvulnerableTo(event.getDamageSource())
                    || target instanceof ArmorStand stand && stand.isMarker()
                    || target instanceof Player other && other.getGameMode()==org.bukkit.GameMode.SPECTATOR
                    || allied(player,target) || player.getLocation().distanceSquared(target.getLocation())>=9)continue;
            distance.put(target.getUniqueId(),origin.distanceSquared(target.getLocation()));
        }
        var budget=new SweepTargets.Budget<>(distance.keySet(),WeaponPerformance.maxTargets(type),
                Comparator.<UUID>comparingDouble(distance::get).thenComparing(Comparator.naturalOrder()));
        var attack=new Attack(type,tools.levelOf(item),instance,Bukkit.getCurrentTick(),budget,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        attacks.put(playerId,attack);
        Bukkit.getScheduler().runTask(plugin,()->attacks.remove(playerId,attack));
    }
    private boolean allied(Player player,LivingEntity target) {
        if(target instanceof Tameable pet && player.getUniqueId().equals(pet.getOwnerUniqueId()))return true;
        var scoreboard=Bukkit.getScoreboardManager().getMainScoreboard();
        var team=scoreboard.getEntryTeam(player.getScoreboardEntryName());
        return team!=null && team.equals(scoreboard.getEntryTeam(target.getScoreboardEntryName()));
    }
    private Attack current(Player player) {
        var attack=attacks.get(player.getUniqueId());if(attack==null || attack.tick()!=Bukkit.getCurrentTick())return null;
        var item=player.getInventory().getItemInMainHand();
        return tools.typeOf(item)==attack.type() && tools.levelOf(item)==attack.level()
                && attack.instance().equals(tools.instance(item))?attack:null;
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void sweep(EntityDamageByEntityEvent event) {
        if(event.getCause()!=DamageCause.ENTITY_SWEEP_ATTACK || !(event.getDamager() instanceof Player player))return;
        var type=tools.typeOf(player.getInventory().getItemInMainHand());if(type==null || !type.weapon())return;
        var attack=current(player);
        if(attack==null || !attack.budget().claim(event.getEntity().getUniqueId())){event.setCancelled(true);return;}
        attack.admitted().add(event);
        double ratio=WeaponPerformance.sweepRatio(type,attack.level());
        if(ratio>1 && event.getDamage()>0)event.setDamage(WeaponPerformance.extendedSweepDamage(event.getDamage(),ratio));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void enforceLimit(EntityDamageByEntityEvent event) {
        if(event.getCause()!=DamageCause.ENTITY_SWEEP_ATTACK || !(event.getDamager() instanceof Player player))return;
        var type=tools.typeOf(player.getInventory().getItemInMainHand());if(type==null || !type.weapon())return;
        var attack=current(player);
        if(attack==null || !attack.admitted().contains(event))event.setCancelled(true);
        // Never un-cancel a selected target: land/PvP/other protection still takes priority.
    }
    @EventHandler public void quit(PlayerQuitEvent event){attacks.remove(event.getPlayer().getUniqueId());}
}
