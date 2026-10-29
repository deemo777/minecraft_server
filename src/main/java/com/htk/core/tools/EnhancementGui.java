package com.htk.core.tools;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

/** Only two real item slots. Lifecycle and transfers are owned by BlacksmithFeature. */
final class EnhancementGui implements InventoryHolder {
    static final int TOOL = 11, MATERIAL = 15, UPGRADE = 22;
    final BlacksmithFeature feature;
    final UUID owner;
    final Inventory inventory;
    boolean returned;
    long nextAttempt;
    private boolean upgradeEnabled;

    EnhancementGui(BlacksmithFeature feature, Player player) {
        this.feature = feature;
        owner = player.getUniqueId();
        Component title = feature.plugin().resourcePackEnabled()
                ? Component.text(negative(8) + "\uE070" + negative(1) + "\uE071"
                        + negative(177) + "\uE072" + negative(1) + "\uE073")
                        .font(Key.key("servercore:forge")).color(NamedTextColor.WHITE)
                : Text.mm("<dark_gray>대장간 · 장비 / 재료 / 강화");
        inventory = Bukkit.createInventory(this, 27, title);
        refresh();
    }
    @Override public Inventory getInventory() { return inventory; }

    void refresh() {
        ItemStack tool = inventory.getItem(TOOL);
        ToolType type = feature.tools().typeOf(tool);
        int current = type == null ? -1 : feature.tools().levelOf(tool);
        String name = "<gray>강화할 장비를 왼쪽에 넣어 주세요";
        List<String> lore = List.of();
        upgradeEnabled = false;
        if (type != null && current >= feature.maxLevel()) {
            name = "<gold>최대 강화 단계예요";
            lore = List.of();
        } else if (feature.validTool(tool)) {
            int target = current + 1;
            var artisan=feature.tools().artisanProgress(tool);
            double chance=ArtisanRules.effectivePercent(target,feature.successPercent(target),artisan);
            ItemStack material = inventory.getItem(MATERIAL);
            boolean hasMaterials = UpgradeRules.hasMaterials(target, feature.materialType(material) == type,
                    material == null ? 0 : material.getAmount());
            long fee = feature.goldCost(target);
            boolean hasGold = feature.canAfford(owner, fee);
            upgradeEnabled = hasMaterials && hasGold;
            name = (upgradeEnabled ? "<#ffc773>" : "<gray>") + "<bold>강화하기 · +" + current + " → +" + target;
            var lines = new java.util.ArrayList<String>();
            if (!hasMaterials) lines.add("<red>전용 재료 부족");
            else if (!hasGold) lines.add("<red>골드 부족");
            lines.addAll(List.of("<gray>골드 소모: <gold>" + Economy.format(fee),
                    "<gray>성공률: <yellow>" + java.math.BigDecimal.valueOf(chance).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%",
                    "<gold>장기백: " + ArtisanRules.energyLabel(artisan) + "%"));
            lore = List.copyOf(lines);
        }
        boolean pack = feature.plugin().resourcePackEnabled();
        inventory.setItem(UPGRADE, Items.of(pack ? Material.PAPER : upgradeEnabled ? Material.ANVIL : Material.GRAY_DYE,
                1, name, lore, pack ? Glyphs.model("forge_button") : null));
    }

    boolean upgradeEnabled() { return upgradeEnabled; }

    private static String negative(int pixels) {
        StringBuilder text = new StringBuilder();
        for (int bit=7;bit>=0;bit--) if ((pixels & (1 << bit)) != 0) text.append((char)(0xF801 + bit));
        return text.toString();
    }

    /** Idempotent drain: close/quit/death/disable may each call this. */
    ItemStack[] drain() {
        if (returned) return new ItemStack[0];
        returned = true;
        ItemStack a = inventory.getItem(TOOL), b = inventory.getItem(MATERIAL);
        inventory.setItem(TOOL, null); inventory.setItem(MATERIAL, null);
        return new ItemStack[]{a, b};
    }
}
