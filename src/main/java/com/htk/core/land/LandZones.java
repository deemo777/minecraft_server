package com.htk.core.land;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 집 구역: 마을(village)에서 집터 설치·넓히기를 할 수 있는 청크들
 * plugins/ServerCore/land-zones.yml
 *   zone: ["3,-2", "4,-2", ...]
 * 구역이 하나도 없으면 마을 어디든 지을 수 있어요 (처음 세팅 전).
 * 관리자: /land zone pos1 · pos2 · add · remove · show · info
 */
final class LandZones {

    private final File file;
    private final Logger log;
    private final Set<Long> chunks = new HashSet<>();

    LandZones(File dataFolder, Logger log) {
        this.file = new File(dataFolder, "land-zones.yml");
        this.log = log;
        load();
    }

    void load() {
        chunks.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String c : yml.getStringList("zone")) {
            try {
                String[] p = c.split(",");
                chunks.add(Land.key(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
            } catch (Exception e) {
                log.warning("land-zones.yml 의 '" + c + "' 를 읽지 못했어요.");
            }
        }
    }

    void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> cs = new ArrayList<>();
        for (long k : chunks) cs.add(Land.cx(k) + "," + Land.cz(k));
        cs.sort(null);
        yml.set("zone", cs);
        try {
            yml.save(file);
        } catch (IOException e) {
            log.warning("land-zones.yml 저장 실패: " + e.getMessage());
        }
    }

    /** 구역이 아직 하나도 없으면 어디든 true */
    boolean allows(int cx, int cz) {
        return chunks.isEmpty() || chunks.contains(Land.key(cx, cz));
    }

    boolean contains(int cx, int cz) {
        return chunks.contains(Land.key(cx, cz));
    }

    boolean isEmpty() {
        return chunks.isEmpty();
    }

    int size() {
        return chunks.size();
    }

    Set<Long> all() {
        return chunks;
    }

    /** 두 청크를 모서리로 하는 네모 안의 청크를 모두 넣기/빼기. 바뀐 청크 수 */
    int setRect(int ax, int az, int bx, int bz, boolean add) {
        int changed = 0;
        for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
            for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) {
                long k = Land.key(x, z);
                if (add ? chunks.add(k) : chunks.remove(k)) changed++;
            }
        }
        if (changed > 0) save();
        return changed;
    }
}
