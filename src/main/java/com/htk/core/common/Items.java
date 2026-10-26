package com.htk.core.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** GUI 칸에 넣을 아이템 만들기 */
public final class Items {

    private Items() {}

    /** 이름·설명(MiniMessage)과 선택적으로 아이템 모델을 붙인 아이템 */
    public static ItemStack of(Material material, int amount, String name, List<String> lore, NamespacedKey model) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(noItalic(Text.mm(name)));
        List<Component> lines = new ArrayList<>(lore.size());
        for (String l : lore) lines.add(noItalic(Text.mm(l)));
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        if (model != null) meta.setItemModel(model);
        item.setItemMeta(meta);
        return item;
    }

    /** 보이지 않는 버튼 (큰 그림 버튼 위에 깔아서 클릭만 받게, 마우스를 올려도 설명창이 안 뜸) */
    public static ItemStack invisible(String name, List<String> lore, boolean pack) {
        if (!pack) {
            // 리소스팩이 없으면 그림이 없으니 유리판 + 설명으로 대신 보여줘요
            return of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 1, name, lore, null);
        }
        return hideTooltip(of(Material.PAPER, 1, name, lore, Glyphs.model("invisible")));
    }

    /** 마우스를 올려도 설명창(이름 포함)이 안 뜨게 */
    public static ItemStack hideTooltip(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        return head(owner, name, lore, null);
    }

    /** model: 예) servercore:player_head_front (얼굴이 정면으로 보이는 머리) */
    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore, NamespacedKey model) {
        ItemStack item = of(Material.PLAYER_HEAD, 1, name, lore, model);
        if (owner != null && item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            item.setItemMeta(skull);
        }
        return item;
    }

    public static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
