package com.htk.core.skill;

import com.htk.core.ServerCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 플레이어별 레벨·경험치: plugins/ServerCore/skill-data/<UUID>.yml */
public final class SkillStore {

    /** 한 분야의 레벨과 "지금 레벨 안에서 모은" 경험치 */
    public static final class Progress {
        public int level = 1;
        public long xp = 0;
    }

    public static final class Data {
        final Map<Skill, Progress> skills = new EnumMap<>(Skill.class);
        /** 달고 있는 칭호 ("분야:레벨", 없으면 빈 글자) */
        volatile String title = "";
        volatile boolean dirty;

        public Progress get(Skill s) {
            return skills.computeIfAbsent(s, k -> new Progress());
        }
    }

    private final ServerCore plugin;
    private final File folder;
    private final Map<UUID, Data> cache = new ConcurrentHashMap<>();

    public SkillStore(ServerCore plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "skill-data");
        if (!folder.exists() && !folder.mkdirs()) plugin.getLogger().warning("skill-data 폴더를 만들지 못했어요.");
    }

    public Data get(UUID id) {
        return cache.computeIfAbsent(id, this::read);
    }

    private Data read(UUID id) {
        Data d = new Data();
        File f = new File(folder, id + ".yml");
        if (f.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            for (Skill s : Skill.values()) {
                Progress p = d.get(s);
                p.level = Math.max(1, yml.getInt(s.key() + ".level", 1));
                p.xp = Math.max(0, yml.getLong(s.key() + ".xp", 0));
            }
            d.title = yml.getString("title", "");
        }
        return d;
    }

    public void markDirty(UUID id) {
        get(id).dirty = true;
    }

    public void save(UUID id) {
        Data d = cache.get(id);
        if (d == null || !d.dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Skill s : Skill.values()) {
            Progress p = d.get(s);
            yml.set(s.key() + ".level", p.level);
            yml.set(s.key() + ".xp", p.xp);
        }
        yml.set("title", d.title);
        try {
            yml.save(new File(folder, id + ".yml"));
            d.dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning(id + " 레벨 저장 실패: " + e.getMessage());
        }
    }

    public void saveAll() {
        cache.keySet().forEach(this::save);
    }

    public void unload(UUID id) {
        save(id);
        cache.remove(id);
    }
}
