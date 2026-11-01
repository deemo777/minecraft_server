package com.htk.core.land;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class WorldGuardBuildRegression {
    static void run() {
        UUID owner=UUID.randomUUID(),neighbor=UUID.randomUUID(),stranger=UUID.randomUUID();
        Land land=new Land(owner,"Owner","village");
        land.chunks.add(Land.key(0,0));
        land.plot.addAll(LandLayout.fromCorner(0,0,0));
        land.members.add(neighbor);
        check(rights(land,owner,0,true),"owner can build in unlocked home");
        check(rights(land,neighbor,0,true),"explicitly trusted neighbor keeps build access");
        check(!rights(land,stranger,0,true),"stranger has no WG exception");
        check(!rights(land,owner,1,true),"reserved but locked chunk has no WG exception");
        check(!rights(land,owner,2,true),"outside plot has no WG exception");
        check(!rights(null,owner,0,true),"common land has no WG exception");
        check(!rights(land,owner,0,false),"other worlds have no WG exception");
        land.chunks.add(Land.key(1,0));
        check(rights(land,owner,1,true),"unlock grants WG exception immediately");
        land.members.remove(neighbor);
        check(!rights(land,neighbor,0,true),"untrust revokes WG exception immediately");
        try {
            Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
                    (proxy,method,args)->null);
            Block inside=block(15),outside=block(16);
            var executor=WorldGuardCorePlacement.executor(Query.class,false,source->true,
                    (p,location)->location.getBlockX()<16);
            var broken=new BlockBreakEvent(inside,player);
            Query allowed=new Query(broken,List.of(inside));
            executor.execute(null,allowed);
            check(allowed.result==Event.Result.ALLOW,"actual query executor permits home block breaking");
            Query mixed=new Query(broken,List.of(inside,outside));
            executor.execute(null,mixed);
            check(mixed.result==Event.Result.DEFAULT,"all affected blocks must be inside authorized land");
            Query empty=new Query(broken,List.of());
            executor.execute(null,empty);
            check(empty.result==Event.Result.DEFAULT,"empty target query is not allowed");
            Query indirect=new Query(null,List.of(inside));
            executor.execute(null,indirect);
            check(indirect.result==Event.Result.DEFAULT,"environmental changes do not inherit player rights");
            broken.setCancelled(true);
            executor.execute(null,allowed);
            check(broken.isCancelled(),"query allowance preserves Bukkit cancellation");
            var placement=WorldGuardCorePlacement.executor(Query.class,true,source->true,(p,location)->false);
            var place=new BlockPlaceEvent(outside,null,inside,null,player,true,EquipmentSlot.HAND);
            Query core=new Query(place,List.of(outside));
            placement.execute(null,core);
            check(core.result==Event.Result.ALLOW,"core placement exception remains available outside owned land");
            var ordinary=WorldGuardCorePlacement.executor(Query.class,true,source->false,
                    (p,location)->location.getBlockX()<16);
            Query homePlace=new Query(place,List.of(inside));
            ordinary.execute(null,homePlace);
            check(homePlace.result==Event.Result.ALLOW,"ordinary block placement is allowed in home");
            Query outsidePlace=new Query(place,List.of(outside));
            ordinary.execute(null,outsidePlace);
            check(outsidePlace.result==Event.Result.DEFAULT,"ordinary block placement outside home retains WG checks");
            var interact=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,inside,BlockFace.UP,EquipmentSlot.HAND);
            Query use=new Query(interact,List.of(inside));
            executor.execute(null,use);
            check(use.result==Event.Result.ALLOW,"home block use is allowed");
            Query outsideUse=new Query(interact,List.of(outside));
            executor.execute(null,outsideUse);
            check(outsideUse.result==Event.Result.DEFAULT,"outside block use retains WG checks");
        } catch(Exception ex) { throw new AssertionError(ex); }
        System.out.println("WorldGuard home build access/query regression checks passed.");
    }

    private static boolean rights(Land land,UUID player,int cx,boolean village) {
        return LandAccessRules.canBypassWorldGuard(village,land,player,cx,0);
    }

    private static Block block(int x) {
        return (Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},
                (proxy,method,args)->method.getName().equals("getLocation")?new Location(null,x,64,0):null);
    }

    // Like WG, the target accessor is inherited from a package-private superclass.
    static abstract class Targets extends Event {
        final List<Block> targets;
        Targets(List<Block> targets) { this.targets=targets; }
        public List<Block> getBlocks() { return targets; }
    }
    public static final class Query extends Targets {
        private static final HandlerList HANDLERS=new HandlerList();
        final Event source;
        Event.Result result=Event.Result.DEFAULT;
        Query(Event source,List<Block> targets) { super(targets);this.source=source; }
        public Event getOriginalEvent() { return source; }
        public void setResult(Event.Result result) { this.result=result; }
        @Override public HandlerList getHandlers() { return HANDLERS; }
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
}
