package com.htk.core.land;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 한 사람의 집 땅 (청크 여러 개, 같은 월드) */
public final class Land {

    final UUID owner;
    String ownerName;
    final String world;
    /** 청크 키 (Land.key). 처음 정한 청크가 맨 앞 */
    final LinkedHashSet<Long> chunks = new LinkedHashSet<>();
    /** Reserved footprint, including locked chunks. Empty only for legacy irregular land. */
    final LinkedHashSet<Long> plot = new LinkedHashSet<>();
    final Set<UUID> members = new LinkedHashSet<>();
    long debt;
    /** 집터 블록 위치 (없으면 hasCore=false) */
    boolean hasCore;
    int coreX, coreY, coreZ;
    /** 집터를 놓을 때 바라본 방향 = 집 지도의 "위" (집터의 문은 그 반대쪽, 놓은 사람 쪽을 봐요) */
    org.bukkit.block.BlockFace facing = org.bukkit.block.BlockFace.NORTH;
    double homeX, homeY, homeZ;
    float homeYaw, homePitch;

    Land(UUID owner, String ownerName, String world) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
    }

    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public String world() { return world; }
    public long debt() { return debt; }
    public int size() { return chunks.size(); }
    public boolean fixedPlot() { return plot.size() == 4; }
    public Set<Long> reservedChunks() { return plot.isEmpty() ? chunks : plot; }
    public boolean reserves(int cx, int cz) { return reservedChunks().contains(key(cx, cz)); }
    Long nextLockedChunk() {
        for (long chunk : plot) if (!chunks.contains(chunk)) return chunk;
        return null;
    }
    public Set<UUID> members() { return members; }

    public long firstChunk() {
        return chunks.iterator().next();
    }

    public boolean trusts(UUID id) {
        return owner.equals(id) || members.contains(id);
    }

    public boolean isCore(org.bukkit.block.Block b) {
        return hasCore && b.getX() == coreX && b.getY() == coreY && b.getZ() == coreZ
                && b.getWorld().getName().equals(world);
    }

    /**
     * 지도에서 집터 칸 기준 (오른쪽 a, 아래 b) 칸 → 실제 청크 좌표 차이 {dx, dz}.
     * 지도의 위 = facing 방향, 오른쪽 = facing 에서 시계 방향으로 90도
     */
    public int[] mapOffset(int a, int b) {
        return switch (facing) {
            case SOUTH -> new int[]{-a, -b};
            case EAST -> new int[]{-b, a};
            case WEST -> new int[]{b, -a};
            default -> new int[]{a, b};       // NORTH
        };
    }

    public boolean has(int cx, int cz) {
        return chunks.contains(key(cx, cz));
    }

    /** 집 스폰 (월드가 꺼져 있으면 null) */
    public Location home() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, homeX, homeY, homeZ, homeYaw, homePitch);
    }

    void setHome(Location l) {
        homeX = l.getX();
        homeY = l.getY();
        homeZ = l.getZ();
        homeYaw = l.getYaw();
        homePitch = l.getPitch();
    }

    // ---- 청크 키 ----
    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public static int cx(long key) {
        return (int) (key >> 32);
    }

    public static int cz(long key) {
        return (int) key;
    }
}
