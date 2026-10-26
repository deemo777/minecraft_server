package com.htk.core.dogam;

import com.htk.core.ServerCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** 플레이어별 발견 기록을 plugins/ServerCore/dogam-data/<UUID>.yml 에 저장해요 */
public final class PlayerDataStore {

    public static final class PlayerData {
        private final Set<String> found = ConcurrentHashMap.newKeySet();
        private volatile boolean dirty;

        /** 처음 발견했으면 true */
        public boolean add(String entryId) {
            boolean added = found.add(entryId);
            if (added) dirty = true;
            return added;
        }
        public boolean has(String entryId) { return found.contains(entryId); }
        public Set<String> found() { return Collections.unmodifiableSet(found); }
        public void clear() { found.clear(); dirty = true; }
    }

    private final ServerCore plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataStore(ServerCore plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "dogam-data");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("data 폴더를 만들지 못했어요.");
        }
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::readFile);
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) save(uuid, data);
    }

    public void saveAll() {
        cache.forEach(this::save);
    }

    public void saveAllDirty() {
        cache.forEach((uuid, data) -> { if (data.dirty) save(uuid, data); });
    }

    private PlayerData readFile(UUID uuid) {
        PlayerData data = new PlayerData();
        File file = new File(folder, uuid + ".yml");
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String id : yml.getStringList("found")) {
                // 예전 형식 "묶음:항목" → "항목" (묶음을 옮겨도 기록이 유지되게 바뀜)
                int colon = id.indexOf(':');
                data.found.add(colon >= 0 ? id.substring(colon + 1) : id);
            }
        }
        return data;
    }

    private void save(UUID uuid, PlayerData data) {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> list = new ArrayList<>(data.found);
        Collections.sort(list);
        yml.set("found", list);
        try {
            yml.save(new File(folder, uuid + ".yml"));
            data.dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, uuid + " 도감 저장 실패", e);
        }
    }
}
