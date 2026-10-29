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
        List<String> lore = List.of("<gray>왼쪽: 마을 장비 1개", "<gray>오른쪽: 같은 장비 전용 강화 재료", "<gray>Shift+클릭으로도 넣을 수 있어요");
        if (type != null && current >= feature.maxLevel()) {
            name = "<gold>최대 강화 단계예요";
        } else if (type != null && current >= 0) {
            int target = current + 1;
            var artisan=feature.tools().artisanProgress(tool);
            double chance=ArtisanRules.effectivePercent(feature.successPercent(target),artisan);
            name = "<#ffc773><bold>강화하기 · +" + current + " → +" + target;
            lore = List.of(UpgradeRules.required(target)==0?"<green>강화 결정 없이 시도할 수 있어요"
                            : "<gray>재료: " + type.korean + " 강화 재료 " + UpgradeRules.required(target) + "개",
                    "<gray>골드: <gold>"+Economy.format(feature.goldCost(target))+" <gray>· 보유: "+Economy.format(feature.balance(owner)),
                    "<gray>성공·실패·장기백 모두 같은 골드 비용",
                    "<gray>성공률: <yellow>" + java.math.BigDecimal.valueOf(chance).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%"
                            + (artisan.guaranteed()?" <gold>장기백 · 확정 성공":""),
                    "<gold>장인의 기운: " + ArtisanRules.energyLabel(artisan) + "% <gray>· 실패 " + artisan.failures() + "회",
                    UpgradeRules.required(target)==0?"<gray>성공·실패 모두 재료 소모 없음 · 장비 파괴 없음"
                            : "<gray>실패해도 재료는 소비돼요 · 장비 파괴 없음",
                    "<gray>실패해도 단계 유지 · 성공률과 장인의 기운 누적",
                    "<gray>기운 100%면 다음 시도 확정 성공 · 재료 비용은 동일",
                    "<dark_gray>화면을 닫으면 넣은 아이템을 돌려받아요");
        }
        inventory.setItem(UPGRADE, Items.of(feature.plugin().resourcePackEnabled() ? Material.PAPER : Material.ANVIL,
                1, name, lore, feature.plugin().resourcePackEnabled() ? Glyphs.model("forge_button") : null));
    }

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
