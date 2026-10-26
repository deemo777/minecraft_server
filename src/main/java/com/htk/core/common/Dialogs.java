package com.htk.core.common;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.function.Consumer;

/** 대화창(Dialog) 버튼 만들기 도우미 */
public final class Dialogs {

    /** 버튼 하나는 한 번만 눌리고, 10분 지나면 자동으로 사라져요 (창을 다시 열 때마다 새로 만들어짐) */
    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(10))
            .build();

    private Dialogs() {}

    /**
     * 누르면 onClick이 서버 메인 스레드에서 실행되는 버튼.
     * (인벤토리를 건드리거나 새 대화창을 띄워도 안전)
     */
    public static ActionButton button(Plugin plugin, Component label, Component tooltip, int width,
                                      Consumer<Player> onClick) {
        DialogAction action = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player player)) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) onClick.accept(player);
            });
        }, OPTIONS);
        return ActionButton.create(label, tooltip, clampWidth(width), action);
    }

    /**
     * 닫기 버튼: 서버가 창을 닫아 줘요.
     * (창의 afterAction 이 NONE 이면 동작 없는 버튼은 아무 일도 안 해서, 닫기는 꼭 이걸 써요)
     */
    public static ActionButton close(Plugin plugin, Component label, int width) {
        return button(plugin, label, null, width, Player::closeDialog);
    }

    public static int clampWidth(int width) {
        return Math.max(1, Math.min(1024, width));
    }
}
