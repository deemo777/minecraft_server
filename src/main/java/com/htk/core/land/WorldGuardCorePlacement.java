package com.htk.core.land;

import com.htk.core.ServerCore;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Optional WorldGuard 7 bridge. Never clears cancellation on a Bukkit event. */
final class WorldGuardCorePlacement {
    private WorldGuardCorePlacement() {}

    static void register(ServerCore plugin, LandFeature land) {
        var guard=plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if(guard==null || !guard.isEnabled()) return;
        try {
            // Resolve through WG's loader so WorldGuard remains an optional dependency.
            Class<? extends Event> type=Class.forName(
                    "com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent",true,
                    guard.getClass().getClassLoader()).asSubclass(Event.class);
            var original=type.getMethod("getOriginalEvent");
            var result=type.getMethod("setResult",Event.Result.class);
            plugin.getServer().getPluginManager().registerEvent(type,new Listener(){},EventPriority.LOWEST,
                    (listener,event)->{
                        try {
                            Event source=(Event)original.invoke(event);
                            if(isCorePlacement(source,land::allowedWorld,land::isCoreItem)) {
                                result.invoke(event,Event.Result.ALLOW);
                            }
                            // LandFeature still validates ownership, zones and all four reserved chunks.
                        } catch(ReflectiveOperationException ex) {
                            throw new EventException(ex);
                        }
                    },plugin,true);
        } catch(ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("WorldGuard 집터 설치 예외를 등록하지 못했습니다: "+ex);
        }
    }

    static boolean isCorePlacement(Event source, java.util.function.Predicate<org.bukkit.World> world,
                                   java.util.function.Predicate<org.bukkit.inventory.ItemStack> core) {
        if(source instanceof BlockPlaceEvent place) {
            return world.test(place.getBlock().getWorld()) && core.test(place.getItemInHand());
        }
        return source instanceof PlayerInteractEvent interact
                && interact.getAction()==Action.RIGHT_CLICK_BLOCK
                && !interact.getPlayer().isSneaking()
                && world.test(interact.getPlayer().getWorld()) && core.test(interact.getItem());
    }

}
