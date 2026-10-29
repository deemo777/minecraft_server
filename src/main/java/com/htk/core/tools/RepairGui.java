package com.htk.core.tools;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import java.util.List;
import java.util.UUID;

/** One real input in the center; every other top slot is controlled by RepairFeature. */
final class RepairGui implements InventoryHolder {
    static final int TOOL = 13, REPAIR = 22;
    final UUID owner;
    final Inventory inventory;
    final RepairFeature feature;
    boolean returned;
    long nextAttempt;
    RepairGui(RepairFeature feature, Player player) {
        this.feature = feature; owner = player.getUniqueId();
        Component title = feature.plugin().resourcePackEnabled()
                ? Component.text(Glyphs.space(-8) + "\uE07C" + Glyphs.space(-1) + "\uE07D"
                    + Glyphs.space(-177) + "\uE07E" + Glyphs.space(-1) + "\uE07F")
                    .font(Key.key("servercore:forge")).color(NamedTextColor.WHITE)
                : Text.mm("<dark_gray>수리 공방 · 장비 수리");
        inventory = Bukkit.createInventory(this, 27, title);
        refresh();
    }
    @Override public Inventory getInventory() { return inventory; }
    void refresh() {
        ItemStack item = inventory.getItem(TOOL);
        boolean pack = feature.plugin().resourcePackEnabled();
        String name = "<gray>가운데에 마을 도구를 넣어 주세요";
        List<String> lore = List.of();
        boolean enabled = false;
        if (feature.valid(item)) {
            Damageable meta = (Damageable) item.getItemMeta();
            int maximum = feature.maximum(item);
            if (meta.getDamage() <= 0 || meta.isUnbreakable()) name = "<gray>수리할 필요가 없어요";
            else {
                long cost = feature.cost(item);
                enabled = feature.canAfford(owner, cost);
                name = (enabled ? "<#ffc773>" : "<gray>") + "<bold>수리하기</bold>";
                var lines = new java.util.ArrayList<String>();
                if (!enabled) lines.add("<red>골드 부족");
                lines.add("<gray>내구도: <white>" + Math.max(0, maximum - meta.getDamage()) + "/" + maximum + " → " + maximum + "/" + maximum);
                lines.add("<gray>골드 소모: <gold>" + Economy.format(cost));
                lines.add("<dark_gray>기본 " + Economy.format(feature.baseCost(item)) + " + 내구도 "
                        + Economy.format(Math.min(meta.getDamage(), maximum)));
                lore = List.copyOf(lines);
            }
        }
        var button = Items.of(pack ? Material.PAPER : enabled ? Material.ANVIL : Material.GRAY_DYE,
                1, name, lore, pack ? Glyphs.model("forge_button") : null);
        inventory.setItem(REPAIR, button);
        // The painted button extends into its neighbors; all three cells expose the same tooltip.
        for (int slot : new int[]{REPAIR - 1, REPAIR + 1}) inventory.setItem(slot,
                Items.of(pack ? Material.PAPER : Material.BLACK_STAINED_GLASS_PANE, 1, name, lore,
                    pack ? Glyphs.model("forge_button") : null));
    }
    ItemStack drain() {
        if (returned) return null;
        returned = true;
        ItemStack item = inventory.getItem(TOOL);
        inventory.setItem(TOOL, null);
        return item;
    }
}
