package com.htk.core.shop;

import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.MerchantInventory;

/** Prevent native emerald trading without affecting ServerCore's NPC shop GUIs. */
public final class VanillaTradeProtection implements Listener {
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if(event.getRightClicked() instanceof AbstractVillager)event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        onInteract(event);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent event) {
        if(event.getInventory() instanceof MerchantInventory inventory
                && inventory.getMerchant() instanceof AbstractVillager)event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onTrade(PlayerTradeEvent event) {
        event.setCancelled(true);
    }
}
