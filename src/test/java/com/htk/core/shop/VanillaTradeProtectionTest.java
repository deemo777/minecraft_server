package com.htk.core.shop;

import io.papermc.paper.event.player.PlayerTradeEvent;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.util.Vector;

public final class VanillaTradeProtectionTest {
    private static int checks;
    private static void check(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    private static <T> T proxy(Class<T> type,Map<String,Object> methods) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->{
            if(methods.containsKey(method.getName()))return methods.get(method.getName());
            if(method.getName().equals("toString"))return type.getSimpleName();
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==int.class)return 0;
            return null;
        }));
    }
    private static InventoryOpenEvent open(Player player,Inventory inventory) {
        return new InventoryOpenEvent(proxy(InventoryView.class,Map.of("getTopInventory",inventory,"getPlayer",player)));
    }
    public static void main(String[] args) {
        var protection=new VanillaTradeProtection();
        var player=proxy(Player.class,Map.of());
        var villager=proxy(Villager.class,Map.of());
        var trader=proxy(WanderingTrader.class,Map.of());
        for(var merchant:new org.bukkit.entity.AbstractVillager[]{villager,trader}) {
            for(var hand:new EquipmentSlot[]{EquipmentSlot.HAND,EquipmentSlot.OFF_HAND}) {
                var click=new PlayerInteractEntityEvent(player,merchant,hand);
                protection.onInteract(click);
                check(click.isCancelled(),"native merchant interaction blocked: "+merchant+" "+hand);
                var at=new PlayerInteractAtEntityEvent(player,merchant,new Vector(),hand);
                protection.onInteractAt(at);
                check(at.isCancelled(),"precise native merchant interaction blocked: "+merchant+" "+hand);
            }
            var inventory=proxy(MerchantInventory.class,Map.of("getMerchant",merchant));
            var opened=open(player,inventory);
            protection.onOpen(opened);
            check(opened.isCancelled(),"forced native merchant inventory opening blocked: "+merchant);
            var trade=new PlayerTradeEvent(player,merchant,null,true,true);
            protection.onTrade(trade);
            check(trade.isCancelled(),"already-open native merchant trade blocked: "+merchant);
        }
        Entity npc=proxy(Interaction.class,Map.of());
        var click=new PlayerInteractEntityEvent(player,npc);
        protection.onInteract(click);
        check(!click.isCancelled(),"ServerCore interaction NPC remains clickable");
        var at=new PlayerInteractAtEntityEvent(player,npc,new Vector());
        protection.onInteractAt(at);
        check(!at.isCancelled(),"ServerCore precise interaction remains clickable");
        var shop=open(player,proxy(Inventory.class,Map.of()));
        protection.onOpen(shop);
        check(!shop.isCancelled(),"ServerCore chest shop GUI remains available");
        var virtual=open(player,proxy(MerchantInventory.class,Map.of("getMerchant",proxy(Merchant.class,Map.of()))));
        protection.onOpen(virtual);
        check(!virtual.isCancelled(),"virtual merchant inventory remains available");
        System.out.println("Vanilla trade protection event checks passed: "+checks);
    }
}
