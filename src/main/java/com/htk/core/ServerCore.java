package com.htk.core;

import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import com.htk.core.dogam.DogamFeature;
import com.htk.core.menu.MenuFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ServerCore extends JavaPlugin {

    private final List<Feature> features = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("menu.yml");
        saveIfMissing("dogam.yml");

        if (getConfig().getBoolean("features.dogam", true)) features.add(new DogamFeature(this));
        if (getConfig().getBoolean("features.menu", true)) features.add(new MenuFeature(this));
        for (Feature f : features) f.enable();

        PluginCommand admin = getCommand("servercore");
        if (admin != null) admin.setExecutor(new CoreCommand(this));

        getLogger().info("켜진 기능 " + features.size() + "개");
    }

    @Override
    public void onDisable() {
        for (Feature f : features) f.disable();
    }

    public void reloadAll() {
        reloadConfig();
        for (Feature f : features) f.reload();
    }

    /** 꺼진 기능의 명령어를 입력했을 때 (켜진 기능은 각자 명령어를 가져가요) */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Text.mm(msg("feature-disabled")));
        return true;
    }

    // ---------- 도우미 ----------

    public boolean resourcePackEnabled() {
        return getConfig().getBoolean("resourcepack.enabled", true);
    }

    public String msg(String key) {
        return getConfig().getString("messages." + key, key);
    }

    public YamlConfiguration loadYaml(String fileName) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), fileName));
    }

    private void saveIfMissing(String fileName) {
        if (!new File(getDataFolder(), fileName).exists()) saveResource(fileName, false);
    }
}
