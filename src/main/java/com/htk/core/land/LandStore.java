package com.htk.core.land;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * plugins/ServerCore/lands.yml
 *   lands:
 *     <주인 uuid>:
 *       name: 주인 이름
 *       world: world
 *       chunks: ["3,-2", "4,-2"]     (청크 x,z — 처음 정한 청크가 맨 앞)
 *       core: {x, y, z, facing}       (집터 블록, facing = 놓을 때 바라본 방향 = 지도 위쪽)
 *       home: {x, y, z, yaw, pitch}
 *       debt: 1000
 *       members: [uuid, ...]
 */
final class LandStore {

    private final File file;
    private final Logger log;
    private final Map<UUID, Land> byOwner = new HashMap<>();
    /** 월드 → (청크 키 → 땅) */
    private final Map<String, Map<Long, Land>> index = new HashMap<>();

    LandStore(File dataFolder, Logger log) {
        this.file = new File(dataFolder, "lands.yml");
        this.log = log;
        load();
    }

    void load() {
        byOwner.clear();
        index.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("lands");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Land land = new Land(UUID.fromString(id), s.getString("name", "?"), s.getString("world", "world"));
                for (String c : s.getStringList("chunks")) {
                    String[] p = c.split(",");
                    land.chunks.add(Land.key(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
                }
                if (land.chunks.isEmpty()) continue;
                for (String c : s.getStringList("plot")) {
                    String[] p = c.split(",");
                    land.plot.add(Land.key(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
                }
                if (!land.plot.isEmpty() && (!LandLayout.isSquare(land.plot) || !land.plot.containsAll(land.chunks))) {
                    log.warning("Invalid fixed plot for " + id + "; keeping all existing chunks as legacy land.");
                    land.plot.clear();
                }
                land.debt = s.getLong("debt", 0);
                if (s.isConfigurationSection("core")) {
                    land.hasCore = true;
                    land.coreX = s.getInt("core.x");
                    land.coreY = s.getInt("core.y");
                    land.coreZ = s.getInt("core.z");
                    try {
                        land.facing = org.bukkit.block.BlockFace.valueOf(s.getString("core.facing", "NORTH").toUpperCase());
                    } catch (IllegalArgumentException ignored) { }
                }
                land.homeX = s.getDouble("home.x");
                land.homeY = s.getDouble("home.y");
                land.homeZ = s.getDouble("home.z");
                land.homeYaw = (float) s.getDouble("home.yaw");
                land.homePitch = (float) s.getDouble("home.pitch");
                for (String m : s.getStringList("members")) land.members.add(UUID.fromString(m));
                put(land);
            } catch (Exception e) {
                log.warning("lands.yml 의 " + id + " 를 읽지 못했어요: " + e.getMessage());
            }
        }
    }

    void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Land land : byOwner.values()) {
            String p = "lands." + land.owner + ".";
            yml.set(p + "name", land.ownerName);
            yml.set(p + "world", land.world);
            List<String> cs = new ArrayList<>();
            for (long k : land.chunks) cs.add(Land.cx(k) + "," + Land.cz(k));
            yml.set(p + "chunks", cs);
            List<String> footprint = new ArrayList<>();
            for (long k : land.plot) footprint.add(Land.cx(k) + "," + Land.cz(k));
            if (!footprint.isEmpty()) yml.set(p + "plot", footprint);
            if (land.hasCore) {
                yml.set(p + "core.x", land.coreX);
                yml.set(p + "core.y", land.coreY);
                yml.set(p + "core.z", land.coreZ);
                yml.set(p + "core.facing", land.facing.name());
            }
            yml.set(p + "home.x", land.homeX);
            yml.set(p + "home.y", land.homeY);
            yml.set(p + "home.z", land.homeZ);
            yml.set(p + "home.yaw", (double) land.homeYaw);
            yml.set(p + "home.pitch", (double) land.homePitch);
            yml.set(p + "debt", land.debt);
            List<String> ms = new ArrayList<>();
            for (UUID m : land.members) ms.add(m.toString());
            yml.set(p + "members", ms);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            log.warning("lands.yml 저장 실패: " + e.getMessage());
        }
    }

    private void put(Land land) {
        byOwner.put(land.owner, land);
        Map<Long, Land> m = index.computeIfAbsent(land.world, w -> new HashMap<>());
        for (long k : land.reservedChunks()) m.put(k, land);
    }

    Land of(UUID owner) {
        return byOwner.get(owner);
    }

    Collection<Land> all() {
        return byOwner.values();
    }

    Land at(String world, int cx, int cz) {
        Map<Long, Land> m = index.get(world);
        return m == null ? null : m.get(Land.key(cx, cz));
    }

    Land at(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return at(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    Land at(Block b) {
        return at(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4);
    }

    Land create(UUID owner, String name, String world, int cx, int cz, Location home, Collection<Long> plot) {
        Land land = new Land(owner, name, world);
        land.chunks.add(Land.key(cx, cz));
        land.plot.addAll(plot);
        land.setHome(home);
        put(land);
        save();
        return land;
    }

    void addChunk(Land land, int cx, int cz) {
        addChunk(land, cx, cz, 0);
    }

    void addChunk(Land land, int cx, int cz, long addedDebt) {
        long k = Land.key(cx, cz);
        if (!land.chunks.add(k)) return;
        land.debt += Math.max(0, addedDebt);
        index.computeIfAbsent(land.world, w -> new HashMap<>()).put(k, land);
        save();
    }

    void removeChunk(Land land, int cx, int cz) {
        long k = Land.key(cx, cz);
        if (!land.chunks.remove(k)) return;
        Map<Long, Land> m = index.get(land.world);
        if (m != null && !land.plot.contains(k)) m.remove(k);
        save();
    }

    void remove(Land land) {
        byOwner.remove(land.owner);
        Map<Long, Land> m = index.get(land.world);
        if (m != null) for (long k : land.reservedChunks()) m.remove(k);
        save();
    }

    void reserve(Land land, Collection<Long> plot) {
        land.plot.addAll(plot);
        Map<Long, Land> m = index.computeIfAbsent(land.world, w -> new HashMap<>());
        for (long k : land.plot) m.put(k, land);
    }

    boolean backupBeforeMigration() {
        if (!file.isFile()) return true;
        File backup = new File(file.getParentFile(), "lands-before-fixed-plot-" + System.currentTimeMillis() + ".yml");
        try {
            java.nio.file.Files.copy(file.toPath(), backup.toPath());
            log.info("집 부지 전환 전 데이터 백업: " + backup.getName());
            return true;
        } catch (IOException e) {
            log.warning("집 부지 백업 실패로 자동 전환을 건너뜁니다: " + e.getMessage());
            return false;
        }
    }
}
