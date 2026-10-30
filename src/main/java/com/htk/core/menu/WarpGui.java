package com.htk.core.menu;

import com.htk.core.common.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.List;

/** Six cards matching the main menu, with a separate return and close control. */
final class WarpGui extends Gui {
    private final MenuFeature feature;
    WarpGui(MenuFeature feature) {
        this.feature=feature;
        boolean pack=feature.plugin().resourcePackEnabled();
        create(5,pack?new Glyphs.Canvas().background(Glyphs.WARP_BG).build(null):Text.mm("<dark_gray>야생 워프"));
        for (var destination:WarpDestination.values()) {
            var icon=pack?Items.invisible(destination.label,List.of(),true)
                    :Items.of(destination.enabled()?Material.COMPASS:Material.GRAY_DYE,1,
                    "<gold>"+destination.label,List.of(),null);
            for (int slot:destination.slots()) inventory.setItem(slot,icon);
        }
        inventory.setItem(0,Items.invisible("<yellow>메뉴로",List.of(),pack));
        inventory.setItem(8,Items.invisible("<red>닫기",List.of(),pack));
    }
    @Override public void onClick(Player player,int slot,InventoryClickEvent event) {
        if(slot==8){player.closeInventory();return;}
        if(slot==0){
            Bukkit.getScheduler().runTask(feature.plugin(),()->{if(player.isOnline())feature.open(player);});
            return;
        }
        var destination=WarpDestination.bySlot(slot);
        if(destination==null || !destination.enabled())return;
        feature.warp(player,destination);
    }
}
