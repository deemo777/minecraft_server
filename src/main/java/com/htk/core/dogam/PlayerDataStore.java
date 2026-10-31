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
        private final Map<String,Integer> deposits=new HashMap<>();
        private volatile boolean dirty;

        /** 처음 발견했으면 true */
        public boolean add(String entryId) {
            boolean added = found.add(entryId);
            if (added) { deposits.remove(entryId);dirty = true; }
            return added;
        }
        public boolean has(String entryId) { return found.contains(entryId); }
        public Set<String> found() { return Collections.unmodifiableSet(found); }
        public int deposited(String entryId){return deposits.getOrDefault(entryId,0);}
        public void setDeposited(String entryId,int amount) {
            found.remove(entryId);
            if(amount<=0)deposits.remove(entryId);else deposits.put(entryId,amount);
            dirty=true;
        }
        public void clear() { found.clear();deposits.clear();dirty = true; }
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
            data=decode(YamlConfiguration.loadConfiguration(file));
        }
        return data;
    }

    static PlayerData decode(YamlConfiguration yml) {
        PlayerData data=new PlayerData();
        for(String id:yml.getStringList("found")) {
            int colon=id.indexOf(':');data.found.add(colon>=0?id.substring(colon+1):id);
        }
        var section=yml.getConfigurationSection("deposits");
        if(section!=null)for(String id:section.getKeys(false)) {
            int amount=section.getInt(id);
            if(amount>0 && !data.has(id))data.deposits.put(id,amount);
        }
        return data;
    }
    static YamlConfiguration encode(PlayerData data) {
        var yml=new YamlConfiguration();var list=new ArrayList<>(data.found);Collections.sort(list);
        yml.set("found",list);
        new TreeMap<>(data.deposits).forEach((id,amount)->yml.set("deposits."+id,amount));
        return yml;
    }
    public boolean saveNow(UUID uuid){return save(uuid,get(uuid));}
    private boolean save(UUID uuid, PlayerData data) {
        try {
            write(new File(folder,uuid+".yml"),data);
            data.dirty=false;return true;
        } catch(IOException e) {
            plugin.getLogger().log(Level.WARNING,uuid+" 도감 저장 실패",e);return false;
        }
    }
    static void write(File file,PlayerData data) throws IOException {
        var target=file.toPath();var temp=target.resolveSibling(target.getFileName()+".tmp");
        encode(data).save(temp.toFile());
        try {java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        catch(java.nio.file.AtomicMoveNotSupportedException ex){java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
    }
}
