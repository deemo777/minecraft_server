package com.htk.core.land;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 집 지도 창 (6줄 상자)
 *   0~4줄 2~6열: 청크 지도 5x5 (한 칸 = 청크 하나, 가운데 = 집터 청크)
 *       지도의 위 = 집터를 놓을 때 바라본 방향 (집 문은 아래쪽을 봐요). 북쪽 고정이 아니에요
 *       내 집 🚪 / 내 땅 🌱 / 해방할 수 있는 곳 + / 남의 집 = 주인 얼굴 / 공용지(집 구역 밖) = 회색
 *       + 를 누르면 창이 닫히고 경계를 보여준 뒤 채팅 버튼으로 확인 (LandFeature.requestExpand)
 *       지금 서 있는 곳: 빈 칸이면 나침반, 무언가 있으면 반짝이 + "(지금 여기)"
 *   5줄: 0열 ◀ 메뉴로, 3열 빚 (보기만, 갚기는 로비의 이장님께), 4열 이웃, 5열 테두리 보기
 * ⚠ 칸 위치는 tools/make_art.py 의 draw_land() 와 약속
 */
final class LandGui extends Gui {

    static final int BACK = 45, DEBT = 48, MEMBERS = 49, BORDER = 50;
    /** 지도 5 x 5 (집터에서 상하좌우 2칸), 2~6열 */
    static final int RANGE = 2, MAP_COL = 2;

    private final LandFeature f;
    private final Map<Integer, long[]> cells = new HashMap<>();   // 칸 → {청크x, 청크z} (청크 해방)

