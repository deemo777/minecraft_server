package com.htk.core.skill;

import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All earned profession and achievement titles, with server-side ownership checks on every click. */
final class HonorGui extends Gui {
    private final SkillFeature feature;
    private final Map<Integer, String> commands = new HashMap<>();
    private final int page;
    private final int pages;
    private record Entry(String name, String detail, String command, boolean selected) {}

    HonorGui(SkillFeature feature, Player player, int requestedPage) {
        this.feature = feature;
        var id = player.getUniqueId();
        var achievements = feature.achievements();
        boolean achievementSelected = achievements != null && !achievements.prefix(id).equals(net.kyori.adventure.text.Component.empty());
        List<Entry> entries = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            for (var title : feature.titlesOf(skill)) {
                if (!feature.hasTitle(id, title.id())) continue;
                entries.add(new Entry(title.text(), skill.korean + " Lv." + title.level(), "honor " + title.id(),
                        !achievementSelected && title.id().equals(feature.equippedTitle(id))));
            }
        }
        if (achievements != null) {
            for (var title : achievements.ownedTitles(id)) {
                entries.add(new Entry(title.name(), "히든 업적", "atitle equip " + title.id(), title.selected()));
            }
        }
        pages = Math.max(1, (entries.size() + 44) / 45);
        page = Math.clamp(requestedPage, 0, pages - 1);
        create(6, Text.mm("<dark_gray>칭호 변환 · " + (page + 1) + "/" + pages));
        for (int slot = 0; slot < 45 && page * 45 + slot < entries.size(); slot++) {
            var entry = entries.get(page * 45 + slot);
            inventory.setItem(slot, Items.of(entry.selected() ? Material.NAME_TAG : Material.PAPER, 1,
                    (entry.selected() ? "<green>▶ " : "<white>") + entry.name(),
                    List.of("<gray>" + entry.detail(), entry.selected() ? "<green>현재 장착 중" : "<yellow>클릭해서 장착"), null));
            commands.put(slot, entry.command());
        }
        if (entries.isEmpty()) inventory.setItem(22, Items.of(Material.BOOK, 1, "<gray>보유한 칭호가 없어요",
                List.of("<gray>직업 레벨을 올리거나 업적을 달성해 보세요."), null));
        button(45, Material.ARROW, "<gray>메뉴로");
        if (page > 0) button(48, Material.ARROW, "<white>이전 페이지");
        button(49, Material.BARRIER, "<yellow>칭호 해제");
        if (page + 1 < pages) button(50, Material.ARROW, "<white>다음 페이지");
        button(53, Material.EXPERIENCE_BOTTLE, "<green>직업 경험치");
    }

    private void button(int slot, Material material, String name) {
        inventory.setItem(slot, Items.of(material, 1, name, List.of(), null));
    }

    @Override public void onClick(Player player, int slot, InventoryClickEvent event) {
        String command = commands.get(slot);
        if (slot == 45) command = "menu";
        if (slot == 49) command = "honor off";
        if (slot == 53) command = "skill";
        final String action = command;
        Bukkit.getScheduler().runTask(feature.plugin(), () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) return;
            if (slot == 48 && page > 0) new HonorGui(feature, player, page - 1).open(player);
            else if (slot == 50 && page + 1 < pages) new HonorGui(feature, player, page + 1).open(player);
            else if (action != null) {
                player.performCommand(action);
                if (slot != 45 && slot != 53) new HonorGui(feature, player, page).open(player);
            }
        });
    }
}
