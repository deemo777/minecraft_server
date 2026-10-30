package com.htk.core.tools;
import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.*;
/** Global removal of hurt cooldown, independent of attack charge. */
public final class CombatImmunityFeature implements Feature,Listener {
 private final ServerCore plugin;
 private final Map<UUID,Integer> original=new HashMap<>();
 public CombatImmunityFeature(ServerCore plugin){this.plugin=plugin;}
 private void apply(LivingEntity e){original.putIfAbsent(e.getUniqueId(),e.getMaximumNoDamageTicks());e.setMaximumNoDamageTicks(0);e.setNoDamageTicks(0);}
 public void enable(){Bukkit.getPluginManager().registerEvents(this,plugin);for(var w:Bukkit.getWorlds())for(var e:w.getLivingEntities())apply(e);}
 public void reload(){}
 public void disable(){for(var w:Bukkit.getWorlds())for(var e:w.getLivingEntities()){Integer old=original.get(e.getUniqueId());if(old!=null)e.setMaximumNoDamageTicks(old);}original.clear();}
 @EventHandler public void add(EntityAddToWorldEvent event){if(event.getEntity() instanceof LivingEntity e)apply(e);}
 @EventHandler public void remove(EntityRemoveFromWorldEvent event){if(event.getEntity() instanceof LivingEntity e){Integer old=original.remove(e.getUniqueId());if(old!=null)e.setMaximumNoDamageTicks(old);}}
}
