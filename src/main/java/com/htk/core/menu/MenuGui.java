package com.htk.core.menu;

import com.htk.core.common.Messages;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Placeholders;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 마을 메뉴 (5줄 상자). 그림은 리소스팩 배경(나무 간판 + 큰 버튼 4개),
 * 칸에는 보이지 않는 아이템을 깔아서 마우스를 올리면 설명이 떠요.
 */
public final class MenuGui extends Gui {

    private final MenuFeature feature;
    private final MenuConfig config;

    public MenuGui(MenuFeature feature, Player player) {
        this.feature = feature;
        this.config = feature.config();
        boolean pack = feature.plugin().resourcePackEnabled();

        Component title = pack
                ? new Glyphs.Canvas().background(Glyphs.MENU_BG).build(null)
                : Text.mm("<dark_gray>🌾 마을 메뉴");
        create(5, title);

        for (MenuConfig.Button b : config.buttons()) {
            var item = Items.hideTooltip(Items.invisible(Placeholders.apply(player, b.name()), Placeholders.apply(player, b.lore()), pack));
            for (int s : b.slots()) if (s >= 0 && s < inventory.getSize()) inventory.setItem(s, item);
        }
        inventory.setItem(config.headSlot(), Items.hideTooltip(Items.head(player,
                Placeholders.apply(player, config.headName()), Placeholders.apply(player, config.headLore()),
                pack ? Glyphs.model("player_head_front") : null)));
    }

    /** 저장된 이동 지점으로 (권한 없이도 플러그인이 직접 옮겨 줘요) */
    private void teleport(Player player, MenuConfig.Button b) {
        String point = b.teleport();
        var plugin = feature.plugin();
        if (point.equalsIgnoreCase("@land")) {          // 내 집 (집이 없으면 집 정하기 창)
            var land = plugin.feature(com.htk.core.land.LandFeature.class);
            if (land != null) {
                land.homeButton(player);
                return;
            }
        }
        var loc = plugin.points().get(point);
        if (loc == null) {
            String world = plugin.points().worldName(point);
            Messages.send(player,Text.mm(world == null
                    ? plugin.msg("point-not-set").replace("%point%", point)
                    : plugin.msg("point-world-missing").replace("%world%", world)));
            return;
        }
        player.closeInventory();
        feature.countdownTeleport(player, loc, b.teleportTitle(), b.teleportSubtitle());
    }

    @Override
    public void onClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == config.headSlot()) {          // 내 얼굴 → 칭호 창
            runNextTick(player, config.headCommand());
            return;
        }
        MenuConfig.Button b = config.bySlot().get(slot);
        if (b == null) return;
        if (b.close()) {
            player.closeInventory();
        } else if (!b.teleport().isBlank()) {
            teleport(player, b);
        } else if (b.comingSoon()) {
            Messages.send(player,Text.mm(feature.plugin().msg("coming-soon")));
        } else if (!b.command().isBlank()) {
            runNextTick(player, b.command());
        }
    }

    /** 클릭 처리 중에 다른 창을 바로 열면 꼬일 수 있어서 다음 틱에 */
    private void runNextTick(Player player, String command) {
        if (command == null || command.isBlank()) return;
        String cmd = Placeholders.apply(player, command);
        Bukkit.getScheduler().runTask(feature.plugin(), () -> {
            if (player.isOnline()) player.performCommand(cmd);
        });
    }
}
