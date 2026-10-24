package com.htk.core.menu;

import com.htk.core.ServerCore;
import com.htk.core.common.Dialogs;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 마을 메뉴 (대화창)
 *  - G 키  : 서버 시작 때 QuickActionInstaller가 만든 데이터팩 대화창
 *  - /메뉴, Shift + F : 여기서 바로 만들어 띄우는 대화창
 * 두 쪽 다 menu.yml 하나로 만들어져서 모양이 같아요.
 */
public final class MenuFeature implements Feature, Listener {

    private final ServerCore plugin;
    private MenuConfig config;

    public MenuFeature(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        config = MenuConfig.load(plugin.loadYaml("menu.yml"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        PluginCommand cmd = plugin.getCommand("menu");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("게임 안에서만 쓸 수 있어요.");
                    return true;
                }
                // G 키 메뉴의 "준비 중" 버튼이 /menu soon 을 실행해요
                if (args.length == 1 && args[0].equalsIgnoreCase("soon")) comingSoon(p);
                else open(p);
                return true;
            });
        }

        if (plugin.getConfig().getBoolean("menu.quick-actions", true)) {
            QuickActionInstaller.install(plugin, config);
        }
    }

    @Override
    public void reload() {
        config = MenuConfig.load(plugin.loadYaml("menu.yml"));
    }

    @Override
    public void disable() { }

    public void open(Player player) {
        if (!player.hasPermission("servercore.menu")) {
            player.sendMessage(Text.mm(plugin.msg("no-permission")));
            return;
        }
        player.showDialog(build());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    private Dialog build() {
        List<ActionButton> buttons = new ArrayList<>();
        for (MenuConfig.Button b : config.buttons()) {
            buttons.add(Dialogs.button(plugin,
                    Text.mm(b.label()),
                    b.tooltip().isEmpty() ? null : Text.lines(b.tooltip()),
                    config.buttonWidth(),
                    p -> press(p, b)));
        }
        ActionButton close = Dialogs.button(plugin, Text.mm(config.closeLabel()), null, 100, Player::closeDialog);

        DialogBase base = DialogBase.builder(Text.mm(config.title()))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        return Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, close, config.columns())));
    }

    private void press(Player player, MenuConfig.Button b) {
        if (b.comingSoon()) {
            comingSoon(player);
        } else if (!b.command().isBlank()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            player.performCommand(b.command());
        }
    }

    private void comingSoon(Player player) {
        player.sendMessage(Text.mm(plugin.msg("coming-soon")));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }

    // Shift + F
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!plugin.getConfig().getBoolean("menu.open-with-sneak-swap", true)) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return; // 그냥 F는 원래대로 양손 바꾸기
        event.setCancelled(true);
        open(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String hint = plugin.getConfig().getString("menu.join-hint", "");
        if (hint != null && !hint.isBlank()) event.getPlayer().sendMessage(Text.mm(hint));
    }
}
