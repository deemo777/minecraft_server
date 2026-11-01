package com.htk.core.land;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** 본인에게만 보이는 충돌 없는 디스플레이로 설치 전 경계를 표시한다. */
final class LandPlacementPreview {
    private final ServerCore plugin;
    private final LandFeature land;
    private final Map<UUID,Integer> corners = new HashMap<>();
    private record Wall(World world, int x, int z, int width, int y) {}
    private final Map<UUID,Wall> walls = new HashMap<>();
    private final Map<UUID,List<BlockDisplay>> displays = new HashMap<>();
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
        Wall next=new Wall(p.getWorld(),minX,minZ,width,baseY);
        if(next.equals(walls.get(p.getUniqueId()))) return;
        clearWall(p);
        List<BlockDisplay> list=new ArrayList<>();
        // Thin translucent images on the exact plot edges; display entities have no collision.
        for(int side=0;side<4;side++) {
            boolean alongX=side<2;
            double x=minX+(side==3?width:0), z=minZ+(side==1?width:0);
            list.add(panel(p,x,baseY,z,alongX?width:.04f,2,alongX?.04f:width,Material.LIGHT_GRAY_STAINED_GLASS));
            for(int i=0;i<=width;i+=4) {
                list.add(panel(p,x+(alongX?i:0)-.025,baseY,z+(alongX?0:i)-.025,
                        .09f,2,.09f,Material.RED_STAINED_GLASS));
            }
        }
        walls.put(p.getUniqueId(),next);
        displays.put(p.getUniqueId(),list);
    }

    private BlockDisplay panel(Player p,double x,double y,double z,float sx,float sy,float sz,Material material) {
        BlockDisplay display=p.getWorld().spawn(new Location(p.getWorld(),x,y,z),BlockDisplay.class,e->{
            e.setBlock(material.createBlockData());
            e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(sx,sy,sz),new Quaternionf()));
            e.setBrightness(new Display.Brightness(15,15));
            e.setPersistent(false);
            e.setVisibleByDefault(false);
        });
        p.showEntity(plugin,display);
        return display;
    }

    private void clearWall(Player p) {
        walls.remove(p.getUniqueId());
        List<BlockDisplay> previous=displays.remove(p.getUniqueId());
        if(previous!=null) previous.forEach(BlockDisplay::remove);
    }
}
