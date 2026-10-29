package com.htk.core.race;

import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.UUID;

/** Individual controls below a server-wide live scoreboard. Watching never charges gold. */
final class RaceGui extends Gui {
    private final RaceFeature feature;
    private UUID round;
    private int selected = -1;
    private long stake = 100;

    RaceGui(RaceFeature feature, Player player) {
        this.feature = feature; round = feature.round();
        create(6, Text.mm("<#6b4226>마을 동물 경주 · 실시간 관전"));
        render(player);
    }
    void render(Player player) {
        if (!round.equals(feature.round())) { round = feature.round(); selected = -1; }
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot,
                Items.hideTooltip(Items.of(Material.BROWN_STAINED_GLASS_PANE, 1, " ", List.of(), null)));
        Material[] lanes = {Material.PINK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE};
        for (int runner = 0; runner < 3; runner++) {
            double progress = feature.progress(runner);
            for (int col = 0; col < 9; col++) inventory.setItem(runner * 9 + col,
                    Items.hideTooltip(Items.of(col == 8 ? Material.WHITE_STAINED_GLASS_PANE : lanes[runner], 1, " ", List.of(), null)));
            var icon = Items.of(RaceFeature.ICONS[runner], 1, RaceFeature.COLORS[runner] + (runner + 1) + "번 · " + RaceFeature.NAMES[runner],
                    List.of("<gray>진행도 <white>" + (int) (progress * 100) + "%", "<gray>모든 관전자에게 같은 경주가 보여요."), null);
            if (feature.phase() == RaceFeature.Phase.RUNNING && runner == feature.leader()) Items.glint(icon);
            inventory.setItem(runner * 9 + Math.clamp((int) (progress * 8), 0, 8), icon);
        }
        inventory.setItem(27, Items.of(Material.CLOCK, 1, "<gold>" + feature.status(),
                List.of("<gray>관전 무료 · 참가 " + feature.participants() + "명", "<gray>첫 참가자가 들어오면 접수를 시작해요.",
                        "<gray>혼자 시작 가능 · 관리자·경기장 설정 불필요", "<gray>/race watch · 다른 사람도 무료 관전",
                        "<gray>동물별 승률 <white>1/3", "<gray>적중 시 <gold>원금 포함 3배", "<gray>실패 시 참가금을 잃어요."), null));
        var bet = feature.bet(player.getUniqueId());
        inventory.setItem(31, Items.of(Material.NAME_TAG, 1, "<white>내 참가 내역", bet == null
                ? List.of("<gray>아직 참가하지 않았어요.", "<gray>관전만 해도 괜찮아요!")
                : List.of(RaceFeature.COLORS[bet.runner()] + RaceFeature.NAMES[bet.runner()], "<gold>" + Economy.format(bet.stake()),
                        "<gray>한 경기 한 번 · 변경·취소 불가"), null));
        inventory.setItem(35, Items.of(Material.GOLD_NUGGET, 1, "<gold>보유 " + Economy.format(feature.balance(player.getUniqueId())), List.of(), null));
        for (int runner = 0; runner < 3; runner++) {
            var item = Items.of(RaceFeature.ICONS[runner], 1, RaceFeature.COLORS[runner] + (selected == runner ? "✔ " : "") + RaceFeature.NAMES[runner],
                    List.of("<yellow>클릭해서 응원할 동물 선택", "<gray>아래에서 금액을 고른 뒤 참가를 확정해요.",
                            "<gray>우승 확률 <white>1/3 <gray>· 적중 시 총 3배"), null);
            if (selected == runner) Items.glint(item);
            inventory.setItem(37 + runner * 3, item);
        }
        for (int choice = 0; choice < RaceRules.STAKES.size(); choice++) {
            long amount = RaceRules.STAKES.get(choice);
            var item = Items.of(Material.GOLD_NUGGET, 1, (stake == amount ? "<gold>✔ " : "<gray>") + Economy.format(amount),
                    List.of("<gray>클릭해서 참가 금액 선택", "<gray>적중 시 총 <gold>" + Economy.format(amount * 3)), null);
            if (stake == amount) Items.glint(item);
            inventory.setItem(45 + choice, item);
        }
        boolean accepting = feature.accepting() && bet == null;
        inventory.setItem(49, Items.of(accepting ? Material.LIME_DYE : Material.GRAY_DYE, 1,
                accepting ? (feature.phase() == RaceFeature.Phase.IDLE ? "<green><bold>경주 시작 · 참가 확정</bold>" : "<green><bold>참가 확정</bold>")
                        : "<gray>지금은 참가할 수 없어요", selected < 0
                        ? List.of("<yellow>먼저 응원할 동물을 선택해 주세요.")
                        : List.of(RaceFeature.COLORS[selected] + RaceFeature.NAMES[selected], "<gold>" + Economy.format(stake) + " 차감",
                                "<gray>적중 시 총 <gold>" + Economy.format(stake * 3), "<red>실패 시 참가금을 잃어요.",
                                "<gray>확정 후 변경·취소할 수 없어요."), null));
        inventory.setItem(53, Items.of(Material.OAK_DOOR, 1, "<gray>창 닫기", List.of("<gray>닫거나 접속을 종료해도 결과는 정산돼요."), null));
    }
    @Override public void onClick(Player player, int slot, InventoryClickEvent event) {
        // A click from an old screen cannot become a bet for a different round.
        if (!round.equals(feature.round())) { render(player); return; }
        if (slot == 53) { feature.close(player, this); return; }
        if (slot == 37 || slot == 40 || slot == 43) selected = (slot - 37) / 3;
        else if (slot >= 45 && slot <= 47) stake = RaceRules.STAKES.get(slot - 45);
        else if (slot == 49 && selected >= 0) feature.bet(player, round, selected, stake);
        render(player);
    }
}
