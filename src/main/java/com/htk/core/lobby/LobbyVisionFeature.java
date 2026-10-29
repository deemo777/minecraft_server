package com.htk.core.lobby;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Silent lobby lighting. The vanilla inventory effect list cannot be hidden by the server. */
public final class LobbyVisionFeature implements Feature, Listener {
    private static final int DURATION=600, PERIOD=40;
    private final ServerCore plugin;
    private final NamespacedKey marker;
    private final Map<UUID,SavedEffect> previous=new HashMap<>();
    private final Set<UUID> changing=new HashSet<>();
    private BukkitTask task;
    private boolean active;

    private record SavedEffect(VisionEffect effect, int tick) {}

    public LobbyVisionFeature(ServerCore plugin) {
        this.plugin=plugin;
        marker=new NamespacedKey(plugin,"lobby_night_vision");
    }

    @Override public void enable() {
        active=true;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player player:Bukkit.getOnlinePlayers()) recover(player);
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->Bukkit.getOnlinePlayers().forEach(this::sync),PERIOD,PERIOD);
    }
    @Override public void reload() { Bukkit.getOnlinePlayers().forEach(this::sync); }
    @Override public void disable() {
        active=false;
        if(task!=null)task.cancel();
        Bukkit.getOnlinePlayers().forEach(this::leave);
        previous.clear();changing.clear();
    }

    private static boolean lobby(Player player) { return player.getWorld().getName().equals("lobby"); }
    private static boolean managed(PotionEffect effect) {
        return effect!=null && effect.getAmplifier()==0 && effect.isAmbient()
            && !effect.hasParticles() && !effect.hasIcon() && effect.getDuration()>0 && effect.getDuration()<=DURATION;
    }
    private void recover(Player player) {
        // A persisted lease from a stopped/crashed server must not leak into another world.
        if(player.getPersistentDataContainer().has(marker,PersistentDataType.BYTE)) {
            change(player,()->{if(managed(player.getPotionEffect(PotionEffectType.NIGHT_VISION)))
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);});
            player.getPersistentDataContainer().remove(marker);
        }
        sync(player);
    }
    private void sync(Player player) {
        if(!active || !player.isOnline())return;
        if(!lobby(player)){leave(player);return;}
        if(player.isDead())return;
        UUID id=player.getUniqueId();
        PotionEffect current=player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        previous.putIfAbsent(id,new SavedEffect(VisionEffect.capture(current),Bukkit.getCurrentTick()));
        if(managed(current) && current.getDuration()>DURATION-PERIOD)return;
        change(player,()->{
            if(current!=null && !managed(current))player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            if(player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,DURATION,0,true,false,false)))
                player.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
        });
    }
    private void leave(Player player) {
        SavedEffect saved=previous.remove(player.getUniqueId());
        if(saved==null)return;
        change(player,()->{
            if(!managed(player.getPotionEffect(PotionEffectType.NIGHT_VISION)))return;
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            VisionEffect restored=saved.effect()==null?null:saved.effect().remaining(Bukkit.getCurrentTick()-saved.tick());
            if(restored!=null)player.addPotionEffect(restored.potion());
        });
        player.getPersistentDataContainer().remove(marker);
    }
    private void change(Player player,Runnable action) {
        UUID id=player.getUniqueId();changing.add(id);
        try {action.run();} finally {changing.remove(id);}
    }
    @EventHandler public void join(PlayerJoinEvent event) {recover(event.getPlayer());}
    @EventHandler public void world(PlayerChangedWorldEvent event) {sync(event.getPlayer());}
    @EventHandler public void respawn(PlayerPostRespawnEvent event) {sync(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event) {leave(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void effect(EntityPotionEffectEvent event) {
        if(!active || event.getModifiedType()!=PotionEffectType.NIGHT_VISION
            || !(event.getEntity() instanceof Player player) || !lobby(player) || changing.contains(player.getUniqueId()))return;
        if(event.getAction()==EntityPotionEffectEvent.Action.CHANGED && !event.isOverride())return;
        // Preserve a newly consumed potion on exit, but never resurrect one removed by milk/death.
        previous.put(player.getUniqueId(),new SavedEffect(VisionEffect.capture(event.getNewEffect()),Bukkit.getCurrentTick()));
        Bukkit.getScheduler().runTask(plugin,()->sync(player));
    }
}
