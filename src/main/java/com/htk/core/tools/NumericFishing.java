package com.htk.core.tools;

import com.htk.core.ServerCore;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Capture rod level per hook; no lure/luck enchantment or treasure duplication. */
final class NumericFishing implements Listener {
    private final ServerCore plugin;
    private final VillageToolFeature tools;
    private final NamespacedKey instance;
    private record Cast(UUID player,EquipmentSlot hand,String instance,int level) {}
    private final Map<UUID,Cast> casts=new HashMap<>();
    private final Set<Runnable> rewards=new LinkedHashSet<>();
    private static final Set<Material> FISH=Set.of(Material.COD,Material.SALMON,Material.PUFFERFISH,Material.TROPICAL_FISH);
    static boolean eligible(Material material){return FISH.contains(material);}
    NumericFishing(ServerCore plugin,VillageToolFeature tools) {
        this.plugin=plugin;this.tools=tools;instance=new NamespacedKey(plugin,"tool_instance");
    }
    void close(){for(var reward:List.copyOf(rewards))if(rewards.remove(reward))reward.run();casts.clear();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void fish(PlayerFishEvent event) {
        var player=event.getPlayer();var hook=event.getHook();var id=hook.getUniqueId();
        if(event.getState()==PlayerFishEvent.State.FISHING) {
            var hand=event.getHand()==EquipmentSlot.OFF_HAND?EquipmentSlot.OFF_HAND:EquipmentSlot.HAND;
            var rod=player.getInventory().getItem(hand);
            if(tools.typeOf(rod)!=ToolType.FISHING_ROD)return;
            String key=rod.getItemMeta().getPersistentDataContainer().get(instance,PersistentDataType.STRING);
            if(key==null)return;
            int level=tools.levelOf(rod);double factor=ToolPerformance.fishingWaitFactor(level);
            hook.setApplyLure(false);
            hook.setWaitTime(Math.max(1,(int)Math.ceil(hook.getMinWaitTime()*factor)),Math.max(1,(int)Math.ceil(hook.getMaxWaitTime()*factor)));
            hook.setLureTime(Math.max(1,(int)Math.ceil(hook.getMinLureTime()*factor)),Math.max(1,(int)Math.ceil(hook.getMaxLureTime()*factor)));
            casts.put(id,new Cast(player.getUniqueId(),hand,key,level));return;
        }
        if(event.getState()==PlayerFishEvent.State.BITE)return;
        Cast cast=casts.remove(id);
        if(cast==null || event.getState()!=PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught))return;
        var rod=player.getInventory().getItem(cast.hand());
        if(tools.typeOf(rod)!=ToolType.FISHING_ROD || !cast.instance().equals(rod.getItemMeta().getPersistentDataContainer().get(instance,PersistentDataType.STRING)))return;
        var fish=caught.getItemStack().clone();
        if(!eligible(fish.getType()) || ThreadLocalRandom.current().nextDouble()>=ToolPerformance.fishingExtraChance(cast.level()))return;
        fish.setAmount(1);var at=player.getLocation().clone();
        Runnable reward=()->{
            if(event.isCancelled())return;
            if(player.isOnline()&&!player.isDead())player.getInventory().addItem(fish).values().forEach(left->at.getWorld().dropItemNaturally(at,left));
            else at.getWorld().dropItemNaturally(at,fish);
        };
        rewards.add(reward);Bukkit.getScheduler().runTask(plugin,()->{if(rewards.remove(reward))reward.run();});
    }
    @EventHandler public void quit(PlayerQuitEvent event){casts.values().removeIf(c->c.player().equals(event.getPlayer().getUniqueId()));}
    @EventHandler public void removed(EntityRemoveEvent event){casts.remove(event.getEntity().getUniqueId());}
}
