package com.htk.core.dogam;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.logging.Logger;

/**
 * dogam.yml의 카테고리와 항목.
 * 모든 항목은 파일에 적힌 순서대로 0번부터 번호(index)를 받아요.
 * 이 번호가 리소스팩 아이콘 글자 번호예요 (tools/build_pack.py 와 같은 규칙).
 */
public final class CollectionRegistry {

    public record Entry(int index, String id, String categoryId, String name, Material material,
                        int required, List<String> description, List<String> rewards) {}

    public record Category(String id, String name, List<Entry> entries, List<String> completeRewards) {}

    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final List<Entry> all = new ArrayList<>();
    private final Map<String, Entry> byId = new HashMap<>();

    public static CollectionRegistry load(ConfigurationSection config, Logger log) {
        CollectionRegistry reg = new CollectionRegistry();
        ConfigurationSection cats = config.getConfigurationSection("categories");
        if (cats == null) {
            log.warning("dogam.yml에 categories가 없어요.");
            return reg;
        }
        // ⚠ 아이콘 번호가 어긋나지 않게, 잘못된 항목도 번호는 하나 차지해요 (건너뛰지 않음)
        int index = 0;
        for (String catId : cats.getKeys(false)) {
            ConfigurationSection c = cats.getConfigurationSection(catId);
            if (c == null) continue;
            List<Entry> entries = new ArrayList<>();
            ConfigurationSection es = c.getConfigurationSection("entries");
            if (es != null) {
                for (String entryId : es.getKeys(false)) {
                    ConfigurationSection e = es.getConfigurationSection(entryId);
                    if (e == null) continue;
                    int myIndex = index++;
                    String matName = e.getString("material");
                    Material mat = matName == null ? null : Material.matchMaterial(matName);
                    if (mat == null) {
                        log.warning("dogam.yml " + catId + "." + entryId + " : 알 수 없는 아이템 '" + matName + "' (건너뜀)");
                        continue;
                    }
                    Entry entry = new Entry(myIndex, catId + ":" + entryId, catId,
                            e.getString("name", entryId), mat,
                            Math.max(1, e.getInt("required", 1)),
                            e.getStringList("description"), e.getStringList("rewards"));
                    entries.add(entry);
                    reg.all.add(entry);
                    reg.byId.put(entry.id(), entry);
                }
            }
            reg.categories.put(catId, new Category(catId, c.getString("name", catId),
                    List.copyOf(entries), c.getStringList("complete-rewards")));
        }
        log.info("도감 항목 " + reg.all.size() + "개를 불러왔어요.");
        return reg;
    }

    /** 모든 항목 (dogam.yml 순서) */
    public List<Entry> all() { return all; }
    public Collection<Category> categories() { return categories.values(); }
    public Category category(String id) { return categories.get(id); }
    public Entry entry(String id) { return byId.get(id); }
}
