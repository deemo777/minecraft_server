package com.htk.core.farm;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.persistence.PersistentDataContainer;

/** Actual listener and chunk-PDC regression, without requiring a running game world. */
public final class SoilBlockRegression {
    private static int checks;
    @SuppressWarnings("unchecked")
    private static <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    public static void main(String[] args) {
        Map<Object,Object> saved=new HashMap<>();
        var data=proxy(PersistentDataContainer.class,(p,m,a)->switch(m.getName()) {
            case "get" -> saved.get(a[0]);
            case "set" -> {saved.put(a[0],a[2]);yield null;}
            case "remove" -> {saved.remove(a[0]);yield null;}
            default -> null;
        });
        var chunk=proxy(Chunk.class,(p,m,a)->m.getName().equals("getPersistentDataContainer")?data:null);
        int[] moisture={7};
        Farmland farm=proxy(Farmland.class,(p,m,a)->switch(m.getName()) {
            case "getMoisture" -> moisture[0];
            case "getMaximumMoisture" -> 7;
            case "setMoisture" -> {moisture[0]=(int)a[0];yield null;}
            default -> null;
        });
        Block soil=proxy(Block.class,(p,m,a)->switch(m.getName()) {
            case "getX" -> -17;
            case "getZ" -> 31;
            case "getY" -> -32;
            case "getChunk" -> chunk;
            case "getLocation" -> new Location(null,-17,-32,31);
            case "getBlockData" -> farm;
            case "getType" -> Material.FARMLAND;
            default -> null;
        });
        FarmPlotFeature feature=new FarmPlotFeature(null);
        feature.mark(soil,true);
        check(feature.marked(soil),"placement persists marker at negative coordinates and Y");
        Block piston=proxy(Block.class,(p,m,a)->m.getName().equals("getRelative")?soil:null);
        var push=new BlockPistonExtendEvent(piston,java.util.List.of(),BlockFace.EAST);
        feature.protectFromPiston(push);
        check(push.isCancelled(),"piston cannot destroy soil absent from the moved-block list");
        feature.mark(soil,true);
        var restored=new FarmPlotFeature(null);
        check(restored.marked(soil),"marker survives a new feature instance/chunk-PDC reload");
        var dirt=proxy(BlockState.class,(p,m,a)->m.getName().equals("getType")?Material.DIRT:null);
        var fade=new BlockFadeEvent(soil,dirt);
        restored.preventDryDecay(fade);
        check(fade.isCancelled() && restored.marked(soil),"dry empty soil cannot decay to dirt or lose its marker");
        Ageable mature=proxy(Ageable.class,(p,m,a)->m.getName().equals("getAge") || m.getName().equals("getMaximumAge")?7:null);
        Block crop=proxy(Block.class,(p,m,a)->m.getName().equals("getBlockData")?mature:m.getName().equals("getRelative")?soil:null);
        Player player=proxy(Player.class,(p,m,a)->m.getName().equals("getGameMode")?GameMode.CREATIVE:null);
        restored.resetMoistureAfterMatureHarvest(new BlockBreakEvent(crop,player));
        check(moisture[0]==0 && restored.dryLocked(soil) && restored.marked(soil),"mature crop harvest dries soil but preserves custom identity");
        var newWet=proxy(BlockState.class,(p,m,a)->m.getName().equals("getBlockData")?farm:null);
        moisture[0]=7;
        var hydrate=new MoistureChangeEvent(soil,newWet);
        restored.moisture(hydrate);
        check(hydrate.isCancelled(),"passive moisture cannot bypass post-harvest dry lock");
        moisture[0]=0;
        restored.watered(soil);
        check(moisture[0]==7 && !restored.dryLocked(soil),"watering can clears the lock and restores maximum moisture");
        check(new FarmPlotFeature(null).marked(soil),"watering cannot erase persistent soil identity");
        BlockBreakEvent broken=new BlockBreakEvent(soil,player);
        restored.stopVanillaDrop(broken);
        check(!broken.isDropItems(),"soil suppresses vanilla dirt drops");
        restored.refund(broken);
        check(!restored.marked(soil) && !restored.dryLocked(soil),"removal clears both markers; creative produces no duplicate item");
        var ordinaryFade=new BlockFadeEvent(soil,dirt);
        restored.preventDryDecay(ordinaryFade);
        check(!ordinaryFade.isCancelled(),"ordinary farmland retains vanilla decay behavior");
        System.out.println("Soil block PDC/decay/harvest/watering/removal checks passed: "+checks);
    }
    private static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
}
