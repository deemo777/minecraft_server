package com.htk.core.skill;

import com.htk.core.common.Gui;
import com.htk.core.common.Glyphs;
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
    private static final int PAGE_SIZE = 28; // Four rows of seven, matching draw_honor().
    private final SkillFeature feature;
    private final Map<Integer, String> commands = new HashMap<>();
    private final int page;
    private final int pages;
    private record Entry(String name, String detail, String command, boolean selected, Material icon) {}

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
                        !achievementSelected && title.id().equals(feature.equippedTitle(id)), switch (skill) {
                            case FARMING -> Material.WHEAT;
                            case MINING -> Material.IRON_PICKAXE;
                            case WOODCUTTING -> Material.IRON_AXE;
                            case FISHING -> Material.FISHING_ROD;
                            case COMBAT -> Material.IRON_SWORD;
                        }));
            }
        }
        if (achievements != null) {
            for (var title : achievements.ownedTitles(id)) {
                entries.add(new Entry(title.name(), "히든 업적", "atitle equip " + title.id(), title.selected(), Material.NETHER_STAR));
            }
        }
        pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(requestedPage, 0, pages - 1);
        boolean pack = feature.plugin().resourcePackEnabled();
        create(6, pack ? new Glyphs.Canvas().background(Glyphs.HONOR_BG).build(
                Text.mm("<#6b4226>보유 " + entries.size() + "개 · " + (page + 1) + "/" + pages))
                : Text.mm("<dark_gray>칭호 변환 · " + (page + 1) + "/" + pages));
        if (!pack) {
            for (int slot = 0; slot < 54; slot++) inventory.setItem(slot,
                    Items.hideTooltip(Items.of(Material.BROWN_STAINED_GLASS_PANE, 1, " ", List.of(), null)));
        }
        String current = entries.stream().filter(Entry::selected).map(Entry::name).findFirst().orElse("<gray>없음");
        inventory.setItem(4, Items.of(Material.NAME_TAG, 1, "<#f7d36b><bold>현재 칭호</bold>",
                List.of("<white>" + current, "", "<gray>보유 칭호 <white>" + entries.size() + "개",
                        "<gray>아래 칭호를 눌러 무료로 바꿔 보세요."), null));
        for (int index = 0; index < PAGE_SIZE; index++) {
            int slot = 10 + (index / 7) * 9 + index % 7;
            inventory.setItem(slot, null);
            if (page * PAGE_SIZE + index >= entries.size()) continue;
            var entry = entries.get(page * PAGE_SIZE + index);
            var item = Items.of(entry.selected() ? Material.NAME_TAG : entry.icon(), 1,
                    (entry.selected() ? "<green>✔ " : "<white>") + entry.name(),
                    List.of("<dark_gray>" + entry.detail(), "",
                            entry.selected() ? "<green>현재 장착 중인 칭호예요." : "<yellow>클릭해서 이 칭호로 변경",
                            "<gray>변경 비용 <white>무료"), null);
            if (entry.selected()) Items.glint(item);
            inventory.setItem(slot, item);
            commands.put(slot, entry.command());
        }
        if (entries.isEmpty()) inventory.setItem(22, Items.of(Material.BOOK, 1, "<gray>보유한 칭호가 없어요",
                List.of("<gray>직업 레벨을 올리거나 업적을 달성해 보세요."), null));
        button(45, Material.ARROW, "<#b5df8b>메뉴로", "마을 메뉴로 돌아갑니다.", pack ? "arrow_left" : null);
        button(48, Material.ARROW, page > 0 ? "<white>이전 페이지" : "<gray>첫 페이지예요",
                (page + 1) + " / " + pages + " 페이지", pack ? (page > 0 ? "arrow_left" : "arrow_left_off") : null);
        button(49, Material.BARRIER, "<#efb1a8>칭호 해제", "장착한 칭호를 해제합니다.", null);
        button(50, Material.ARROW, page + 1 < pages ? "<white>다음 페이지" : "<gray>마지막 페이지예요",
                (page + 1) + " / " + pages + " 페이지", pack ? (page + 1 < pages ? "arrow_right" : "arrow_right_off") : null);
        button(53, Material.EXPERIENCE_BOTTLE, "<#b5df8b>직업 경험치", "직업 레벨과 다음 칭호를 확인합니다.", null);
    }

    private void button(int slot, Material material, String name, String detail, String model) {
        inventory.setItem(slot, Items.of(material, 1, name, List.of("<gray>" + detail), model == null ? null : Glyphs.model(model)));
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
