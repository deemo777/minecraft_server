package com.htk.core.land;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 이웃 관리 (6줄 상자): 서버에 한 번이라도 들어온 사람 모두의 얼굴 + 이름.
 * 클릭하면 허가 ✔ / 취소. 허가한 사람이 먼저, 그다음 이름 순.
 *   0~4줄: 한 페이지 45명,  5줄: 0열 ◀ 집 지도로, 3열 이전 페이지, 5열 다음 페이지, 가운데 페이지 숫자
 * ⚠ 칸·숫자 위치는 tools/make_art.py 의 draw_members() 와 약속
 */
final class MembersGui extends Gui {

    static final int BACK = 45, PREV = 48, NEXT = 50, PER_PAGE = 45;
    private static final int PAGE_NUM_X = 88;

    private final LandFeature f;
    private final int page;
    private final List<UUID> shown = new ArrayList<>();

    MembersGui(LandFeature f, Player p, int page) {
        this.f = f;
        boolean pack = f.plugin().resourcePackEnabled();
        Land land = f.landOf(p.getUniqueId());

        // 서버에 등록된 계정 (나 빼고): 허가한 사람 먼저, 그다음 이름 순
        List<OfflinePlayer> all = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() == null || op.getUniqueId().equals(p.getUniqueId())) continue;
            all.add(op);
        }
        all.sort(Comparator
                .comparing((OfflinePlayer op) -> land == null || !land.members().contains(op.getUniqueId()))
                .thenComparing(op -> op.getName().toLowerCase(Locale.ROOT)));
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));

        int count = land == null ? 0 : land.members().size();
        String titleText = f.msg("members-title", "이웃 허가 · %count%명").replace("%count%", String.valueOf(count));
        Component title = pack
                ? new Glyphs.Canvas().background(Glyphs.MEMBERS_BG)
                        .number((this.page + 1) + "/" + pages, PAGE_NUM_X)
                        .build(Text.mm("<#6b4226>" + titleText))
                : Text.mm("<dark_gray>" + titleText + " (" + (this.page + 1) + "/" + pages + ")");
        create(6, title);

        int from = this.page * PER_PAGE;
        for (int i = from; i < Math.min(all.size(), from + PER_PAGE); i++) {
            OfflinePlayer op = all.get(i);
            boolean trusted = land != null && land.members().contains(op.getUniqueId());
            String name = trusted
                    ? f.msg("member-on", "<#7acb52><bold>✔ %name%").replace("%name%", op.getName())
                    : f.msg("member-off", "<white>%name%").replace("%name%", op.getName());
            ItemStack head = Items.head(op, name, List.of(), trusted && pack ? Glyphs.model("checked_head") : null);
            if (trusted && !pack) Items.glint(head);
            inventory.setItem(i - from, head);
            shown.add(op.getUniqueId());
        }

        inventory.setItem(BACK, Items.hideTooltip(pack
                ? Items.of(Material.PAPER, 1, "", List.of(), Glyphs.model("arrow_left"))
                : Items.of(Material.ARROW, 1, "<gray>집 지도로", List.of(), null)));
        if (pages > 1) {
            inventory.setItem(PREV, arrow(false, this.page > 0, pack));
            inventory.setItem(NEXT, arrow(true, this.page < pages - 1, pack));
        }
    }

    private static ItemStack arrow(boolean next, boolean active, boolean pack) {
        if (pack) {
            String model = (next ? "arrow_right" : "arrow_left") + (active ? "" : "_off");
            return Items.hideTooltip(Items.of(Material.PAPER, 1, "", List.of(), Glyphs.model(model)));
        }
        return Items.hideTooltip(Items.of(active ? Material.ARROW : Material.GRAY_DYE, 1, "", List.of(), null));
    }

    @Override
    public void onClick(Player p, int slot, InventoryClickEvent event) {
        if (slot == BACK) {
            Bukkit.getScheduler().runTask(f.plugin(), () -> f.openGui(p));
            return;
        }
        if (slot == PREV || slot == NEXT) {
            int target = slot == NEXT ? page + 1 : page - 1;
            Bukkit.getScheduler().runTask(f.plugin(), () -> new MembersGui(f, p, target).open(p));
            return;
        }
        if (slot < 0 || slot >= shown.size()) return;
        Land land = f.landOf(p.getUniqueId());
        if (land == null) return;
        UUID id = shown.get(slot);
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        if (land.members().contains(id)) f.untrust(p, id, op.getName());
        else f.trust(p, op);
        Bukkit.getScheduler().runTask(f.plugin(), () -> new MembersGui(f, p, page).open(p));
    }
}
