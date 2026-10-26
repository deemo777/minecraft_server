package com.htk.core.common;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** 모든 Gui의 클릭을 한 곳에서 받아서 각 Gui에게 넘겨줘요 */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Gui gui)) return;
        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < event.getView().getTopInventory().getSize();
        // 내 가방 쪽 클릭: 허용한 GUI면 평소처럼 (단, 위쪽으로 옮겨질 수 있는 Shift·더블클릭은 막기)
        if (!top && gui.allowsOwnInventory() && !event.getClick().isShiftClick()
                && event.getClick() != ClickType.DOUBLE_CLICK) {
            return;
        }
        event.setCancelled(true); // 그 밖에는 아이템 이동 금지
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (top) gui.onClick(player, raw, event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Gui gui)) return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (touchesTop || !gui.allowsOwnInventory()) event.setCancelled(true);
    }
}
