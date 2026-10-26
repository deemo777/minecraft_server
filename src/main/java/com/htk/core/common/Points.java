package com.htk.core.common;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 이름 붙인 위치 (메뉴의 "집으로", "마을로" 같은 이동 버튼용)
 * plugins/ServerCore/points.yml 에 저장. 게임에서 /servercore setpoint <이름> 으로 지금 서 있는 곳을 저장해요.
 */
public final class Points {

    private final File file;
    private final Logger log;
    private YamlConfiguration yml;

    public Points(File dataFolder, Logger log) {
        this.file = new File(dataFolder, "points.yml");
        this.log = log;
        reload();
    }

    public void reload() {
        yml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public void set(String name, Location loc) {
        String p = "points." + name.toLowerCase();
        yml.set(p + ".world", loc.getWorld().getName());
        yml.set(p + ".x", loc.getX());
        yml.set(p + ".y", loc.getY());
        yml.set(p + ".z", loc.getZ());
        yml.set(p + ".yaw", (double) loc.getYaw());
        yml.set(p + ".pitch", (double) loc.getPitch());
        save();
    }

    public boolean remove(String name) {
        String p = "points." + name.toLowerCase();
        if (yml.getConfigurationSection(p) == null) return false;
        yml.set(p, null);
        save();
        return true;
    }

    public List<String> names() {
        ConfigurationSection sec = yml.getConfigurationSection("points");
        return sec == null ? List.of() : new ArrayList<>(sec.getKeys(false));
    }

    /** 저장된 적 없거나, 그 월드가 지금 안 켜져 있으면 null */
    public Location get(String name) {
        ConfigurationSection s = yml.getConfigurationSection("points." + name.toLowerCase());
        if (s == null) return null;
        World world = Bukkit.getWorld(s.getString("world", ""));
        if (world == null) return null;
        return new Location(world, s.getDouble("x", 0), s.getDouble("y", 0), s.getDouble("z", 0),
                (float) s.getDouble("yaw", 0), (float) s.getDouble("pitch", 0));
    }

    /** 저장은 돼 있는데 월드가 없는 경우 안내용 */
    public String worldName(String name) {
        return yml.getString("points." + name.toLowerCase() + ".world");
    }

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            log.warning("points.yml 저장 실패: " + e.getMessage());
        }
    }
}
