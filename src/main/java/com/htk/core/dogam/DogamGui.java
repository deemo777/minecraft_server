package com.htk.core.dogam;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 도감 (6줄 상자, 스타듀밸리 수집품 판)
 *
 *   열:  0  1  2  3  4  5  6  7  8
 *   줄0  ■  ■  ■  ■  ■  ■  ■  ■  ■
 *   줄1  ■  ■  ■  ■  ■  ■  ■  ■  ■
 *   줄2  ■  ■  ■  ■  ■  ■  ■  ■  ■     ← 한 페이지 45개
 *   줄3  ■  ■  ■  ■  ■  ■  ■  ■  ■
 *   줄4  ■  ■  ■  ■  ■  ■  ■  ■  ■
 *   줄5  ◀  [ 진행도 게이지  ][2/8] ▶     ← 게이지·페이지는 제목 그림 글자
 */
public final class DogamGui extends Gui {

    /** 한 페이지의 항목 칸 (왼쪽 페이지 → 오른쪽 페이지, 위에서 아래 순서) */
    static final int[] ENTRY_SLOTS = entrySlots();
    static final int PREV_SLOT = slot(5, 0), NEXT_SLOT = slot(5, 8);

    // 배경 그림 안의 위치 (tools/make_art.py 와 약속)
    private static final int BAR_X = 31, BAR_PIECES = 22, PAGE_CENTER_X = 136;

    private final DogamFeature feature;
    private final int page, pages;
    private final Map<Integer, CollectionRegistry.Entry> bySlot = new HashMap<>();

    public DogamGui(DogamFeature feature, Player player, int requestedPage) {
        this.feature = feature;
        List<CollectionRegistry.Entry> all = feature.registry().all();
        this.pages = Math.max(1, (all.size() + ENTRY_SLOTS.length - 1) / ENTRY_SLOTS.length);
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));
        boolean pack = feature.plugin().resourcePackEnabled();

        int found = feature.foundTotal(player), total = all.size();
        String titleText = feature.config().getString("title", "도감 진행도 (%found%/%total%)")
                .replace("%found%", String.valueOf(found))
                .replace("%total%", String.valueOf(total));

        Component title;
        if (pack) {
            title = new Glyphs.Canvas()
                    .background(Glyphs.DOGAM_BG)
                    .bar(BAR_X, BAR_PIECES, found, total)
                    .number((page + 1) + "/" + pages, PAGE_CENTER_X)
                    .build(Text.mm(titleText));
        } else {
            title = Text.mm(titleText + " <dark_gray>" + (page + 1) + "/" + pages);
        }
        create(6, title);

        // 항목
        PlayerDataStore.PlayerData data = feature.store().get(player.getUniqueId());
        int start = page * ENTRY_SLOTS.length;
        for (int i = 0; i < ENTRY_SLOTS.length && start + i < all.size(); i++) {
            CollectionRegistry.Entry e = all.get(start + i);
            inventory.setItem(ENTRY_SLOTS[i], entryItem(player, e, data.has(e.id()), pack));
            bySlot.put(ENTRY_SLOTS[i], e);
        }

        // 화살표
        inventory.setItem(PREV_SLOT, arrow(false, page > 0, pack));
        inventory.setItem(NEXT_SLOT, arrow(true, page < pages - 1, pack));
    }

    private ItemStack entryItem(Player player, CollectionRegistry.Entry e, boolean done, boolean pack) {
        // 마우스를 올리면 아이템 이름만 보여요 (설명·개수 안내 없음). 칸의 숫자 = 등록에 필요한 개수
        Material mat = e.material().isItem() ? e.material() : Material.BARRIER;
        // 등록한 항목: 리소스팩의 "원래 아이템 + 체크" 모델 / 리소스팩 없으면 반짝임
        var model = done && pack ? Glyphs.model("checked/" + mat.getKey().getKey()) : null;
        ItemStack item = Items.of(mat, e.required(), e.name(), List.of(), model);
        if (done && !pack) Items.glint(item);
        return item;
    }

    private ItemStack arrow(boolean next, boolean active, boolean pack) {
        String name = active ? feature.msg(next ? "next-page" : "prev-page") : feature.msg("no-more-page");
        if (pack) {
            String model = (next ? "arrow_right" : "arrow_left") + (active ? "" : "_off");
            return Items.hideTooltip(Items.of(Material.PAPER, 1, name, List.of(), Glyphs.model(model)));
        }
        return Items.hideTooltip(Items.of(active ? Material.ARROW : Material.GRAY_DYE, 1, name, List.of(), null));
    }

    @Override
    public void onClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == PREV_SLOT || slot == NEXT_SLOT) {
            int target = slot == NEXT_SLOT ? page + 1 : page - 1;
            if (target < 0 || target >= pages) return;
            feature.openLater(player, target);
            return;
        }
        CollectionRegistry.Entry e = bySlot.get(slot);
        if (e != null) feature.click(player, e, page);
    }

    /** 0~4번 줄 전부 (왼쪽 위부터 오른쪽으로) */
    private static int[] entrySlots() {
        int[] out = new int[45];
        for (int i = 0; i < 45; i++) out[i] = i;
        return out;
    }
}
