package com.htk.core.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** shops.yml 읽기 */
public final class ShopConfig {

    /**
     * 상점 품목.
     * sell = 플레이어가 상점에 팔 때 받는 값 (0이면 못 팖)
     * buy  = 플레이어가 상점에서 살 때 내는 값 (0이면 못 삼)
     */
    public record Item(String id, Material material, long sell, long buy, String product) {}

    /** skill = 판매가 보너스를 주는 레벨링 분야 (farming, mining ... 없으면 빈 글자) */
    public record Shop(String id, String title, String theme, String npcName, String npcProfession, List<Item> items,
                       String skill) {}

    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public static ShopConfig load(YamlConfiguration yml, Logger log) {
        ShopConfig cfg = new ShopConfig();
        ConfigurationSection root = yml.getConfigurationSection("shops");
        if (root == null) {
            log.warning("shops.yml 에 shops 가 없어요.");
            return cfg;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            List<Item> items = new ArrayList<>();
            ConfigurationSection is = s.getConfigurationSection("items");
            if (is != null) {
                for (String itemId : is.getKeys(false)) {
                    ConfigurationSection i = is.getConfigurationSection(itemId);
                    if (i == null) continue;
                    String matName = i.getString("material", itemId);
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null || !mat.isItem()) {
                        log.warning("shops.yml " + id + "." + itemId + " : 알 수 없는 아이템 '" + matName + "' (건너뜀)");
                        continue;
                    }
                    long sell = Math.max(0, i.getLong("sell", 0));
                    long buy = Math.max(0, i.getLong("buy", 0));
                    if (sell == 0 && buy == 0) continue;
                    items.add(new Item(itemId, mat, sell, buy, i.getString("product", "")));
                }
            }
            cfg.shops.put(id.toLowerCase(), new Shop(id.toLowerCase(),
                    s.getString("title", id), s.getString("theme", id),
                    s.getString("npc-name", s.getString("title", id)), s.getString("npc-profession", "NONE"),
                    List.copyOf(items), s.getString("skill", "")));
        }
        log.info("상점 " + cfg.shops.size() + "개를 불러왔어요.");
        return cfg;
    }

    public Shop shop(String id) {
        return id == null ? null : shops.get(id.toLowerCase());
    }

    public List<String> ids() {
        return new ArrayList<>(shops.keySet());
    }
}
