package com.htk.core.menu;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/** menu.yml 읽기 */
public record MenuConfig(int headSlot, String headName, List<String> headLore, String headCommand,
                         Map<Integer, Button> bySlot, List<Button> buttons) {

    public record Button(String id, List<Integer> slots, String name, List<String> lore,
                         String command, String teleport, String teleportTitle, String teleportSubtitle,
                         boolean comingSoon, boolean close) {}

    /** Apply the old built-in layout migration in memory, preserving custom buttons. */
    public static void migrateLayout(YamlConfiguration yml, YamlConfiguration defaults) {
        if ("skill".equals(com.htk.core.common.CommandNames.configured(yml.getString("head-command", ""))))
            yml.set("head-command", "honor");
        if (!yml.isConfigurationSection("buttons.skill") &&
                yml.getIntegerList("buttons.warp.slots").equals(List.of(12,13,14,21,22,23,30,31,32,39,40,41))) {
            yml.set("buttons.warp.slots", defaults.getIntegerList("buttons.warp.slots"));
            yml.set("buttons.skill", defaults.getConfigurationSection("buttons.skill"));
        }
    }

    /** buttons가 예전(2.0 대화창) 형식이면 null */
    public static MenuConfig load(YamlConfiguration yml) {
        ConfigurationSection sec = yml.getConfigurationSection("buttons");
        if (sec == null) return null;

        List<Button> buttons = new ArrayList<>();
        Map<Integer, Button> bySlot = new HashMap<>();
        for (String id : sec.getKeys(false)) {
            ConfigurationSection b = sec.getConfigurationSection(id);
            if (b == null) continue;
            List<Integer> slots = b.getIntegerList("slots");
            Button button = new Button(id, slots,
                    b.getString("name", id), b.getStringList("lore"),
                    com.htk.core.common.CommandNames.configured(b.getString("command", "")), b.getString("teleport", ""),
                    b.getString("teleport-title", ""), b.getString("teleport-subtitle", ""),
                    b.getBoolean("coming-soon", false), b.getBoolean("close", false));
            buttons.add(button);
            for (int s : slots) if (s >= 0 && s < 45) bySlot.put(s, button);
        }
        return new MenuConfig(yml.getInt("head-slot", 0),
                yml.getString("head-name", "%player%"), yml.getStringList("head-lore"),
                com.htk.core.common.CommandNames.configured(yml.getString("head-command", "honor")),
                Map.copyOf(bySlot), List.copyOf(buttons));
    }
}
