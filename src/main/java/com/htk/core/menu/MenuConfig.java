package com.htk.core.menu;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** menu.yml 읽기 */
public record MenuConfig(String title, int columns, int buttonWidth, List<Button> buttons, String closeLabel) {

    public record Button(String label, List<String> tooltip, String command, boolean comingSoon) {}

    public static MenuConfig load(YamlConfiguration yml) {
        List<Button> buttons = new ArrayList<>();
        for (Map<?, ?> map : yml.getMapList("buttons")) {
            Object tip = map.get("tooltip");
            List<String> tooltip = new ArrayList<>();
            if (tip instanceof List<?> l) for (Object o : l) tooltip.add(String.valueOf(o));
            else if (tip != null) tooltip.add(String.valueOf(tip));
            buttons.add(new Button(
                    String.valueOf(map.containsKey("label") ? map.get("label") : "버튼"),
                    tooltip,
                    map.containsKey("command") ? String.valueOf(map.get("command")) : "",
                    Boolean.TRUE.equals(map.get("coming-soon"))));
        }
        return new MenuConfig(
                yml.getString("title", "메뉴"),
                Math.max(1, yml.getInt("columns", 2)),
                Math.max(1, Math.min(1024, yml.getInt("button-width", 120))),
                List.copyOf(buttons),
                yml.getString("close-label", "닫기"));
    }
}
