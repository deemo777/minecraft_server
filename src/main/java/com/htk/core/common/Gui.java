package com.htk.core.common;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 모든 상자 GUI의 부모 클래스.
 * GuiListener가 클릭을 막고(아이템 못 빼게) 위쪽 칸 클릭만 onClick으로 넘겨줘요.
 */
public abstract class Gui implements InventoryHolder {

    protected Inventory inventory;

    protected Inventory create(int rows, Component title) {
        inventory = Bukkit.createInventory(this, rows * 9, title);
        return inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /**
     * true면 GUI가 열려 있는 동안에도 아래 내 가방은 평소처럼 쓸 수 있어요
     * (커서에 든 아이템을 가방에 내려놓기 등). Shift+클릭·더블클릭으로 위쪽에 넣는 건 계속 막아요.
     */
    public boolean allowsOwnInventory() {
        return false;
    }

    /** 위쪽(GUI) 칸을 눌렀을 때. slot = 0부터 시작하는 칸 번호 */
    public abstract void onClick(Player player, int slot, InventoryClickEvent event);

    public static int slot(int row, int col) {
        return row * 9 + col;
    }
}
