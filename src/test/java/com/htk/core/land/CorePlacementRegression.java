package com.htk.core.land;

import java.lang.reflect.Proxy;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Exercise actual Bukkit event types without starting a server. */
final class CorePlacementRegression {
    static void run() {
        Player player=player(false);
        Block block=(Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},
                (proxy,method,args)->null);
        BlockPlaceEvent place=new BlockPlaceEvent(block,null,block,null,player,true,EquipmentSlot.HAND);
        check(allowed(place,true,true),"village core bypasses placement query");
        check(!allowed(place,true,false),"ordinary blocks do not bypass guard");
        check(!allowed(place,false,true),"other worlds do not bypass guard");
        place.setCancelled(true);
        allowed(place,true,true);
        check(place.isCancelled(),"guard bridge never revives a cancelled Bukkit placement");
        for(Action action:Action.values()) {
            var interact=new PlayerInteractEvent(player,action,null,block,BlockFace.UP,EquipmentSlot.HAND);
            check(allowed(interact,true,true)==(action==Action.RIGHT_CLICK_BLOCK),"only placement right clicks qualify: "+action);
            check(!allowed(interact,true,false),"non-core right clicks keep protection");
            check(!allowed(interact,false,true),"right clicks outside village keep protection");
        }
        var sneak=new PlayerInteractEvent(player(true),Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);
        check(!allowed(sneak,true,true),"corner selection does not bypass guard");
        check(!allowed(null,true,true),"missing original events do not bypass guard");
        System.out.println("Core placement event regression checks passed.");
    }

    private static boolean allowed(Event event,boolean village,boolean core) {
        return WorldGuardCorePlacement.isCorePlacement(event,world->village,item->core);
    }

    private static Player player(boolean sneaking) {
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
                (proxy,method,args)->method.getName().equals("isSneaking")?sneaking:null);
    }

    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
}
