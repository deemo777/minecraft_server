package com.htk.core.land;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** 본인에게만 보이는 입자로 집터 설치 전 경계를 표시한다. */
final class LandPlacementPreview {
    private final ServerCore plugin;
    private final LandFeature land;
    private final Map<UUID,Integer> corners = new HashMap<>();
    private BukkitTask task;
    private static final String[] NAMES = {"왼쪽 위(북서)","오른쪽 위(북동)","오른쪽 아래(남동)","왼쪽 아래(남서)"};
    LandPlacementPreview(ServerCore plugin, LandFeature land) { this.plugin=plugin; this.land=land; }
    void start() { task=Bukkit.getScheduler().runTaskTimer(plugin, () -> {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    },1,4); }
    void stop() { if (task!=null) task.cancel(); corners.clear(); }
    void quit(Player p) { corners.remove(p.getUniqueId()); }
    Set<Long> footprint(Player p,int x,int z) { return LandLayout.fromCorner(x,z,corners.getOrDefault(p.getUniqueId(),0)); }
    void cycle(Player p) {
        int c=(corners.getOrDefault(p.getUniqueId(),0)+1)%4; corners.put(p.getUniqueId(),c);
        p.sendActionBar(Text.mm("<#ffd34d>집터 위치: "+NAMES[c]+" <gray>· 웅크리기+우클릭으로 변경"));
        update(p);
    }
    private void update(Player p) {
        if (!land.allowedWorld(p.getWorld()) || !land.isCoreItem(p.getInventory().getItemInMainHand())
                || land.store().of(p.getUniqueId()) != null) return;
        var hit=p.getWorld().rayTraceBlocks(p.getEyeLocation(),p.getEyeLocation().getDirection(),6,FluidCollisionMode.NEVER,true);
        if (hit==null || hit.getHitBlock()==null || hit.getHitBlockFace()==null) return;
        Block target=hit.getHitBlock().getRelative(hit.getHitBlockFace());
        int cx=target.getX()>>4,cz=target.getZ()>>4;
        Set<Long> plot=footprint(p,cx,cz);
        boolean valid=target.isReplaceable();
        for (long k:plot) {
            if (!land.zones().allows(Land.cx(k),Land.cz(k)) || land.store().at(LandFeature.HOME_WORLD,Land.cx(k),Land.cz(k))!=null) valid=false;
        }
        int minX=plot.stream().mapToInt(Land::cx).min().orElseThrow()*16;
        int minZ=plot.stream().mapToInt(Land::cz).min().orElseThrow()*16;
        double y=target.getY()+.08;
        stripedSquare(p,minX,minZ,32,y);
        line(p,minX+16,minZ,minX+16,minZ+32,y,Color.fromRGB(0xFF7777));
        line(p,minX,minZ+16,minX+32,minZ+16,y,Color.fromRGB(0xFF7777));
        square(p,cx*16,cz*16,16,y+.12,Color.fromRGB(valid?0x7ACB52:0xF05B60));
        p.sendActionBar(Text.mm((valid?"<#7acb52>설치 가능":"<#f05b60>설치 불가")
                +" <gray>· 집터 "+NAMES[corners.getOrDefault(p.getUniqueId(),0)]+" · 웅크리기+우클릭: 변경"));
    }
    private void square(Player p,int x,int z,int width,double y,Color c) {
        line(p,x,z,x+width,z,y,c);line(p,x+width,z,x+width,z+width,y,c);
        line(p,x+width,z+width,x,z+width,y,c);line(p,x,z+width,x,z,y,c);
    }
    private void stripedSquare(Player p,int x,int z,int width,double y) {
        stripedWall(p,x,z,x+width,z,y);stripedWall(p,x+width,z,x+width,z+width,y);
        stripedWall(p,x+width,z+width,x,z+width,y);stripedWall(p,x,z+width,x,z,y);
    }
    private void stripedWall(Player p,int x0,int z0,int x1,int z1,double y) {
        int length=Math.max(Math.abs(x1-x0),Math.abs(z1-z0));
        int heightSteps=4;
        for(int i=0;i<=length;i++) {
            double x=x0+(x1-x0)*i/(double)length;
            double z=z0+(z1-z0)*i/(double)length;
            Color color=(i%2==0)?Color.fromRGB(0xFF3030):Color.fromRGB(0xFFFFFF);
            var dust=new Particle.DustOptions(color,1.5f);
            for(int h=0;h<=heightSteps;h++) p.spawnParticle(Particle.DUST,
                    x,y+h*.5,z,1,0,0,0,0,dust);
        }
    }
    private void line(Player p,int x0,int z0,int x1,int z1,double y,Color c) {
        int steps=Math.max(Math.abs(x1-x0),Math.abs(z1-z0))/2;
        var dust=new Particle.DustOptions(c,1.25f);
        for (int i=0;i<=steps;i++) p.spawnParticle(Particle.DUST,
                x0+(x1-x0)*i/(double)steps,y,z0+(z1-z0)*i/(double)steps,1,0,0,0,0,dust);
    }
}