    LandGui(LandFeature f, Player p) {
        this.f = f;
        boolean pack = f.plugin().resourcePackEnabled();
        Land mine = f.landOf(p.getUniqueId());

        String titleText = mine == null
                ? f.msg("gui-title-new", "집 정하기")
                : f.msg("gui-title", "내 집 · 땅 %size%/%max%")
                        .replace("%size%", String.valueOf(mine.size())).replace("%max%", String.valueOf(f.maxChunks()));
        Component title = pack
                ? new Glyphs.Canvas().background(Glyphs.LAND_BG).build(Text.mm("<#6b4226>" + titleText))
                : Text.mm("<dark_gray>" + titleText);
        create(6, title);

        // 지도 가운데 = 집터 청크 (해방할 수 있는 범위가 집터에서 상하좌우 RANGE 칸이라 지도 안에 다 들어와요)
        Location here = p.getLocation();
        String world = mine != null ? mine.world : here.getWorld().getName();
        long center = mine != null ? mine.firstChunk() : Land.key(here.getBlockX() >> 4, here.getBlockZ() >> 4);
        int centerX = Land.cx(center), centerZ = Land.cz(center);
        boolean sameWorld = here.getWorld().getName().equals(world);
        int hereX = here.getBlockX() >> 4, hereZ = here.getBlockZ() >> 4;
        long nextCost = mine == null ? -1 : f.nextExpandCost(mine);

        for (int b = -RANGE; b <= RANGE; b++) {           // b: 지도 아래쪽으로
            for (int a = -RANGE; a <= RANGE; a++) {       // a: 지도 오른쪽으로
                int[] d = mine != null ? mine.mapOffset(a, b) : new int[]{a, b};
                int cx = centerX + d[0], cz = centerZ + d[1];
                int slot = Gui.slot(b + RANGE, a + RANGE + MAP_COL);
                boolean isHere = sameWorld && cx == hereX && cz == hereZ;
                Land owner = f.store().at(world, cx, cz);
                ItemStack item = null;
                if (owner != null && owner == mine) {
                    boolean first = mine.firstChunk() == Land.key(cx, cz);
                    if (!mine.has(cx, cz)) {
                        boolean next = f.canExpandTo(mine, cx, cz);
                        boolean ready = next && mine.debt <= 0 && nextCost >= 0;
                        String name = ready
                                ? "<#7acb52><bold>+ 다음 청크 해방</bold> <#ffd34d>(빚 +" + Economy.format(nextCost) + ")"
                                : next ? "<yellow>다음 해방 청크 <gray>· 현재 빚부터 갚아 주세요"
                                : "<gray>잠긴 내 부지 <dark_gray>· 앞 청크부터 순서대로 해방해요";
                        item = Items.of(ready ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE,
                                1, name, List.of(),
                                pack ? Glyphs.model(ready ? "land_plus" : "land_plus_off") : null);
                        if (ready) cells.put(slot, new long[]{cx, cz});
                    } else {
                    item = Items.of(first ? Material.LODESTONE : Material.GRASS_BLOCK, 1,
                            (first ? f.msg("cell-home", "<#7acb52><bold>🏠 내 집</bold>") : f.msg("cell-mine", "<#7acb52>내 땅"))
                                    + (isHere ? f.msg("cell-here-suffix", " <gray>(지금 여기)") : ""),
                            List.of(), first && pack ? Glyphs.model("house_core") : null);
                    }
                } else if (owner != null) {
                    item = Items.head(Bukkit.getOfflinePlayer(owner.owner),
                            f.msg("cell-other", "<#e07a6a>%owner%님의 집").replace("%owner%", owner.ownerName),
                            List.of(), null);
                } else if (mine != null && (!mine.reserves(cx, cz) || !f.inZone(cx, cz))) {
                    item = Items.of(Material.GRAY_STAINED_GLASS_PANE, 1,
                            f.msg("cell-public", "<gray>공용지 <dark_gray>· 집을 지을 수 없어요"), List.of(), null);
                }
                if (item == null && isHere) {
                    item = Items.of(Material.COMPASS, 1, f.msg("cell-here", "<white>📍 지금 여기"), List.of(), null);
                } else if (item != null && isHere) {
                    // 내 땅·남의 집·해방할 곳 위에 서 있으면: 반짝이 + 이름 뒤에 (지금 여기)
                    Items.glint(item);
                    if (owner == null || owner != mine) {
                        var meta = item.getItemMeta();
                        if (meta != null && meta.displayName() != null) {
                            meta.displayName(meta.displayName().append(Text.mm(f.msg("cell-here-suffix", " <gray>(지금 여기)"))));
                            item.setItemMeta(meta);
                        }
                    }
                }
                if (item != null) inventory.setItem(slot, item);
            }
        }

        // 아래 줄
        inventory.setItem(BACK, Items.hideTooltip(pack
                ? Items.of(Material.PAPER, 1, "", List.of(), Glyphs.model("arrow_left"))
                : Items.of(Material.ARROW, 1, "<gray>메뉴로", List.of(), null)));
        if (mine != null) {
            ItemStack debt = Items.of(Material.GOLD_NUGGET, 1, mine.debt > 0
                            ? f.msg("btn-debt", "<#ffd34d><bold>빚 %debt%</bold> <gray>· 로비의 이장님께 갚아요").replace("%debt%", Economy.format(mine.debt))
                            : f.msg("btn-no-debt", "<#7acb52>빚 없음"),
                    List.of(), pack ? Glyphs.model("gold_coin") : null);
            if (mine.debt > 0) Items.glint(debt);
            inventory.setItem(DEBT, debt);
            ItemStack members = Items.head(p, f.msg("btn-members", "<white>이웃 관리 <gray>(%count%명)")
                    .replace("%count%", String.valueOf(mine.members.size())), List.of(), null);
            inventory.setItem(MEMBERS, members);
            inventory.setItem(BORDER, Items.of(Material.SPYGLASS, 1, f.msg("btn-border", "<white>내 땅 테두리 보기"), List.of(), null));
        }
    }

    @Override
    public void onClick(Player p, int slot, InventoryClickEvent event) {
        long[] cell = cells.get(slot);
        if (cell != null) {
            Bukkit.getScheduler().runTask(f.plugin(), () -> {
                if (p.isOnline()) f.requestExpand(p, (int) cell[0], (int) cell[1]);
            });
            return;
        }
        Land mine = f.landOf(p.getUniqueId());
        switch (slot) {
            case BACK -> run(p, "menu");
            case MEMBERS -> {
                if (mine != null) Bukkit.getScheduler().runTask(f.plugin(), () -> new MembersGui(f, p, 0).open(p));
            }
            case BORDER -> {
                if (mine != null) {
                    p.closeInventory();
                    f.showBorder(p, mine);
                }
            }
            default -> { }
        }
    }

    private void reopen(Player p) {
        Bukkit.getScheduler().runTask(f.plugin(), () -> {
            if (p.isOnline()) new LandGui(f, p).open(p);
        });
    }

    private void run(Player p, String cmd) {
        Bukkit.getScheduler().runTask(f.plugin(), () -> {
            if (p.isOnline()) p.performCommand(cmd);
        });
    }
}
