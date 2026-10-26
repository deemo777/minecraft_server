package com.htk.core.common;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * %이름% 치환 문구. 각 기능이 자기 값을 등록하고, 메뉴 같은 곳은 가져다 써요.
 * (메뉴가 도감 코드를 직접 몰라도 되게)
 */
public final class Placeholders {

    private static final Map<String, Function<Player, String>> VALUES = new ConcurrentHashMap<>();

    private Placeholders() {}

    public static void register(String key, Function<Player, String> value) {
        VALUES.put(key, value);
    }

    public static void clear() {
        VALUES.clear();
    }

    public static String apply(Player player, String text) {
        if (text == null) return "";
        String out = text.replace("%player%", player.getName());
        for (Map.Entry<String, Function<Player, String>> e : VALUES.entrySet()) {
            String token = "%" + e.getKey() + "%";
            if (out.contains(token)) out = out.replace(token, e.getValue().apply(player));
        }
        return out;
    }

    public static List<String> apply(Player player, List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(apply(player, line));
        return out;
    }
}
