package com.htk.core.shop;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상점 (주민 교환처럼): 한 줄에 [아이템] → [골드], 왼쪽 5줄 + 오른쪽 5줄 = 한 페이지 10개
 *
 *   열:   0   1     2   3      4   5     6   7      8
 *   줄0~4    [아이템] → [골드]  |  [아이템] → [골드]
 *   줄5   ◀      [ 🪙 보유 골드 1,234G ]          ▶
 *
 *   골드 칸 클릭        가방의 그 아이템 1개를 팔고 골드 주머니가 마우스 커서에 (계속 누르면 쌓여요)
 *   Shift+골드 칸       전부 팔고 주머니를 가방에
 *   아이템 칸 클릭      골드로 1개 사서 커서에 (Shift: 한 묶음을 가방에) — shops.yml 에 buy 가격이 있을 때만
 */
public final class ShopGui extends Gui {

    static final int PER_PAGE = 10;
    static final int PREV_SLOT = slot(5, 0), NEXT_SLOT = slot(5, 8);
    private static final int[] ITEM_COL = {1, 5}, GOLD_COL = {3, 7};   // 왼쪽 / 오른쪽 (tools/make_art.py 와 약속)
    private static final int BALANCE_CENTER_X = 92;

    private final ShopFeature feature;
    private final ShopConfig.Shop shop;
    private final int page, pages;
    private final Map<Integer, ShopConfig.Item> itemSlots = new HashMap<>();
    private final Map<Integer, ShopConfig.Item> goldSlots = new HashMap<>();

    public ShopGui(ShopFeature feature, Player player, ShopConfig.Shop shop, int requestedPage) {
        this.feature = feature;
        this.shop = shop;
        this.pages = Math.max(1, (shop.items().size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));
        boolean pack = feature.plugin().resourcePackEnabled();
        long balance = feature.economy().balance(player.getUniqueId());

        Component title;
        if (pack) {
            title = new Glyphs.Canvas()
                    .background(Glyphs.shopBackground(shop.theme()))
                    .number(Economy.format(balance), BALANCE_CENTER_X)
                    .build(Text.mm(shop.title()));
        } else {
            title = Text.mm(shop.title() + " <dark_gray>· <gold>" + Economy.format(balance));
        }
        create(6, title);

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < shop.items().size(); i++) {
            ShopConfig.Item it = shop.items().get(start + i);
            int side = i / 5, row = i % 5;
            int itemSlot = slot(row, ITEM_COL[side]), goldSlot = slot(row, GOLD_COL[side]);
            inventory.setItem(itemSlot, itemIcon(it));
            inventory.setItem(goldSlot, goldIcon(player, it));
            itemSlots.put(itemSlot, it);
            goldSlots.put(goldSlot, it);
        }
        if (pages > 1) {
            inventory.setItem(PREV_SLOT, arrow(false, page > 0, pack));
            inventory.setItem(NEXT_SLOT, arrow(true, page < pages - 1, pack));
        }
    }

    /** 왼쪽 칸: 그 아이템 (이름은 게임 기본 이름). 살 수 있으면 가격 한 줄 */
    private ItemStack itemIcon(ShopConfig.Item it) {
        ItemStack item = new ItemStack(it.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<Component> lore = new ArrayList<>();
        if (it.buy() > 0) {
            lore.add(line(feature.msg("lore-buy", "<gray>클릭: <#ffd34d>%price%</#ffd34d>로 1개 사기 <dark_gray>(Shift: 한 묶음)")
                    .replace("%price%", Economy.format(it.buy()))));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** 오른쪽 칸: 골드 (받는 금액) */
    private ItemStack goldIcon(Player player, ShopConfig.Item it) {
        long price = it.sell() > 0 ? feature.sellPrice(player, shop, it, 1) : it.buy();
        List<String> lore = new ArrayList<>();
        if (it.sell() > 0) {
            int have = feature.count(player, it.material());
            lore.add(feature.msg("lore-sell", "<gray>클릭: 1개 팔기 <dark_gray>(Shift: 전부 %have%개 → %total%)")
                    .replace("%have%", String.valueOf(have))
                    .replace("%total%", Economy.format(feature.sellPrice(player, shop, it, have))));
        }
        ItemStack gold = Items.of(Material.GOLD_NUGGET, 1,
                feature.msg("gold-name", "<#ffd34d><bold>%price%").replace("%price%", Economy.format(price)), lore,
                feature.plugin().resourcePackEnabled() ? Glyphs.model("gold_coin") : null);
        Items.glint(gold);
        return gold;
    }

    /** 커서에 받은 주머니·아이템을 가방에 내려놓을 수 있게 */
    @Override
    public boolean allowsOwnInventory() {
        return true;
    }

    /** 같은 창에서 골드 칸 설명(보유 개수)만 새로 */
    private void refresh(Player player) {
        goldSlots.forEach((slot, it) -> inventory.setItem(slot, goldIcon(player, it)));
    }

    private static Component line(String mm) {
        return Text.mm(mm).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack arrow(boolean next, boolean active, boolean pack) {
        if (pack) {
            String model = (next ? "arrow_right" : "arrow_left") + (active ? "" : "_off");
            return Items.hideTooltip(Items.of(Material.PAPER, 1, "", List.of(), Glyphs.model(model)));
        }
        return Items.hideTooltip(Items.of(active ? Material.ARROW : Material.GRAY_DYE, 1, "", List.of(), null));
    }

    @Override
    public void onClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == PREV_SLOT || slot == NEXT_SLOT) {
            int target = slot == NEXT_SLOT ? page + 1 : page - 1;
            if (pages > 1 && target >= 0 && target < pages) feature.openLater(player, shop, target);
            return;
        }
        // 그냥 클릭: 마우스 커서에 바로 (주민 교환처럼) / Shift+클릭: 전부(팔기)·한 묶음(사기)을 가방에
        boolean toCursor = !event.getClick().isShiftClick();
        long before = feature.economy().balance(player.getUniqueId());
        boolean done = false;
        ShopConfig.Item sell = goldSlots.get(slot);
        if (sell != null) {
            done = feature.sell(player, shop, sell, toCursor);
        } else {
            ShopConfig.Item buy = itemSlots.get(slot);
            if (buy != null) done = feature.buy(player, buy, toCursor);
        }
        if (!done) return;
        refresh(player);   // 보유 개수 다시 쓰기 (창을 다시 열면 커서의 아이템이 가방으로 들어가 버려서 그대로 둬요)
        // 잔액이 바뀌었으면(주머니가 모자라 잔액에서 낸 경우) 아래 잔액 숫자를 다시 그리려고 창을 다시 열어요
        if (!toCursor && feature.economy().balance(player.getUniqueId()) != before) feature.openLater(player, shop, page);
    }
}
