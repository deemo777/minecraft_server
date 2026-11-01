package com.htk.core.land;

import com.htk.core.ServerCore;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.event.player.PlayerInteractEvent;

/** Optional WorldGuard 7 bridge. Never clears cancellation on a Bukkit event. */
final class WorldGuardCorePlacement {
    private WorldGuardCorePlacement() {}

    static void register(ServerCore plugin, LandFeature land) {
        var guard=plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if(guard==null || !guard.isEnabled()) return;
        try {
            // Resolve through WG's loader so WorldGuard remains an optional dependency.
            for(String query:List.of("PlaceBlockEvent","BreakBlockEvent","UseBlockEvent")) {
                Class<? extends Event> type=Class.forName(
                        "com.sk89q.worldguard.bukkit.event.block."+query,true,
                        guard.getClass().getClassLoader()).asSubclass(Event.class);
                EventExecutor executor=executor(type,query.equals("PlaceBlockEvent"),
                        source->isCorePlacement(source,land::allowedWorld,land::isCoreItem),
                        land::canBypassWorldGuardAt);
                plugin.getServer().getPluginManager().registerEvent(type,new Listener(){},EventPriority.LOWEST,
                        executor,plugin,true);
            }
        } catch(ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("WorldGuard 집터/건축 예외를 등록하지 못했습니다: "+ex);
        }
    }

    static EventExecutor executor(Class<? extends Event> type,boolean placement,Predicate<Event> core,
                                  BiPredicate<Player,Location> build) throws ReflectiveOperationException {
        Method original=type.getMethod("getOriginalEvent");
        Method result=type.getMethod("setResult",Event.Result.class);
        Method blocks=type.getMethod("getBlocks");
        // WG declares getBlocks on its package-private AbstractBlockEvent superclass.
        if(!blocks.trySetAccessible()) throw new IllegalAccessException("WorldGuard getBlocks");
        return (listener,event)->{
            try {
                Event source=(Event)original.invoke(event);
                boolean allowed=placement && core.test(source);
                if(!allowed) {
                    Player player=builder(source);
                    if(player!=null) {
                        @SuppressWarnings("unchecked")
                        List<Block> targets=(List<Block>)blocks.invoke(event);
                        allowed=!targets.isEmpty() && targets.stream().allMatch(block->build.test(player,block.getLocation()));
                    }
                }
                // Only pre-allow WG's query. Never revive a cancelled Bukkit event or change region flags.
                if(allowed) result.invoke(event,Event.Result.ALLOW);
            } catch(ReflectiveOperationException ex) {
                throw new EventException(ex);
            }
        };
    }

    private static Player builder(Event source) {
        if(source instanceof BlockPlaceEvent place) return place.getPlayer();
        if(source instanceof BlockBreakEvent broken) return broken.getPlayer();
        if(source instanceof PlayerInteractEvent interact) return interact.getPlayer();
        if(source instanceof PlayerBucketEvent bucket) return bucket.getPlayer();
        return null; // No indirect explosions, pistons, mobs or environmental changes.
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
