package com.htk.core.land;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 청크 경계를 빛나는 선으로 보여주기 (그 사람에게만 보여요)
 *   - 땅 위 테두리를 따라 얇은 막대(블록 디스플레이), 바깥 모서리마다 높은 기둥
 *   - 빛남(glow) 효과라 벽 너머·멀리서도 윤곽이 보여요
 *   - 저장되지 않는 임시 엔티티라 서버를 껐다 켜도 남지 않아요
 */
final class BorderDisplay {

    /** 선 색: 초록 = 내 땅, 노랑 = 넓힐 곳, 하늘 = 집 구역 */
    enum Style {
        MINE(Color.fromRGB(0x7ACB52), Material.LIME_CONCRETE),
        TARGET(Color.fromRGB(0xFFD34D), Material.YELLOW_CONCRETE),
        ZONE(Color.fromRGB(0x6AA8E0), Material.LIGHT_BLUE_CONCRETE);

        final Color glow;
        final Material block;

        Style(Color glow, Material block) {
            this.glow = glow;
            this.block = block;
        }
    }

    record Layer(Set<Long> chunks, Style style) {}

    private static final float THICK = 0.12f;
    private static final float PILLAR = 0.18f;
    private static final float PILLAR_HEIGHT = 12f;
    /** 이보다 멀면 그리지 않아요 (안 보이기도 하고, 먼 청크를 불러오지 않게) */
    private static final double MAX_DISTANCE = 160;

    private final Plugin plugin;
    private final Map<UUID, List<Entity>> shown = new HashMap<>();
    private final Map<UUID, BukkitTask> timers = new HashMap<>();

    BorderDisplay(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 이전 것을 지우고 새로 보여줘요. 하나라도 그렸으면 true (다른 월드거나 너무 멀면 false) */
    boolean show(Player p, World world, List<Layer> layers, int seconds) {
        clear(p);
        if (world == null || !p.getWorld().equals(world)) return false;
        List<Entity> list = new ArrayList<>();
        Location eye = p.getLocation();
        for (Layer layer : layers) {
            BlockData data = layer.style().block.createBlockData();
            Set<String> pillars = new HashSet<>();
            for (long k : layer.chunks()) {
                int cx = Land.cx(k), cz = Land.cz(k);
                int x0 = cx << 4, z0 = cz << 4;
                if (eye.distance(new Location(world, x0 + 8, eye.getY(), z0 + 8)) > MAX_DISTANCE) continue;
                boolean n = !layer.chunks().contains(Land.key(cx, cz - 1));
                boolean s = !layer.chunks().contains(Land.key(cx, cz + 1));
                boolean w = !layer.chunks().contains(Land.key(cx - 1, cz));
                boolean e = !layer.chunks().contains(Land.key(cx + 1, cz));
                for (int i = 0; i < 16; i++) {
                    if (n) list.add(bar(p, world, data, layer.style(), x0 + i, z0, x0 + i, z0 - THICK / 2, 1, THICK));
                    if (s) list.add(bar(p, world, data, layer.style(), x0 + i, z0 + 15, x0 + i, z0 + 16 - THICK / 2, 1, THICK));
                    if (w) list.add(bar(p, world, data, layer.style(), x0, z0 + i, x0 - THICK / 2, z0 + i, THICK, 1));
                    if (e) list.add(bar(p, world, data, layer.style(), x0 + 15, z0 + i, x0 + 16 - THICK / 2, z0 + i, THICK, 1));
                }
                // 바깥 모서리 기둥 (네 귀퉁이 중 땅 경계에 걸린 곳)
                int[][] corners = {{0, 0, -1, -1}, {16, 0, 0, -1}, {0, 16, -1, 0}, {16, 16, 0, 0}};
                for (int[] c : corners) {
                    int px = x0 + c[0], pz = z0 + c[1];
                    int ox = cx + c[2], oz = cz + c[3];
                    boolean inner = layer.chunks().contains(Land.key(ox, oz)) && layer.chunks().contains(Land.key(ox + 1, oz))
                            && layer.chunks().contains(Land.key(ox, oz + 1)) && layer.chunks().contains(Land.key(ox + 1, oz + 1));
                    if (inner || !pillars.add(px + "," + pz)) continue;
                    int y = surface(world, Math.min(px, x0 + 15), Math.min(pz, z0 + 15));
                    list.add(spawn(p, world, data, layer.style(),
                            new Location(world, px - PILLAR / 2, y, pz - PILLAR / 2), PILLAR, PILLAR_HEIGHT, PILLAR));
                }
            }
        }
        if (list.isEmpty()) return false;
        shown.put(p.getUniqueId(), list);
        timers.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> clear(p), seconds * 20L));
        return true;
    }

    void clear(Player p) {
        BukkitTask t = timers.remove(p.getUniqueId());
        if (t != null) t.cancel();
        List<Entity> list = shown.remove(p.getUniqueId());
        if (list != null) for (Entity e : list) e.remove();
    }

    void clearAll() {
        for (BukkitTask t : timers.values()) t.cancel();
        timers.clear();
        for (List<Entity> list : shown.values()) for (Entity e : list) e.remove();
        shown.clear();
    }

    /** 땅 위 막대 하나: (sx, sz) 칸의 지면 높이에, (px, pz) 위치부터 길이 lx × lz */
    private Entity bar(Player p, World w, BlockData data, Style style, int sx, int sz, double px, double pz, float lx, float lz) {
        int y = surface(w, sx, sz);
        return spawn(p, w, data, style, new Location(w, px, y + 0.02, pz), lx, THICK, lz);
    }

    /** 그 칸에서 밟을 수 있는 높이 (가장 높은 블록 바로 위) */
    private static int surface(World w, int x, int z) {
        return w.getHighestBlockYAt(x, z) + 1;
    }

    private Entity spawn(Player p, World w, BlockData data, Style style, Location at, float sx, float sy, float sz) {
        BlockDisplay d = w.spawn(at, BlockDisplay.class, e -> {
            e.setBlock(data);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(sx, sy, sz), new Quaternionf()));
            e.setGlowing(true);
            e.setGlowColorOverride(style.glow);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(4f);
            e.setPersistent(false);
            e.setVisibleByDefault(false);
        });
        p.showEntity(plugin, d);
        return d;
    }
}
