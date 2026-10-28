package com.htk.core.land;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** 본인에게만 보이는 입자로 집터 설치 전 경계를 표시한다. */
final class LandPlacementPreview {
    private final ServerCore plugin;
    private final LandFeature land;
    private final Map<UUID,Integer> corners = new HashMap<>();
    private final Map<UUID,Map<Location,Material>> walls = new HashMap<>();
    private BukkitTask task;
    private static final String[] NAMES = {"왼쪽 위(북서)","오른쪽 위(북동)","오른쪽 아래(남동)","왼쪽 아래(남서)"};
    LandPlacementPreview(ServerCore plugin, LandFeature land) { this.plugin=plugin; this.land=land; }
    void start() { task=Bukkit.getScheduler().runTaskTimer(plugin, () -> {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    },1,4); }
    void stop() {
        if (task!=null) task.cancel();
        for (Player player:Bukkit.getOnlinePlayers()) clearWall(player);
        corners.clear();
    }
    void quit(Player p) { corners.remove(p.getUniqueId()); clearWall(p); }
    Set<Long> footprint(Player p,int x,int z) { return LandLayout.fromCorner(x,z,corners.getOrDefault(p.getUniqueId(),0)); }
    void cycle(Player p) {
        int c=(corners.getOrDefault(p.getUniqueId(),0)+1)%4; corners.put(p.getUniqueId(),c);
        p.sendActionBar(Text.mm("<#ffd34d>집터 위치: "+NAMES[c]+" <gray>· 웅크리기+우클릭으로 변경"));
        update(p);
    }
    private void update(Player p) {
        if (!land.allowedWorld(p.getWorld()) || !land.isCoreItem(p.getInventory().getItemInMainHand())
                || land.store().of(p.getUniqueId()) != null) { clearWall(p); return; }
        var hit=p.getWorld().rayTraceBlocks(p.getEyeLocation(),p.getEyeLocation().getDirection(),6,FluidCollisionMode.NEVER,true);
        if (hit==null || hit.getHitBlock()==null || hit.getHitBlockFace()==null) { clearWall(p); return; }
        Block target=hit.getHitBlock().getRelative(hit.getHitBlockFace());
        int cx=target.getX()>>4,cz=target.getZ()>>4;
        Set<Long> plot=footprint(p,cx,cz);
        boolean valid=target.isReplaceable();
        for (long k:plot) {
            if (!land.zones().allows(Land.cx(k),Land.cz(k)) || land.store().at(LandFeature.HOME_WORLD,Land.cx(k),Land.cz(k))!=null) valid=false;
        }
        int minX=plot.stream().mapToInt(Land::cx).min().orElseThrow()*16;
        int minZ=plot.stream().mapToInt(Land::cz).min().orElseThrow()*16;
        showWall(p,minX,minZ,32,target.getY());
        p.sendActionBar(Text.mm((valid?"<#7acb52>설치 가능":"<#f05b60>설치 불가")
                +" <gray>· 집터 "+NAMES[corners.getOrDefault(p.getUniqueId(),0)]+" · 웅크리기+우클릭: 변경"));
    }

    private void showWall(Player p,int minX,int minZ,int width,int baseY) {
        Map<Location,Material> next=new HashMap<>();
        for(int y=baseY;y<baseY+2;y++) for(int i=0;i<width;i++) {
            Material material=(i%4==0 || i==width-1)?Material.RED_STAINED_GLASS:Material.LIGHT_GRAY_STAINED_GLASS;
            add(next,p.getWorld(),minX+i,y,minZ,material);
            add(next,p.getWorld(),minX+i,y,minZ+width-1,material);
            add(next,p.getWorld(),minX,y,minZ+i,material);
            add(next,p.getWorld(),minX+width-1,y,minZ+i,material);
        }
        Map<Location,Material> previous=walls.get(p.getUniqueId());
        if(next.equals(previous)) return;
        if(previous!=null) for(Location location:previous.keySet()) if(!next.containsKey(location)) restore(p,location);
        next.forEach((location,material)->p.sendBlockChange(location,material.createBlockData()));
        walls.put(p.getUniqueId(),next);
    }

    private void add(Map<Location,Material> blocks,World world,int x,int y,int z,Material material) {
        blocks.put(new Location(world,x,y,z),material);
    }

    private void clearWall(Player p) {
        Map<Location,Material> previous=walls.remove(p.getUniqueId());
        if(previous!=null) previous.keySet().forEach(location->restore(p,location));
    }

    private void restore(Player p,Location location) {
        World world=location.getWorld();
        if(world==null || !world.isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4)) return;
        BlockData actual=world.getBlockAt(location).getBlockData();
        p.sendBlockChange(location,actual);
    }
}
